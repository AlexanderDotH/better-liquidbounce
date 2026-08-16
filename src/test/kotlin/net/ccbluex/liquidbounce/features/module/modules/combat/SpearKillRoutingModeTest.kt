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

package net.ccbluex.liquidbounce.features.module.modules.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpearKillRoutingModeTest {

    @Test
    fun `missing routing retains legacy direct behavior`() {
        assertEquals(
            SpearKillRoutingMode.DIRECT,
            resolveSpearKillRoutingMode(
                configuredRouting = null,
                legacyAStarEnabled = null,
            ),
        )
        assertEquals(
            SpearKillRoutingMode.DIRECT,
            resolveSpearKillRoutingMode(
                configuredRouting = null,
                legacyAStarEnabled = false,
            ),
        )
    }

    @Test
    fun `legacy enabled AStar migrates to AStar routing`() {
        assertEquals(
            SpearKillRoutingMode.A_STAR,
            resolveSpearKillRoutingMode(
                configuredRouting = null,
                legacyAStarEnabled = true,
            ),
        )
    }

    @Test
    fun `routing exposes Direct AStar and NetworkOptimized`() {
        assertEquals(
            listOf("Direct", "AStar", "NetworkOptimized"),
            SpearKillRoutingMode.entries.map { it.tag },
        )
    }

    @Test
    fun `Direct and NetworkOptimized return immediately while standalone AStar retains its hold`() {
        assertEquals(0, spearKillStrikeHoldTicks(SpearKillRoutingMode.DIRECT))
        assertEquals(SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS, spearKillStrikeHoldTicks(SpearKillRoutingMode.A_STAR))
        assertEquals(0, spearKillStrikeHoldTicks(SpearKillRoutingMode.NETWORK_OPTIMIZED))
    }

    @Test
    fun `explicit routing remains authoritative over legacy AStar state`() {
        for (routing in SpearKillRoutingMode.entries) {
            assertEquals(
                routing,
                resolveSpearKillRoutingMode(
                    configuredRouting = routing,
                    legacyAStarEnabled = true,
                ),
            )
        }
    }

    @Test
    fun `resolving a migrated routing value is idempotent`() {
        for (legacyAStarEnabled in listOf<Boolean?>(null, false, true)) {
            val migratedRouting = resolveSpearKillRoutingMode(
                configuredRouting = null,
                legacyAStarEnabled = legacyAStarEnabled,
            )

            assertEquals(
                migratedRouting,
                resolveSpearKillRoutingMode(
                    configuredRouting = migratedRouting,
                    legacyAStarEnabled = legacyAStarEnabled,
                ),
            )
        }
    }
}
