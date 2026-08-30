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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.ScaffoldStyle
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal object ScaffoldCandidatePlanner {

    private const val MIN_HORIZONTAL_MOVEMENT_SQ = 1.0E-4

    fun collect(
        target: LivingEntity,
        visualPos: Vec3,
        movement: Vec3,
        style: ScaffoldStyle,
    ): List<BlockPos> {
        val base = blockBelow(visualPos)
        val horizontal = Vec3(movement.x, 0.0, movement.z)
        val candidates = when (style) {
            ScaffoldStyle.NORMAL -> normalCandidates(base, visualPos, horizontal)
            ScaffoldStyle.TELLY -> tellyCandidates(target, base, visualPos, horizontal)
            ScaffoldStyle.TOWER -> towerCandidates(target, base, horizontal)
        }
        return candidates.map(BlockPos::immutable).filter(::canRenderFakeBlock).distinct()
    }

    private fun normalCandidates(base: BlockPos, visualPos: Vec3, horizontal: Vec3): List<BlockPos> {
        if (horizontal.lengthSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ) {
            return listOf(base)
        }
        val behind = visualPos.subtract(horizontal.normalize().scale(0.7))
        return listOf(base, blockBelow(behind))
    }

    private fun tellyCandidates(
        target: LivingEntity,
        base: BlockPos,
        visualPos: Vec3,
        horizontal: Vec3,
    ): List<BlockPos> {
        if (target.onGround() && target.deltaMovement.y >= -0.03) {
            return emptyList()
        }
        if (horizontal.lengthSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ) {
            return listOf(base)
        }
        val forward = visualPos.add(horizontal.normalize().scale(0.8))
        return listOf(base, blockBelow(forward))
    }

    private fun towerCandidates(target: LivingEntity, base: BlockPos, horizontal: Vec3): List<BlockPos> {
        val movingUp = target.deltaMovement.y > 0.03
        val mostlyStationary = horizontal.lengthSqr() < 0.018
        return if (movingUp && mostlyStationary) listOf(base, base.below()) else emptyList()
    }

    private fun blockBelow(pos: Vec3): BlockPos = BlockPos.containing(pos.x, pos.y - 0.05, pos.z).below()

    private fun canRenderFakeBlock(pos: BlockPos): Boolean {
        val level = mc.level ?: return false
        if (!level.worldBorder.isWithinBounds(pos)) {
            return false
        }
        val state = pos.stateOrEmpty
        return state.isAir || state.canBeReplaced()
    }
}
