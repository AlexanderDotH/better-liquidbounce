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
package net.ccbluex.liquidbounce.features.module.modules.movement.avoidhazards

import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.entity.isOnMagmaBlock
import net.ccbluex.liquidbounce.utils.math.iterateBlockPos
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

internal object AvoidHazardCollision {
    fun isUnsafe(boundingBox: AABB, level: ClientLevel, modes: Collection<Avoid>): Boolean {
        if (Avoid.MAGMA in modes && boundingBox.isOnMagmaBlock()) return true
        return boundingBox.iterateBlockPos().any { pos -> intersectsBlock(boundingBox, level, modes, pos) }
    }

    private fun intersectsBlock(
        boundingBox: AABB,
        level: ClientLevel,
        modes: Collection<Avoid>,
        pos: BlockPos,
    ): Boolean {
        val state = pos.state ?: return false
        return modes.any { mode -> intersectsHazard(boundingBox, level, pos, state, mode) }
    }

    private fun intersectsHazard(
        boundingBox: AABB,
        level: ClientLevel,
        pos: BlockPos,
        state: BlockState,
        mode: Avoid,
    ): Boolean {
        val fluid = state.fluidState
        if (!mode.test(state.block, fluid, pos)) return false
        return when (mode) {
            Avoid.MAGMA -> false
            Avoid.LAVA -> fluid.getShape(level, pos).let { !it.isEmpty && boundingBox.intersects(it.bounds().move(pos)) }
            Avoid.CACTI -> boundingBox.inflate(CACTUS_BLOCK_MARGIN, 0.0, CACTUS_BLOCK_MARGIN).intersects(pos)
            else -> state.getShape(level, pos).let { !it.isEmpty && boundingBox.intersects(it.bounds().move(pos)) }
        }
    }

    private const val CACTUS_BLOCK_MARGIN = 0.001
}
