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
package net.ccbluex.liquidbounce.utils.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult

internal class BlockRaycastTraversal(
    private val world: BlockGetter,
    exclude: Collection<BlockPos>?,
    include: BlockPos?,
    maxBlastResistance: Float?,
) {
    private val filter = BlockRaycastFilter(exclude, include, maxBlastResistance)

    fun hit(context: ClipContext, pos: BlockPos): BlockHitResult? {
        val excluded = filter.isExcluded(pos)
        val blockState = resolveBlockState(pos, excluded)
        val fluidState = resolveFluidState(pos, excluded)
        val from = context.from
        val to = context.to

        val blockShape = context.getBlockShape(blockState, world, pos)
        val blockHitResult = world.clipWithInteractionOverride(from, to, pos, blockShape, blockState)
        val fluidShape = context.getFluidShape(fluidState, world, pos)
        val fluidHitResult = fluidShape.clip(from, to, pos)
        return nearestHit(context, blockHitResult, fluidHitResult)
    }

    private fun resolveBlockState(pos: BlockPos, excluded: Boolean): BlockState {
        if (excluded) {
            return Blocks.VOID_AIR.defaultBlockState()
        }
        if (filter.isIncluded(pos)) {
            return Blocks.OBSIDIAN.defaultBlockState()
        }

        val blockState = world.getBlockState(pos)
        return if (filter.allows(blockState)) blockState else Blocks.VOID_AIR.defaultBlockState()
    }

    private fun resolveFluidState(pos: BlockPos, excluded: Boolean): FluidState {
        if (excluded) {
            return Fluids.EMPTY.defaultFluidState()
        }

        val fluidState = world.getFluidState(pos)
        return if (filter.allows(fluidState)) fluidState else Fluids.EMPTY.defaultFluidState()
    }

    private fun nearestHit(
        context: ClipContext,
        blockHitResult: BlockHitResult?,
        fluidHitResult: BlockHitResult?,
    ): BlockHitResult? {
        val blockHitDistance = blockHitResult?.let {
            context.from.distanceToSqr(blockHitResult.location)
        } ?: Double.MAX_VALUE
        val fluidHitDistance = fluidHitResult?.let {
            context.from.distanceToSqr(fluidHitResult.location)
        } ?: Double.MAX_VALUE
        return if (blockHitDistance <= fluidHitDistance) blockHitResult else fluidHitResult
    }

    fun miss(context: ClipContext): BlockHitResult {
        val direction = context.from.subtract(context.to)
        return BlockHitResult.miss(
            context.to,
            Direction.getApproximateNearest(direction.x, direction.y, direction.z),
            BlockPos.containing(context.to),
        )
    }
}

private class BlockRaycastFilter(
    private val exclude: Collection<BlockPos>?,
    private val include: BlockPos?,
    private val maxBlastResistance: Float?,
) {
    fun isExcluded(pos: BlockPos): Boolean = exclude?.let { pos in it } ?: false

    fun isIncluded(pos: BlockPos): Boolean = include != null && pos == include

    fun allows(blockState: BlockState): Boolean = maxBlastResistance?.let {
        blockState.block.explosionResistance < it
    } != true

    fun allows(fluidState: FluidState): Boolean = maxBlastResistance?.let {
        fluidState.explosionResistance < it
    } != true
}
