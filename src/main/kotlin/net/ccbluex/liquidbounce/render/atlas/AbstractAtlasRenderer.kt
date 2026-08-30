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

@file:JvmName("AbstractAtlasRendererKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.atlas

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.ccbluex.liquidbounce.render.buffer.clearColorAndDepth
import net.ccbluex.liquidbounce.render.buffer.copyTo
import net.ccbluex.liquidbounce.render.buffer.readFully
import net.ccbluex.liquidbounce.render.buffer.withOutputTextureOverride
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.Rect2i
import net.minecraft.client.renderer.SubmitNodeStorage
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import org.apache.commons.lang3.function.Consumers
import org.lwjgl.system.MemoryUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode.NONE

internal sealed class AbstractAtlasRenderer<A : Any>(
    private val label: String,
) : MinecraftShortcuts {

    protected abstract val tileSize: Int
    protected abstract val tileCount: Int

    private val tilesPerRow: Int
        get() = Mth.smallestSquareSide(tileCount)

    private val textureSize: Int
        get() = tileSize * tilesPerRow

    private val framebufferLazy = lazy(NONE) {
        TextureTarget(
            "$label atlas framebuffer",
            textureSize,
            textureSize,
            true,
            GpuFormat.RGBA8_UNORM,
        )
    }
    private val framebuffer by framebufferLazy

    private val submitNodeStorageLazy = lazy(NONE, ::SubmitNodeStorage)
    protected val submitNodeStorage by submitNodeStorageLazy

    private val featureRenderDispatcherLazy = lazy(NONE) {
        FeatureRenderDispatcher(
            mc.gameRenderer.renderBuffers,
            mc.modelManager,
            mc.atlasManager,
            mc.font,
            mc.gameRenderer.gameRenderState(),
        )
    }
    protected val featureRenderDispatcher by featureRenderDispatcherLazy

    private val poseStack = PoseStack()
    private val projection = Projection()

    private val projectionMatrixBufferLazy = lazy(NONE) {
        ProjectionMatrixBuffer("$label atlas")
    }
    private val projectionMatrixBuffer by projectionMatrixBufferLazy

    private val closed = AtomicBoolean()

    protected abstract fun renderTiles(): Map<Identifier, Rect2i>

    /** Called on [Util.backgroundExecutor]. */
    protected abstract fun buildAtlas(images: Map<Identifier, ByteArray>): A

    fun render(): CompletableFuture<A> = try {
        framebuffer.clearColorAndDepth()
        RenderSystem.backupProjectionMatrix()
        val tileRects = try {
            projection.setupOrtho(
                -1000.0F,
                1000.0F,
                textureSize.toFloat(),
                textureSize.toFloat(),
                true,
            )
            RenderSystem.setProjectionMatrix(
                projectionMatrixBuffer.getBuffer(projection),
                ProjectionType.ORTHOGRAPHIC,
            )
            withOutputTextureOverride(
                framebuffer.colorTextureView,
                framebuffer.depthTextureView,
                ::renderTiles,
            )
        } finally {
            RenderSystem.restoreProjectionMatrix()
        }

        readbackAsync(tileRects)
    } catch (throwable: Throwable) {
        close()
        CompletableFuture.failedFuture(throwable)
    }

    protected fun tileRect(index: Int) = Rect2i(
        (index % tilesPerRow) * tileSize,
        (index / tilesPerRow) * tileSize,
        tileSize,
        tileSize,
    )

    protected inline fun withTile(rect: Rect2i, block: PoseStack.() -> Unit) {
        poseStack.pushPose()
        try {
            RenderSystem.enableScissorForRenderTypeDraws(
                rect.x,
                textureSize - rect.y - rect.height,
                rect.width,
                rect.height,
            )
            try {
                poseStack.block()
            } finally {
                RenderSystem.disableScissorForRenderTypeDraws()
            }
        } finally {
            poseStack.popPose()
        }
    }

    private fun readbackAsync(tileRects: Map<Identifier, Rect2i>): CompletableFuture<A> {
        val colorTexture = requireNotNull(framebuffer.colorTexture) {
            "$label atlas framebuffer has no color texture"
        }
        val readbackBuffer = gpuDevice.createBuffer(
            { "$label atlas readback" },
            GpuBuffer.USAGE_MAP_READ or GpuBuffer.USAGE_COPY_DST,
            textureSize.toLong() * textureSize * GpuFormat.RGBA8_UNORM.blockSize(),
        )
        val result = CompletableFuture<A>()

        try {
            colorTexture.copyTo(readbackBuffer) {
                processReadback(readbackBuffer, tileRects, result)
            }
        } catch (throwable: Throwable) {
            closeReadbackResources(readbackBuffer)?.let(throwable::addSuppressed)
            result.completeExceptionally(label, throwable)
        }

        return result
    }

    private fun processReadback(
        readbackBuffer: GpuBuffer,
        tileRects: Map<Identifier, Rect2i>,
        result: CompletableFuture<A>,
    ) {
        val atlasPixels = if (result.isCancelled) {
            null
        } else {
            try {
                readbackBuffer.readFully()
            } catch (throwable: Throwable) {
                closeReadbackResources(readbackBuffer)?.let(throwable::addSuppressed)
                result.completeExceptionally(label, throwable)
                return
            }
        }

        val closeFailure = closeReadbackResources(readbackBuffer)
        if (closeFailure != null) {
            MemoryUtil.memFree(atlasPixels)
            result.completeExceptionally(label, closeFailure)
            return
        }
        if (atlasPixels == null) {
            return
        }

        encodeAtlasAsync(label, tileSize, textureSize, atlasPixels, tileRects, result, ::buildAtlas)
    }

    private fun closeReadbackResources(readbackBuffer: GpuBuffer): Throwable? {
        var failure: Throwable? = null
        try {
            readbackBuffer.close()
        } catch (throwable: Throwable) {
            failure = throwable
        }

        try {
            close()
        } catch (throwable: Throwable) {
            failure?.addSuppressed(throwable) ?: run { failure = throwable }
        }
        return failure
    }

    private fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        if (projectionMatrixBufferLazy.isInitialized()) {
            projectionMatrixBuffer.close()
        }
        if (framebufferLazy.isInitialized()) {
            framebuffer.destroyBuffers()
        }
        if (submitNodeStorageLazy.isInitialized()) {
            submitNodeStorage.drainPhases(Consumers.nop())
        }
        if (featureRenderDispatcherLazy.isInitialized()) {
            featureRenderDispatcher.close()
        }
    }
}
