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

internal object PortalCandidateSelector {

    fun findBest(center: BlockPos): NetherPortal? = selectBest(
        PortalCandidateGeometry.around(center).map(PortalCandidateGeometry::createPortal),
        ::score,
    )

    internal fun <T> selectBest(candidates: Sequence<T>, scoreCandidate: (T) -> Int?): T? {
        var result: T? = null
        var resultScore: Int? = null
        for (candidate in candidates) {
            val candidateScore = scoreCandidate(candidate) ?: continue
            if (resultScore == null || resultScore < candidateScore) {
                result = candidate
                resultScore = candidateScore
            }
        }
        return result
    }

    private fun score(portal: NetherPortal): Int? {
        portal.calculateScore()
        if (!portal.isValid()) {
            return null
        }
        return portal.score
    }
}
