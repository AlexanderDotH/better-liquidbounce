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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillAttemptTrackerTest {

    @Test
    fun `normal lifecycle preserves route progress and terminal authorization`() {
        val tracker = SpearKillAttemptTracker()

        val started = tracker.begin(
            SpearKillAttemptPlan(
                targetIdentity = "c7d5c2e1-85fc-4f0e-b387-322b1a7a3b06",
                targetName = "Target",
                targetSource = "KillAura",
                plannedRouteMode = "PacketAStar",
                plannedOutboundStepCount = 3,
                predictedHitTick = 7,
                chargeTicks = 4,
                terminalAuthorizationRequired = true,
            ),
        )

        tracker.recordOutboundStep()
        tracker.recordOutboundStep()
        tracker.recordChargeTicks(6)
        tracker.authorizeTerminal(tick = 42)

        assertEquals("c7d5c2e1-85fc-4f0e-b387-322b1a7a3b06", started.targetIdentity)
        assertEquals("Target", started.targetName)
        assertEquals("KillAura", started.targetSource)
        assertEquals("PacketAStar", started.plannedRouteMode)
        assertEquals(3, started.plannedOutboundStepCount)
        assertEquals(0, started.outboundStepCount)
        assertTrue(started.terminalAuthorizationRequired)
        assertFalse(started.terminalAuthorized)

        val active = requireNotNull(tracker.current)
        assertEquals(2, active.outboundStepCount)
        assertEquals(7, active.predictedHitTick)
        assertEquals(6, active.chargeTicks)
        assertTrue(active.terminalAuthorized)
        assertEquals(42, active.terminalAuthorizationTick)
        assertEquals(SpearKillAttemptOutcome.UNCONFIRMED, tracker.complete()?.outcome)
    }

    @Test
    fun `damage evidence produces a confirmed damage outcome`() {
        val tracker = SpearKillAttemptTracker()
        tracker.begin(testAttemptPlan())

        tracker.markDamageEvidence()

        val completed = requireNotNull(tracker.complete())
        assertTrue(completed.damageEvidence)
        assertEquals(SpearKillAttemptOutcome.DAMAGE_CONFIRMED, completed.outcome)
    }

    @Test
    fun `completion without damage evidence remains unconfirmed and never miss`() {
        val tracker = SpearKillAttemptTracker()
        tracker.begin(testAttemptPlan())
        tracker.markSetback()
        tracker.markRecovery()

        val completed = requireNotNull(tracker.complete())
        assertTrue(completed.setback)
        assertTrue(completed.recovery)
        assertEquals(SpearKillAttemptOutcome.UNCONFIRMED, completed.outcome)
        assertFalse(SpearKillAttemptOutcome.values().any { it.name == "MISS" })
    }

    @Test
    fun `blocked and defeated signals remain visible in final outcomes`() {
        val tracker = SpearKillAttemptTracker()
        tracker.begin(testAttemptPlan())
        tracker.markBlocked()

        val blocked = requireNotNull(tracker.complete())
        assertTrue(blocked.blocked)
        assertEquals(SpearKillAttemptOutcome.BLOCKED, blocked.outcome)

        tracker.begin(testAttemptPlan())
        tracker.markDefeated()

        val defeated = requireNotNull(tracker.complete())
        assertTrue(defeated.defeated)
        assertEquals(SpearKillAttemptOutcome.DEFEATED, defeated.outcome)

        tracker.begin(testAttemptPlan())
        tracker.markTargetRemoved()

        val removed = requireNotNull(tracker.complete())
        assertTrue(removed.targetRemoved)
        assertEquals(SpearKillAttemptOutcome.ABORTED, removed.outcome)
    }

    @Test
    fun `abort retains its final snapshot and reset clears telemetry`() {
        val tracker = SpearKillAttemptTracker()
        tracker.begin(testAttemptPlan())

        val aborted = requireNotNull(tracker.abort("world-change"))
        assertEquals(SpearKillAttemptOutcome.ABORTED, aborted.outcome)
        assertEquals("world-change", aborted.abortReason)
        assertNull(tracker.current)
        assertEquals(aborted, tracker.lastCompleted)

        tracker.reset()

        assertNull(tracker.current)
        assertNull(tracker.lastCompleted)
    }

    private fun testAttemptPlan() = SpearKillAttemptPlan(
        targetIdentity = "target-id",
        targetName = "Target",
        targetSource = "SpearKill",
        plannedRouteMode = "Packet",
        plannedOutboundStepCount = 1,
        predictedHitTick = 2,
        chargeTicks = 0,
        terminalAuthorizationRequired = false,
    )
}
