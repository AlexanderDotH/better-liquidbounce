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
package net.ccbluex.liquidbounce.features.block.contract

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

data class BlockPlacementTarget(
    /**
     * BlockPos which is right-clicked
     */
    val interactedBlockPos: BlockPos,
    /**
     * Block pos at which a new block is placed
     */
    val placedBlock: BlockPos,
    val direction: Direction,
    /**
     * Exact point on [interactedBlockPos] selected by target finding.
     */
    val interactionPoint: Vec3,
    /**
     * Some blocks must be placed above a certain height of the block. For example stairs and slabs must be placed
     * at the upper half (=> minY = 0.5) in order to be placed correctly
     */
    val minPlacementY: Double,
    val rotation: Rotation
) {

    val blockHitResult: BlockHitResult
        get() = BlockHitResult(
            interactionPoint,
            direction,
            interactedBlockPos,
            false
        )

    fun doesCrosshairTargetMatchRequirements(crosshairTarget: BlockHitResult): Boolean {
        return when {
            crosshairTarget.type != HitResult.Type.BLOCK -> false
            crosshairTarget.blockPos != this.interactedBlockPos -> false
            crosshairTarget.direction != this.direction -> false
            crosshairTarget.location.y < this.minPlacementY -> false
            else -> true
        }
    }
}

class PlacementPlan(
    val targetPos: BlockPos,
    val placementTarget: BlockPlacementTarget,
    val hotbarItemSlot: HotbarItemSlot
) {
    fun doesCorrespondTo(rayTraceResult: BlockHitResult, sideMustMatch: Boolean = true): Boolean {
        return rayTraceResult.type == HitResult.Type.BLOCK
            && rayTraceResult.blockPos == this.placementTarget.interactedBlockPos
            && (!sideMustMatch || rayTraceResult.direction == this.placementTarget.direction)
    }
}
