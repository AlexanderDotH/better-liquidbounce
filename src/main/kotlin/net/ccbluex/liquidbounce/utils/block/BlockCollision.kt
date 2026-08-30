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

import net.ccbluex.fastutil.weightedFilterSortedByAtMost
import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.client.isOlderThan1_21_2
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.boundsOrNull
import net.ccbluex.liquidbounce.utils.math.distanceToSqr
import net.ccbluex.liquidbounce.utils.math.iterator
import net.ccbluex.liquidbounce.utils.math.plus
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.network.useItem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Check if box is reaching of specified blocks
 */
inline fun AABB.isBlockAtPosition(
    isCorrectBlock: (Block?) -> Boolean,
): Boolean {
    val blockPos = BlockPos.MutableBlockPos(0, floor(minY).toInt(), 0)

    for (x in floor(minX).toInt()..ceil(maxX).toInt()) {
        for (z in floor(minZ).toInt()..ceil(maxZ).toInt()) {
            blockPos.x = x
            blockPos.z = z

            if (isCorrectBlock(blockPos.getBlock())) {
                return true
            }
        }
    }

    return false
}

/**
 * Check if box intersects with bounding box of specified blocks
 */
inline fun AABB.collideBlockIntersects(
    checkCollisionShape: Boolean = true,
    isCorrectBlock: (Block) -> Boolean
): Boolean {
    for (blockPos in collidingRegion) {
        val blockState = blockPos.state

        if (blockState == null || !isCorrectBlock(blockState.block)) {
            continue
        }

        if (!checkCollisionShape) {
            return true
        }

        val shape = blockState.getCollisionShape(mc.level!!, blockPos)

        if (shape.isEmpty) {
            continue
        }

        if (intersects(shape.bounds())) {
            return true
        }
    }

    return false
}

val AABB.collidingRegion: BoundingBox
    get() = BoundingBox(
        floor(this.minX).toInt(), floor(this.minY).toInt(), floor(this.minZ).toInt(),
        ceil(this.maxX).toInt(), ceil(this.maxY).toInt(), ceil(this.maxZ).toInt(),
    )

fun BlockState.canBeReplacedWith(
    pos: BlockPos,
    usedStack: ItemStack,
): Boolean {
    val placementContext =
        BlockPlaceContext(
            mc.player!!,
            InteractionHand.MAIN_HAND,
            usedStack,
            BlockHitResult(Vec3.atLowerCornerOf(pos), Direction.UP, pos, false),
        )

    return canBeReplaced(
        placementContext,
    )
}
