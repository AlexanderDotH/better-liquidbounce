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
package net.ccbluex.liquidbounce.features.module.modules.world.autobuild

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

internal data class PortalCandidateGeometry(
    val origin: BlockPos,
    val down: Boolean,
    val direction: Direction,
    val rotated: Direction,
) {

    fun createPortal() = NetherPortal(origin, down, direction, rotated)

    companion object {
        fun around(center: BlockPos): Sequence<PortalCandidateGeometry> =
            Direction.BY_2D_DATA.asSequence().flatMap { direction ->
                forDirection(center, direction)
            }

        private fun forDirection(center: BlockPos, direction: Direction): Sequence<PortalCandidateGeometry> =
            VERTICAL_OFFSETS.asSequence().flatMap { verticalOffset ->
                forHeight(center, direction, verticalOffset)
            }

        private fun forHeight(
            center: BlockPos,
            direction: Direction,
            verticalOffset: Int,
        ): Sequence<PortalCandidateGeometry> = LATERAL_OFFSETS.asSequence().map { lateralOffset ->
            create(center, direction, verticalOffset, lateralOffset)
        }

        private fun create(
            center: BlockPos,
            direction: Direction,
            verticalOffset: Int,
            lateralOffset: Int,
        ): PortalCandidateGeometry {
            val rotated = direction.clockWise
            val portalOrigin = center.mutable().move(direction)
            if (lateralOffset == -1) {
                portalOrigin.move(rotated.opposite)
            }
            if (verticalOffset == -1) {
                portalOrigin.move(Direction.DOWN)
            }
            return PortalCandidateGeometry(portalOrigin, verticalOffset == -1, direction, rotated)
        }

        private val VERTICAL_OFFSETS = -1..0
        private val LATERAL_OFFSETS = 0 downTo -1
    }
}
