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

import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.boundsOrNull
import net.minecraft.core.BlockPos
import net.minecraft.core.TypedInstance
import net.minecraft.core.Vec3i
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

fun Vec3i.toBlockPos() = BlockPos(this)

val BlockPos.state: BlockState? get() = mc.level?.getBlockState(this)

@Deprecated(
    "Use BlockPos.state or BlockPos.stateOrEmpty instead",
    replaceWith = ReplaceWith("this.state", imports = ["net.ccbluex.liquidbounce.utils.block.state"]),
    level = DeprecationLevel.ERROR,
)
@JvmName("getState-deprecated")
inline fun BlockPos.getState() = state

val BlockPos.stateOrEmpty: BlockState get() = state ?: Blocks.VOID_AIR.defaultBlockState()

fun BlockPos.getBlock(): Block? = state?.block

fun BlockPos.getCenterDistanceSquared() = this.distToCenterSqr(player.position())

fun BlockPos.getCenterDistanceSquaredEyes() = this.distToCenterSqr(player.eyePosition)

val BlockState.isBed: Boolean
    get() = block is BedBlock

val TypedInstance<Block>.isAnyChest: Boolean
    get() = this.`is`(Blocks.CHEST)
        || this.`is`(Blocks.TRAPPED_CHEST)
        || this.`is`(Blocks.ENDER_CHEST)
        || this.`is`(BlockTags.COPPER_CHESTS)

/**
 * Converts this [BlockPos] to an immutable one if needed.
 */
val BlockPos.immutable: BlockPos get() = if (this is BlockPos.MutableBlockPos) this.immutable() else this

/**
 * Returns the block box outline of the block at the position. If the block is air, it will return an empty box.
 * Outline Box should be used for rendering purposes only.
 *
 * Returns [FULL_BLOCK_BOX] when block is air or does not exist.
 */
val BlockPos.outlineBox: AABB
    get() {
        val blockState = state ?: return FULL_BLOCK_BOX
        if (blockState.isAir) {
            return FULL_BLOCK_BOX
        }

        val outlineShape = blockState.getShape(world, this)
        return outlineShape.boundsOrNull() ?: FULL_BLOCK_BOX
    }

val BlockPos.collisionShape: VoxelShape
    get() = state?.getCollisionShape(world, this) ?: Shapes.empty()

val BlockPos.outlineShape: VoxelShape
    get() = state?.getShape(world, this) ?: Shapes.empty()

fun BlockState.outlineBox(blockPos: BlockPos): AABB {
    val outlineShape = this.getShape(world, blockPos)

    return outlineShape.boundsOrNull() ?: FULL_BLOCK_BOX
}


/**
 * Some blocks like slabs or stairs must be placed on upper side in order to be placed correctly.
 */
val Block.mustBePlacedOnUpperSide: Boolean
    get() {
        return this is SlabBlock || this is StairBlock
    }
