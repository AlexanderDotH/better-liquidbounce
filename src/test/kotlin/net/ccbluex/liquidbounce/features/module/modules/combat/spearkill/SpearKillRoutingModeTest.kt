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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `routing exposes Direct AStar NetworkOptimized and Instant`() {
        assertEquals(
            listOf("Direct", "AStar", "NetworkOptimized", "Instant"),
            SpearKillRoutingMode.entries.map { it.tag },
        )
    }

    @Test
    fun `Instant retains two server boundaries while other direct modes return immediately`() {
        assertEquals(0, spearKillStrikeHoldTicks(SpearKillRoutingMode.DIRECT))
        assertEquals(SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS, spearKillStrikeHoldTicks(SpearKillRoutingMode.A_STAR))
        assertEquals(0, spearKillStrikeHoldTicks(SpearKillRoutingMode.NETWORK_OPTIMIZED))
        assertEquals(SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS, spearKillStrikeHoldTicks(SpearKillRoutingMode.INSTANT))
    }

    @Test
    fun `Instant predicts its terminal hit during the server evaluation tick`() {
        assertEquals(
            1,
            spearKillDirectRouteHitTicks(
                routingMode = SpearKillRoutingMode.INSTANT,
                outboundTickCount = 24,
                stepWaitTicks = 4,
                strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
            ),
        )
        assertEquals(
            spearKillDirectPacketHitTicks(stepCount = 24, stepWaitTicks = 0, strikeHoldTicks = 0),
            spearKillDirectRouteHitTicks(
                routingMode = SpearKillRoutingMode.DIRECT,
                outboundTickCount = 24,
                stepWaitTicks = 0,
                strikeHoldTicks = 0,
            ),
        )
    }

    @Test
    fun `Instant does not replan after committing its one hop route`() {
        assertFalse(shouldTrackSpearKillPacketTarget(SpearKillRoutingMode.INSTANT))
        assertTrue(shouldTrackSpearKillPacketTarget(SpearKillRoutingMode.DIRECT))
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
