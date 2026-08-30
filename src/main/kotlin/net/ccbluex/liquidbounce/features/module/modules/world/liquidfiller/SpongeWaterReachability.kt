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
package net.ccbluex.liquidbounce.features.module.modules.world.liquidfiller

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BucketPickup
import net.minecraft.world.level.block.SpongeBlock
import net.minecraft.world.level.block.state.BlockState

/**
 * Tests whether a candidate sponge position can reach a water target with vanilla sponge traversal rules.
 *
 * @see SpongeBlock.removeWaterBreadthFirstSearch
 */
internal class SpongeWaterReachability(
    private val isWater: (BlockState) -> Boolean = { state -> state.fluidState.`is`(FluidTags.WATER) },
    private val stateAt: (BlockPos) -> BlockState?,
) {

    fun canAbsorbFrom(spongePos: BlockPos, waterPos: BlockPos): Boolean {
        if (spongePos == waterPos) {
            return true
        }

        var reachedTarget = false
        BlockPos.breadthFirstTraversal(
            spongePos,
            SpongeBlock.MAX_DEPTH,
            SpongeBlock.MAX_COUNT + 1,
            { pos, consumer ->
                Direction.entries.forEach { direction -> consumer.accept(pos.relative(direction)) }
            },
            { pos ->
                traversalStatus(pos, spongePos, waterPos) {
                    reachedTarget = true
                }
            },
        )

        return reachedTarget
    }

    private fun traversalStatus(
        pos: BlockPos,
        spongePos: BlockPos,
        waterPos: BlockPos,
        onTargetReached: () -> Unit,
    ): BlockPos.TraversalNodeStatus {
        if (pos == spongePos) {
            return BlockPos.TraversalNodeStatus.ACCEPT
        }

        val state = stateAt(pos) ?: return BlockPos.TraversalNodeStatus.SKIP
        if (!isWater(state)) {
            return BlockPos.TraversalNodeStatus.SKIP
        }

        if (pos == waterPos) {
            onTargetReached()
            return BlockPos.TraversalNodeStatus.STOP
        }

        return if (state.isAbsorbableWaterBlock()) {
            BlockPos.TraversalNodeStatus.ACCEPT
        } else {
            BlockPos.TraversalNodeStatus.SKIP
        }
    }

    private fun BlockState.isAbsorbableWaterBlock(): Boolean {
        val block = block
        return block is BucketPickup ||
            block === Blocks.KELP ||
            block === Blocks.KELP_PLANT ||
            block === Blocks.SEAGRASS ||
            block === Blocks.TALL_SEAGRASS
    }
}
