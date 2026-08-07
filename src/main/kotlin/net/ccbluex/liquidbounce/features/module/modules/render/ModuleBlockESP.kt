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
import com.mojang.blaze3d.pipeline.RenderTarget
import kotlinx.atomicfu.atomic
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.DrawOutlinesEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.render.CachedMeshStorage
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.GenericRainbowColorMode
import net.ccbluex.liquidbounce.render.GenericStaticColorMode
import net.ccbluex.liquidbounce.render.MapColorMode
import net.ccbluex.liquidbounce.render.addShapeFaces
import net.ccbluex.liquidbounce.render.addShapeOutlines
import net.ccbluex.liquidbounce.render.buildMesh
import net.ccbluex.liquidbounce.render.drawGenericBlockESP
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowContributionRole
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.EspHaloStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderRenderer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.getDynamicTransformsUniform
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.translate
import net.ccbluex.liquidbounce.render.utils.DistanceFadeUniformValueGroup
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.block.AbstractBlockLocationTracker
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.ccbluex.liquidbounce.utils.inventory.findBlocksEndingWith
import net.ccbluex.liquidbounce.utils.math.PositionedVoxelShape
import net.ccbluex.liquidbounce.utils.math.center
import net.ccbluex.liquidbounce.utils.math.mergeAdjacentVoxelShapes
import net.ccbluex.liquidbounce.utils.math.toVec3f
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Matrix4f
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentSkipListSet

/**
 * BlockESP module
 *
 * Allows you to see selected blocks through walls.
 */

object ModuleBlockESP : ClientModule("BlockESP", ModuleCategories.RENDER) {

    private val modes = choices("Mode", 0) {
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

    private val distanceFade = tree(DistanceFadeUniformValueGroup())
    private val mergeAdjacent by boolean("MergeAdjacent", false).onChanged {
        markDirtyForModes()
    }

    private val tracers = tree(BlockEspTracerSettings(this))

    sealed class Mode(name: String) : net.ccbluex.liquidbounce.config.types.group.Mode(name) {
        final override val parent get() = modes

        protected var useColor = false
        protected val dirtyFlag = atomic(true)

        fun markDirty() {
            if (this.running) {
                dirtyFlag.value = true
            }
        }

        final override fun enable() {
            dirtyFlag.value = true
            super.enable()
        }

        protected fun getDynamicTransformsUniform(
            modelView: Matrix4f? = null,
            colorModulatorAlpha: Int = -1,
        ) = getDynamicTransformsUniform(
            modelView = modelView,
            colorModulator = if (useColor) {
                Color4b.WHITE
            } else {
                val color = colorMode.activeMode.getColor(BlockPos.ZERO to Blocks.AIR.defaultBlockState())
                if (colorModulatorAlpha == -1) color else color.alpha(colorModulatorAlpha)
            },
        )
    }

    private object BoxMode : Mode("Box") {
        private val outline by boolean("Outline", true).onChanged {
            if (!it && running) {
                outlinesRenderState.clearStates()
            }
        }
        private val facesRenderState = CachedMeshStorage("${ModuleBlockESP.name} $name Faces")
        private val outlinesRenderState = CachedMeshStorage("${ModuleBlockESP.name} $name Outlines")

        override fun disable() {
            facesRenderState.clearStates()
            facesRenderState.clearBuffers()
            outlinesRenderState.clearStates()
            outlinesRenderState.clearBuffers()
            super.disable()
        }

        @Suppress("unused")
        private val renderHandler = handler<WorldRenderEvent> { event ->
            if (outline) {
                mc.gameRenderer.mainRenderTarget().drawGenericBlockESP(
                    outlinesRenderState,
                    ClientRenderPipelines.relativeLines(useColor),
                    distanceFade,
                ) {
                    getDynamicTransformsUniform(
                        modelView = event.poseStack.last().pose(),
                        colorModulatorAlpha = 150,
                    )
                }
            }

            mc.gameRenderer.mainRenderTarget().drawGenericBlockESP(
                facesRenderState,
                ClientRenderPipelines.relativeQuads(useColor),
                distanceFade,
            ) {
                getDynamicTransformsUniform(
                    modelView = event.poseStack.last().pose(),
                )
            }
        }

        @Suppress("unused")
        private val tickHandler = handler<GameTickEvent> {
            if (BlockTracker.isEmpty()) {
                facesRenderState.clearStates()
                outlinesRenderState.clearStates()
                return@handler
            }

            if (!dirtyFlag.compareAndSet(expect = true, update = false)) {
                return@handler
            }

            val colorMode = colorMode.activeMode
            useColor = colorMode.isParamSensitive
            val mergedShapes = collectBlockShapes(colorMode, useColor)

            facesRenderState.buildMesh(
                pipeline = ClientRenderPipelines.relativeQuads(useColor),
                origin = player.blockPosition(),
            ) { pose, origin ->
                for (mergedShape in mergedShapes) {
                    pose.withPush {
                        translate(mergedShape.blockPos, origin)
                        addShapeFaces(last().pose(), mergedShape.shape, mergedShape.key.color)
                    }
                }
            }

            if (outline) {
                outlinesRenderState.buildMesh(
                    pipeline = ClientRenderPipelines.relativeLines(useColor),
                    origin = player.blockPosition(),
                ) { pose, meshOrigin ->
                    for (mergedShape in mergedShapes) {
                        pose.withPush {
                            translate(mergedShape.blockPos, meshOrigin)
                            addShapeOutlines(last().pose(), mergedShape.shape, mergedShape.key.color)
                        }
                    }
                }
            }
        }

    }

    sealed class CachedMaskMode(name: String) : Mode(name) {
        private val renderState by lazy { CachedMeshStorage("${ModuleBlockESP.name} $name") }

        override fun disable() {
            renderState.clearStates()
            renderState.clearBuffers()
            super.disable()
        }

        internal fun drawMask(renderTarget: RenderTarget): Boolean =
            renderTarget.drawGenericBlockESP(
                renderState,
                ClientRenderPipelines.outlineQuads(useColor),
                distanceFade,
            ) {
                getDynamicTransformsUniform(
                    colorModulatorAlpha = 255,
                )
            }

        @Suppress("unused")
        private val tickHandler = handler<GameTickEvent> {
            if (BlockTracker.isEmpty()) {
                renderState.clearStates()
                return@handler
            }

            if (!dirtyFlag.compareAndSet(expect = true, update = false)) {
                return@handler
            }

            val colorMode = colorMode.activeMode
            useColor = colorMode.isParamSensitive
            val origin = player.blockPosition()

            renderState.buildMesh(
                pipeline = ClientRenderPipelines.outlineQuads(useColor),
                origin = origin,
            ) { pose, meshOrigin ->
                for (mergedShape in collectBlockShapes(colorMode, useColor)) {
                    pose.withPush {
                        translate(mergedShape.blockPos, meshOrigin)
                        addShapeFaces(last().pose(), mergedShape.shape, mergedShape.key.color?.alpha(255))
                    }
                }
            }
        }
    }

    object GlowMode : CachedMaskMode("Glow") {
        @Suppress("unused")
        private val renderHandler = handler<DrawOutlinesEvent> { event ->
            if (drawMask(event.renderTarget)) {
                event.markDirty()
            }
        }
    }

    object ShaderEspMode : CachedMaskMode("ShaderESP") {
        private val styleConfig = EspGlowStyleConfig(this)

        internal val style: EspGlowStyle
            get() = styleConfig.style
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
        modes.modes.forEach { it.markDirty() }
    }

    @Suppress("unused")
    private val tracerRenderHandler = handler<WorldRenderEvent> { event ->
        if (!tracers.running || BlockTracker.isEmpty()) return@handler

        val sources = buildList {
            forEachTrackedBlocks { blockPos, blockState, _ ->
                add(BlockTracerSource(blockPos, blockState))
            }
        }
        val cameraPosition = event.camera.position()
        val maximumDistance = distanceFade.farEnd.toDouble()
        val activeColorMode = colorMode.activeMode
        val batch = createBlockTracerBatch(
            targets = collectBlockTracerTargets(sources),
            eyePosition = Vec3f.eyeVector(event.camera),
            cameraPosition = cameraPosition,
            maximumDistanceSquared = maximumDistance * maximumDistance,
            lineWidth = tracers.lineWidth,
        ) { blockPos, blockState ->
            activeColorMode.getColor(blockPos to blockState)
        }

        event.renderEnvironment {
            drawTracerBatch(batch, glowMask = false)
        }
        batch.contributeGlowIfPresent {
            EspShaderRenderer.contributeGlow(event, tracers.style, EspGlowContributionRole.HALO_ONLY) {
                drawTracerBatch(it, glowMask = true)
            }
        }
    }

    private inline fun forEachTrackedBlocks(
        block: (blockPos: BlockPos, blockState: BlockState, outlineShape: VoxelShape) -> Unit,
    ) {
        for ((blockPos, t) in BlockTracker.iterate()) {
            val blockState = t.state
            val outlineShape = t.shape
            block(blockPos, blockState, outlineShape)
        }
    }

    private fun collectBlockShapes(
        colorMode: net.ccbluex.liquidbounce.render.GenericColorMode<Pair<BlockPos, BlockState>>,
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

    private data class BlockMergeKey(val block: Block, val color: Color4b?)

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

internal class BlockEspTracerSettings(parent: EventListener? = null) :
    ToggleableValueGroup(parent, "Tracers", false) {

    val lineWidth by float("LineWidth", 1f, 1f..16f)
    private val styleConfig = EspHaloStyleConfig(this)

    val style
        get() = styleConfig.style
}

internal fun defaultBlockEspTargets(): ConcurrentSkipListSet<Block> =
    ConcurrentSkipListSet(findBlocksEndingWith("_BED", "DRAGON_EGG")).apply {
        add(Blocks.NETHER_PORTAL)
    }

internal fun migrateLegacyNetherPortalTarget(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val valuesByName = storedValues.associateBy { it.asJsonObject["name"].asString }
    val legacyToggle = valuesByName[LEGACY_NETHER_PORTALS_SETTING]?.asJsonObject ?: return
    if (!legacyToggle["value"].asBoolean) return

    val storedTargets = valuesByName["Targets"]
        ?.asJsonObject
        ?.get("value")
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?: return
    if (storedTargets.none { it.asString == NETHER_PORTAL_ID }) {
        storedTargets.add(NETHER_PORTAL_ID)
    }
}

internal data class BlockTracerSource(
    val blockPos: BlockPos,
    val blockState: BlockState,
)

internal data class BlockTracerTarget(
    val colorSource: BlockTracerSource,
    val worldPosition: Vec3,
)

internal fun collectBlockTracerTargets(sources: Collection<BlockTracerSource>): List<BlockTracerTarget> {
    val regularTargets = sources
        .filter { it.blockState.block !== Blocks.NETHER_PORTAL }
        .map { BlockTracerTarget(it, it.blockPos.center) }
    val portalTargets = sources
        .filter { it.blockState.block === Blocks.NETHER_PORTAL }
        .groupBy { it.blockState.getValue(NetherPortalBlock.AXIS) }
        .flatMap { (axis, portalSources) -> collectPortalTracerTargets(axis, portalSources) }

    return (regularTargets + portalTargets).sortedBy { it.colorSource.blockPos.asLong() }
}

private fun collectPortalTracerTargets(
    axis: Direction.Axis,
    sources: List<BlockTracerSource>,
): List<BlockTracerTarget> {
    val remaining = sources.associateByTo(HashMap()) { it.blockPos }
    return buildList {
        while (remaining.isNotEmpty()) {
            val seed = remaining.values.minBy { it.blockPos.asLong() }
            val queue = ArrayDeque<BlockTracerSource>()
            val component = mutableListOf<BlockTracerSource>()
            remaining.remove(seed.blockPos)
            queue.add(seed)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                component += current
                for (direction in portalDirections(axis)) {
                    remaining.remove(current.blockPos.relative(direction))?.let(queue::add)
                }
            }

            val center = component.fold(Vec3.ZERO) { sum, source -> sum.add(source.blockPos.center) }
                .scale(1.0 / component.size)
            add(BlockTracerTarget(seed, center))
        }
    }
}

private fun portalDirections(axis: Direction.Axis): Array<Direction> = when (axis) {
    Direction.Axis.X -> PORTAL_X_DIRECTIONS
    Direction.Axis.Z -> PORTAL_Z_DIRECTIONS
    Direction.Axis.Y -> emptyArray()
}

internal fun createBlockTracerBatch(
    targets: Collection<BlockTracerTarget>,
    eyePosition: Vec3f,
    cameraPosition: Vec3,
    maximumDistanceSquared: Double,
    lineWidth: Float,
    colorProvider: (BlockPos, BlockState) -> Color4b,
): TracerRenderBatch {
    val segments = targets.mapNotNull { target ->
        if (target.worldPosition.distanceToSqr(cameraPosition) > maximumDistanceSquared) return@mapNotNull null

        val source = target.colorSource
        TracerSegment(
            color = colorProvider(source.blockPos, source.blockState).with(a = 255),
            eyePosition = eyePosition,
            targetPosition = target.worldPosition.subtract(cameraPosition).toVec3f(),
        )
    }
    return TracerRenderBatch(segments, lineWidth)
}

private val PORTAL_X_DIRECTIONS = arrayOf(Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST)
private val PORTAL_Z_DIRECTIONS = arrayOf(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH)
private const val LEGACY_NETHER_PORTALS_SETTING = "NetherPortals"
private const val NETHER_PORTAL_ID = "minecraft:nether_portal"
