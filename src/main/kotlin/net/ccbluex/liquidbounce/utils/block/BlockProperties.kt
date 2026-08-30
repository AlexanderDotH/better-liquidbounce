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

import net.ccbluex.liquidbounce.utils.client.isOlderThan1_21_2
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.level.block.state.BlockState

fun BlockState.isNotBreakable(pos: BlockPos) = !isBreakable(pos)

fun BlockState.isBreakable(pos: BlockPos): Boolean {
    return !isAir && (player.isCreative || getDestroySpeed(world, pos) >= 0f)
}

fun BlockPos?.fallDamageMultiplier(entity: Entity): Float =
    this?.getBlock()?.fallDamageMultiplier(entity) ?: 1f

fun Block?.fallDamageMultiplier(entity: Entity): Float =
    when (this) {
        Blocks.WATER, Blocks.COBWEB, Blocks.POWDER_SNOW -> 0f
        Blocks.HAY_BLOCK, Blocks.HONEY_BLOCK -> 0.2f
        Blocks.SLIME_BLOCK -> if (entity.isSuppressingBounce && isOlderThan1_21_2) 1f else 0f
        is BedBlock -> 0.5f
        else -> 1f
    }

fun BlockPos.isBlastResistant(): Boolean {
    return getBlock()!!.explosionResistance >= 600f
}

@Suppress("UnusedReceiverParameter")
fun RespawnAnchorBlock.isCharged(state: BlockState): Boolean {
    return state.getValue(RespawnAnchorBlock.CHARGE) > 0
}

/**
 * Returns the second bed block position that might not exist (normally beds are two blocks long tho).
 */
@Suppress("UnusedReceiverParameter")
fun BedBlock.getPotentialSecondBedBlock(state: BlockState, pos: BlockPos): BlockPos {
    return pos.relative((state.getValue(HorizontalDirectionalBlock.FACING)).opposite)
}
