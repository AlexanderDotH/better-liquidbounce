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

@file:JvmName("BlockExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.block

import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.DoubleBlockCombiner
import net.minecraft.world.level.block.SupportType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * Basically [BlockGetter.raycast] but this method allows us to exclude blocks using [exclude].
 */
@Suppress("SpellCheckingInspection")
fun BlockGetter.raycast(
    context: ClipContext,
    exclude: Collection<BlockPos>?,
    include: BlockPos?,
    maxBlastResistance: Float?
): BlockHitResult {
    val traversal = BlockRaycastTraversal(this, exclude, include, maxBlastResistance)
    return BlockGetter.traverseBlocks(
        context.from,
        context.to,
        context,
        traversal::hit,
        traversal::miss,
    )
}

fun BlockPos.canStandOn(): Boolean {
    return this.state?.isFaceSturdy(world, this, Direction.UP, SupportType.CENTER) ?: false
}

fun BlockState?.anotherChestPartDirection(): Direction? {
    if (this?.block !is ChestBlock) return null

    if (ChestBlock.getBlockType(this) === DoubleBlockCombiner.BlockType.SINGLE) {
        return null
    }

    return ChestBlock.getConnectedDirection(this)
}

fun BlockState?.anotherBedPartDirection(): Direction? {
    if (this?.block !is BedBlock) return null

    // [body|head] -> (facing)
    val bedFacing = this.getValue(BedBlock.FACING)

    return if (BedBlock.getBlockType(this) == DoubleBlockCombiner.BlockType.FIRST) {
        bedFacing.opposite
    } else {
        bedFacing
    }
}
