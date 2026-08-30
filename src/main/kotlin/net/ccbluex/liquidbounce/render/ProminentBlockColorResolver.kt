/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
@file:JvmName("ProminentBlockColorResolverKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render

import com.mojang.blaze3d.platform.NativeImage
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.BlockStateModelSet
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.texture.SpriteContents
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.ARGB
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class ProminentBlockColorResolver {

    private var currentModelSet: BlockStateModelSet? = null
    private val modelSamples = IdentityHashMap<BlockState, List<ModelTextureSample>>()
    private val spritePixels = IdentityHashMap<SpriteContents, IntArray>()
    private val resolvedColors = HashMap<BlockColorCacheKey, Color4b?>()

    fun resolve(
        minecraft: Minecraft,
        level: BlockAndTintGetter,
        blockPos: BlockPos,
        blockState: BlockState,
    ): Color4b? {
        val modelSet = minecraft.modelManager.blockStateModelSet
        refreshFor(modelSet)

        val samples = modelSamples.getOrPut(blockState) {
            collectModelSamples(modelSet, blockState)
        }
        if (samples.isEmpty()) return null

        val resolvedTints = resolveTints(minecraft, level, blockPos, blockState, samples)
        val cacheKey = BlockColorCacheKey(blockState, resolvedTints.signature)
        if (resolvedColors.containsKey(cacheKey)) {
            return resolvedColors[cacheKey]
        }

        val color = calculateProminentColor(samples, resolvedTints.byIndex)
        if (resolvedColors.size >= MAX_RESOLVED_COLORS) {
            resolvedColors.clear()
        }
        resolvedColors[cacheKey] = color
        return color
    }

    private fun refreshFor(modelSet: BlockStateModelSet) {
        if (currentModelSet === modelSet) return

        currentModelSet = modelSet
        modelSamples.clear()
        spritePixels.clear()
        resolvedColors.clear()
    }

    private fun collectModelSamples(
        modelSet: BlockStateModelSet,
        blockState: BlockState,
    ): List<ModelTextureSample> = runCatching {
        val model = modelSet.get(blockState)
        if (model === modelSet.missingModel()) return emptyList()

        val parts = mutableListOf<BlockStateModelPart>()
        model.collectParts(RandomSource.create(MODEL_SEED), parts)
        val weights = LinkedHashMap<ModelTextureKey, Int>()

        for (part in parts) {
            for (direction in Direction.entries) {
                collectQuadWeights(part.getQuads(direction), weights)
            }
            collectQuadWeights(part.getQuads(null), weights)
        }

        if (weights.isEmpty()) {
            val particleSprite = model.particleMaterial().sprite()
            listOf(ModelTextureSample(particleSprite, tintIndex = NO_TINT_INDEX, FULL_FACE_WEIGHT))
        } else {
            weights.map { (key, weight) -> ModelTextureSample(key.sprite, key.tintIndex, weight) }
        }
    }.getOrDefault(emptyList())

    private fun collectQuadWeights(
        quads: List<BakedQuad>,
        weights: MutableMap<ModelTextureKey, Int>,
    ) {
        for (quad in quads) {
            val material = quad.materialInfo()
            val key = ModelTextureKey(material.sprite(), material.tintIndex())
            weights[key] = (weights[key] ?: 0) + quadSurfaceWeight(quad)
        }
    }

    private fun quadSurfaceWeight(quad: BakedQuad): Int {
        val firstEdge = Vector3f(quad.position1()).sub(quad.position0())
        val secondEdge = Vector3f(quad.position3()).sub(quad.position0())
        return (firstEdge.cross(secondEdge).length() * FULL_FACE_WEIGHT).roundToInt().coerceAtLeast(1)
    }

    private fun resolveTints(
        minecraft: Minecraft,
        level: BlockAndTintGetter,
        blockPos: BlockPos,
        blockState: BlockState,
        samples: List<ModelTextureSample>,
    ): ResolvedTints {
        val tintIndices = samples.asSequence()
            .map(ModelTextureSample::tintIndex)
            .filter { it != NO_TINT_INDEX }
            .distinct()
            .sorted()
            .toList()
        if (tintIndices.isEmpty()) return ResolvedTints.EMPTY

        val byIndex = HashMap<Int, Int>(tintIndices.size)
        val signature = ArrayList<Int>(tintIndices.size * 2)
        for (tintIndex in tintIndices) {
            val tint = minecraft.blockColors.getTintSource(blockState, tintIndex)
                ?.colorInWorld(blockState, level, blockPos)
                ?: NO_TINT
            byIndex[tintIndex] = tint
            signature += tintIndex
            signature += tint
        }
        return ResolvedTints(byIndex, signature)
    }

    private fun calculateProminentColor(
        samples: List<ModelTextureSample>,
        tints: Map<Int, Int>,
    ): Color4b? {
        val accumulator = ProminentColorAccumulator()
        for (sample in samples) {
            val tint = tints[sample.tintIndex] ?: NO_TINT
            for (pixel in sampledPixels(sample.sprite.contents())) {
                accumulator.add(ARGB.multiply(pixel, tint), sample.surfaceWeight)
            }
        }
        return accumulator.prominentColor()
    }

    private fun sampledPixels(contents: SpriteContents): IntArray = spritePixels.getOrPut(contents) {
        runCatching {
            val image = (contents as SpriteContentsImageAccess).originalImage
            samplePixels(image)
        }.getOrDefault(IntArray(0))
    }

    private fun samplePixels(image: NativeImage): IntArray {
        if (image.format() != NativeImage.Format.RGBA) return IntArray(0)

        val pixelCount = image.width.toLong() * image.height
        val stride = ceil(sqrt(pixelCount.toDouble() / MAX_SPRITE_SAMPLES)).toInt().coerceAtLeast(1)
        val capacity = ceil(image.width.toDouble() / stride).toInt() *
            ceil(image.height.toDouble() / stride).toInt()
        val pixels = IntArray(capacity)
        var index = 0

        for (y in 0 until image.height step stride) {
            for (x in 0 until image.width step stride) {
                pixels[index++] = image.getPixel(x, y)
            }
        }
        return pixels.copyOf(index)
    }

    private data class ModelTextureKey(val sprite: TextureAtlasSprite, val tintIndex: Int)

    private data class ModelTextureSample(
        val sprite: TextureAtlasSprite,
        val tintIndex: Int,
        val surfaceWeight: Int,
    )

    private data class ResolvedTints(
        val byIndex: Map<Int, Int>,
        val signature: List<Int>,
    ) {
        companion object {
            val EMPTY = ResolvedTints(emptyMap(), emptyList())
        }
    }

    private data class BlockColorCacheKey(val blockState: BlockState, val tintSignature: List<Int>)

    private companion object {
        const val MODEL_SEED = 42L
        const val FULL_FACE_WEIGHT = 256
        const val MAX_SPRITE_SAMPLES = 4096
        const val MAX_RESOLVED_COLORS = 4096
        const val NO_TINT_INDEX = -1
        const val NO_TINT = -1
    }
}
