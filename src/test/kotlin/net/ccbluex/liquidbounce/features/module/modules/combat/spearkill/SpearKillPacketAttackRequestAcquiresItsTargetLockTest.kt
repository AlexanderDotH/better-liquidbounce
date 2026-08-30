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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillPacketAttackRequestAcquiresItsTargetLockTest {

    @Test
    fun `packet attack request acquires its target lock during preparation`() {
        assertTrue(shouldAcquireSpearKillPreparationLock(
            packetMovementMode = true,
            attackActive = false,
            attackRequested = true,
            hasTarget = true,
            hasLockedTarget = false,
        ))

        listOf(
            booleanArrayOf(false, false, true, true, false),
            booleanArrayOf(true, true, true, true, false),
            booleanArrayOf(true, false, false, true, false),
            booleanArrayOf(true, false, true, false, false),
            booleanArrayOf(true, false, true, true, true),
        ).forEach { values ->
            assertFalse(shouldAcquireSpearKillPreparationLock(
                packetMovementMode = values[0],
                attackActive = values[1],
                attackRequested = values[2],
                hasTarget = values[3],
                hasLockedTarget = values[4],
            ))
        }
    }

    @Test
    fun `a rejected target is skipped in favor of the next eligible candidate`() {
        val rejected = setOf("blocked")
        val selected = listOf("blocked", "reachable").firstOrNull { candidate ->
            isSpearKillTargetCandidateEligible(
                isCombatSafe = true,
                isAlive = true,
                isInCurrentWorld = true,
                isWithinRange = true,
                isRejected = candidate in rejected,
            )
        }

        assertEquals("reachable", selected)
    }

    @Test
    fun `post-kill chaining tries nearby targets nearest first until one route is reachable`() {
        data class Candidate(val name: String, val distanceSquared: Double)

        val attempted = mutableListOf<String>()
        val selection = selectNearestReachableSpearKillChainTarget(
            candidates = listOf(
                Candidate("far", 25.0),
                Candidate("near-blocked", 4.0),
                Candidate("middle", 9.0),
            ),
            distanceSquared = Candidate::distanceSquared,
            createRoute = { candidate ->
                attempted += candidate.name
                candidate.name.takeIf { it == "middle" }?.let { "route-to-$it" }
            },
        )

        assertEquals(listOf("near-blocked", "middle"), attempted)
        assertEquals("middle", selection?.target?.name)
        assertEquals("route-to-middle", selection?.route)
    }

    @Test
    fun `post-kill chaining returns no selection only after every nearby route fails`() {
        val attempted = mutableListOf<String>()

        val selection: SpearKillTargetChainSelection<String, String>? =
            selectNearestReachableSpearKillChainTarget(
                candidates = listOf("second", "first"),
                distanceSquared = { candidate -> if (candidate == "first") 1.0 else 4.0 },
                createRoute = { candidate -> attempted += candidate; null },
            )

        assertEquals(listOf("first", "second"), attempted)
        assertEquals(null, selection)
    }
}
