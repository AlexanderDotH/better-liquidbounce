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
package net.ccbluex.liquidbounce.features.module.modules.render

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.blockesp.BlockEspBoxMode
import net.ccbluex.liquidbounce.features.module.modules.render.blockesp.BlockEspCachedMaskMode
import net.ccbluex.liquidbounce.features.module.modules.render.blockesp.BlockEspDirtyMode
import net.ccbluex.liquidbounce.features.module.modules.render.blockesp.BlockEspRuntime
import net.ccbluex.liquidbounce.features.module.modules.render.blockesp.BlockEspTracerRenderer
import net.ccbluex.liquidbounce.features.module.modules.render.blockesp.BlockEspTracerSettings
import net.ccbluex.liquidbounce.features.module.modules.render.blockesp.BlockMergeKey
import net.ccbluex.liquidbounce.features.module.modules.render.blockesp.BlockTracerSource
import net.ccbluex.liquidbounce.features.module.modules.render.blockesp.defaultBlockEspTargets
import net.ccbluex.liquidbounce.features.module.modules.render.blockesp.migrateLegacyNetherPortalTarget
import net.ccbluex.liquidbounce.render.GenericColorMode
import net.ccbluex.liquidbounce.render.GenericRainbowColorMode
import net.ccbluex.liquidbounce.render.GenericStaticColorMode
import net.ccbluex.liquidbounce.render.MapColorMode
import net.ccbluex.liquidbounce.render.engine.esp.EspFeatureRendererRegistry
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowSource
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.getDynamicTransformsUniform
import net.ccbluex.liquidbounce.render.utils.DistanceFadeUniformValueGroup
import net.ccbluex.liquidbounce.utils.block.AbstractBlockLocationTracker
import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.utils.math.PositionedVoxelShape
import net.ccbluex.liquidbounce.utils.math.mergeAdjacentVoxelShapes
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Matrix4f

/** Allows you to see selected blocks through walls. */
object ModuleBlockESP : ClientModule("BlockESP", ModuleCategories.RENDER) {

    private val modeRuntime = object : BlockEspRuntime {
        override val distanceFadeSettings
            get() = this@ModuleBlockESP.distanceFadeSettings
        override val activeBlockColorMode
            get() = this@ModuleBlockESP.activeBlockColorMode

        override fun trackerIsEmpty() = this@ModuleBlockESP.trackerIsEmpty()
        override fun modeTransforms(useColor: Boolean, modelView: Matrix4f?, colorModulatorAlpha: Int) =
            this@ModuleBlockESP.modeTransforms(useColor, modelView, colorModulatorAlpha)
        override fun collectBlockShapes(
            colorMode: GenericColorMode<Pair<BlockPos, BlockState>>,
            useColor: Boolean,
        ) = this@ModuleBlockESP.collectBlockShapes(colorMode, useColor)
    }

    internal val modeGroup = choices("Mode", 0) {
        arrayOf(
            BoxMode,
            GlowMode,
            ShaderEspMode,
        )
    }
    private val targets by blocks(
        "Targets",
        defaultBlockEspTargets(),
    ).onChange {
        restartTrackerIfRunning()
        it
    }

    private val colorMode = choices("ColorMode", 0) {
        arrayOf(
            MapColorMode(it),
            GenericStaticColorMode(it, Color4b(255, 179, 72, 50)),
            GenericRainbowColorMode(it)
        )
    }.apply {
        onChanged { markDirtyForModes() }
    }

    internal val distanceFadeSettings = tree(DistanceFadeUniformValueGroup())
    private val mergeAdjacent by boolean("MergeAdjacent", false).onChanged {
        markDirtyForModes()
    }

    private val tracers = tree(BlockEspTracerSettings(this))

    private object BoxMode : BlockEspBoxMode(modeRuntime)

    object GlowMode : BlockEspCachedMaskMode("Glow", runtimeProvider = { modeRuntime }) {
        override val style = EspGlowStyle(
            radius = 4f,
            softness = 0.5f,
            intensity = 0f,
            coreSize = 2f,
            opacity = 1f,
        )
    }

    object ShaderEspMode : BlockEspCachedMaskMode("ShaderESP", runtimeProvider = { modeRuntime }) {
        private val styleConfig = EspGlowStyleConfig(this)

        override val style: EspGlowStyle
            get() = styleConfig.style
    }

    internal val activeShaderMode: BlockEspCachedMaskMode?
        get() = (modeGroup.activeMode as? BlockEspCachedMaskMode)?.takeIf { it.running }

    init {
        EspFeatureRendererRegistry.registerGlow(
            id = "module:block_esp",
            source = EspGlowSource.BLOCK_ESP,
            style = { activeShaderMode?.style },
            drawMask = { target -> activeShaderMode?.drawMask(target) == true },
        )
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacyNetherPortalTarget(jsonObject)
    }

    override fun onEnabled() {
        ChunkScanner.subscribe(BlockTracker)
    }

    override fun onDisabled() {
        ChunkScanner.unsubscribe(BlockTracker)
        markDirtyForModes()
    }

    private fun restartTrackerIfRunning() {
        if (!running) return

        onDisabled()
        onEnabled()
    }

    private fun markDirtyForModes() {
        modeGroup.modes.filterIsInstance<BlockEspDirtyMode>().forEach(BlockEspDirtyMode::markDirty)
    }

    internal val activeBlockColorMode: GenericColorMode<Pair<BlockPos, BlockState>>
        get() = colorMode.activeMode

    internal fun trackerIsEmpty(): Boolean = BlockTracker.isEmpty()

    internal fun modeTransforms(
        useColor: Boolean,
        modelView: Matrix4f? = null,
        colorModulatorAlpha: Int = -1,
    ) = getDynamicTransformsUniform(
        modelView = modelView,
        colorModulator = if (useColor) {
            Color4b.WHITE
        } else {
            colorMode.activeMode
                .getColor(BlockPos.ZERO to Blocks.AIR.defaultBlockState())
                .let {
                    if (colorModulatorAlpha == -1) {
                        it
                    } else {
                        it.alpha(colorModulatorAlpha)
                    }
                }
        },
    )

    @Suppress("unused")
    private val tracerRenderHandler = handler<WorldRenderEvent> { event ->
        if (!tracers.running || BlockTracker.isEmpty()) return@handler

        val sources = buildList {
            forEachTrackedBlocks { blockPos, blockState, _ ->
                add(BlockTracerSource(blockPos, blockState))
            }
        }
        val activeColorMode = colorMode.activeMode
        BlockEspTracerRenderer.render(
            event = event,
            sources = sources,
            maximumDistance = distanceFadeSettings.farEnd.toDouble(),
            lineWidth = tracers.lineWidth,
            style = tracers.style,
        ) { blockPos, blockState ->
            activeColorMode.getColor(blockPos to blockState)
        }
    }

    private fun forEachTrackedBlocks(
        block: (blockPos: BlockPos, blockState: BlockState, outlineShape: VoxelShape) -> Unit,
    ) {
        for ((blockPos, t) in BlockTracker.iterate()) {
            val blockState = t.state
            val outlineShape = t.shape
            block(blockPos, blockState, outlineShape)
        }
    }

    internal fun collectBlockShapes(
        colorMode: GenericColorMode<Pair<BlockPos, BlockState>>,
        useColor: Boolean,
    ): List<PositionedVoxelShape<BlockMergeKey>> {
        val shapes = buildList {
            forEachTrackedBlocks { blockPos, blockState, outlineShape ->
                val color = if (useColor) colorMode.getColor(blockPos to blockState) else null
                add(
                    PositionedVoxelShape(
                        blockPos = blockPos.asLong(),
                        key = BlockMergeKey(blockState.block, color),
                        shape = outlineShape,
                    )
                )
            }
        }

        return if (mergeAdjacent) shapes.mergeAdjacentVoxelShapes() else shapes
    }

    private class TrackedState(@JvmField val state: BlockState, @JvmField val shape: VoxelShape)

    private object BlockTracker : AbstractBlockLocationTracker.BlockPos2State<TrackedState>() {
        override fun getStateFor(pos: BlockPos, state: BlockState): TrackedState? {
            return if (!state.isAir && state.block in targets) {
                TrackedState(state, state.getShape(world, pos))
            } else {
                null
            }
        }

        override fun onUpdated() {
            markDirtyForModes()
        }
    }

    fun showTracers(): Boolean = running && tracers.enabled

}
