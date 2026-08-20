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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

internal data class MerchantTargetCandidate<T>(
    val entity: T,
    val entityId: Int,
    val boxedDistance: Double,
    val visible: Boolean,
    val alive: Boolean,
    val adult: Boolean,
    val sleeping: Boolean,
)

internal object MerchantTargetSelector {

    fun <T> select(
        candidates: Iterable<MerchantTargetCandidate<T>>,
        range: Float,
        wallRange: Float,
        canRetry: (Int) -> Boolean,
    ): MerchantTargetCandidate<T>? {
        val effectiveWallRange = wallRange.coerceAtMost(range)

        return candidates.asSequence()
            .filter { it.alive && it.adult && !it.sleeping && canRetry(it.entityId) }
            .filter { it.boxedDistance <= if (it.visible) range else effectiveWallRange }
            .minByOrNull(MerchantTargetCandidate<T>::boxedDistance)
    }
}
