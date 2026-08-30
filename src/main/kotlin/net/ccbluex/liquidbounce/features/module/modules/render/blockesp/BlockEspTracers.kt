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
package net.ccbluex.liquidbounce.features.module.modules.render.blockesp

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.module.modules.render.tracers.TracerRenderBatch
import net.ccbluex.liquidbounce.features.module.modules.render.tracers.TracerSegment
import net.ccbluex.liquidbounce.render.engine.esp.EspHaloStyleConfig
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.utils.inventory.findBlocksEndingWith
import net.ccbluex.liquidbounce.utils.math.center
import net.ccbluex.liquidbounce.render.engine.type.toVec3f
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentSkipListSet

internal class BlockEspTracerSettings(parent: EventListener? = null) :
    ToggleableValueGroup(parent, "Tracers", false) {
    val lineWidth by float("LineWidth", 1f, 1f..16f)
    private val styleConfig = EspHaloStyleConfig(this)
    val style
        get() = styleConfig.style
}

internal fun defaultBlockEspTargets(): ConcurrentSkipListSet<Block> =
    ConcurrentSkipListSet(findBlocksEndingWith("_BED", "DRAGON_EGG")).apply { add(Blocks.NETHER_PORTAL) }

internal data class BlockTracerSource(val blockPos: BlockPos, val blockState: BlockState)

internal data class BlockTracerTarget(val colorSource: BlockTracerSource, val worldPosition: Vec3)

internal fun collectBlockTracerTargets(sources: Collection<BlockTracerSource>): List<BlockTracerTarget> {
    val regularTargets = sources.filter { it.blockState.block !== Blocks.NETHER_PORTAL }
        .map { BlockTracerTarget(it, it.blockPos.center) }
    val portalTargets = sources.filter { it.blockState.block === Blocks.NETHER_PORTAL }
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
                portalDirections(axis).forEach { direction ->
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
            colorProvider(source.blockPos, source.blockState).with(a = 255),
            eyePosition,
            target.worldPosition.subtract(cameraPosition).toVec3f(),
        )
    }
    return TracerRenderBatch(segments, lineWidth)
}

private val PORTAL_X_DIRECTIONS = arrayOf(Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST)
private val PORTAL_Z_DIRECTIONS = arrayOf(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH)
