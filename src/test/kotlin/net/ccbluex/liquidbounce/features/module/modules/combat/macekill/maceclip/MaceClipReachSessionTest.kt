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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceClipReachSessionTest {

    @Test
    fun `target loss before strike and timeout fail closed with distinct outcomes`() {
        val lostTarget = session(startedAtTick = 100)
        val timedOut = session(startedAtTick = 100)

        assertEquals(MaceClipReachSessionOutcome.TARGET_LOST, lostTarget.evaluate(101, targetAlive = false))
        assertEquals(MaceClipReachSessionOutcome.TIMED_OUT, timedOut.evaluate(140, targetAlive = true))
    }

    @Test
    fun `target death after strike commit does not interrupt the mandatory exact return`() {
        val session = session(startedAtTick = 100)

        assertTrue(session.commitStrike(101, targetAlive = true))
        assertFalse(session.commitStrike(101, targetAlive = true))

        assertEquals(MaceClipReachSessionOutcome.ACTIVE, session.evaluate(102, targetAlive = false))
        assertEquals(MaceClipReachSessionOutcome.COMPLETED, session.complete())
    }

    @Test
    fun `server correction terminates the clip session and remains sticky`() {
        val session = session()

        assertEquals(MaceClipReachSessionOutcome.CORRECTED, session.recordCorrection())

        assertEquals(MaceClipReachSessionOutcome.CORRECTED, session.evaluate(500, targetAlive = false))
        assertEquals(MaceClipReachSessionOutcome.CORRECTED, session.complete())
    }

    @Test
    fun `failed terminal route installation records a sticky replan rejection`() {
        val session = session()

        assertEquals(MaceClipReachSessionOutcome.REPLAN_REJECTED, session.recordReplanRejected())

        assertEquals(MaceClipReachSessionOutcome.REPLAN_REJECTED, session.evaluate(500, targetAlive = false))
        assertFalse(session.commitStrike(500, targetAlive = true))
    }

    @Test
    fun `terminal endpoint can be replanned before strike while retaining exact inverse`() {
        val session = session()
        val newEndpoint = Vec3(45.0, 70.0, 8.0)

        val applied = assertInstanceOf(
            MaceClipReachReplanResult.Applied::class.java,
            session.replanTerminal(newEndpoint, ACCEPT_ALL_ANCHORS),
        )

        assertEquals(newEndpoint, applied.plan.endpoint)
        assertEquals(session.plan.origin, session.plan.returnMovements.fold(session.plan.endpoint, Vec3::add))
        assertEquals(MaceClipReachSessionOutcome.ACTIVE, session.outcome)
    }

    @Test
    fun `terminal replan preserves every confirmed outbound movement and its inverse`() {
        val session = session()
        val originalPlan = session.plan
        assertTrue(session.recordOutboundMovementConfirmed())
        assertTrue(session.recordOutboundMovementConfirmed())

        val applied = assertInstanceOf(
            MaceClipReachReplanResult.Applied::class.java,
            session.replanTerminal(Vec3(45.0, 70.0, 8.0), ACCEPT_ALL_ANCHORS),
        )

        assertEquals(originalPlan.outboundMovements.take(2), applied.plan.outboundMovements.take(2))
        assertEquals(2, session.confirmedOutboundMovementCount)
        assertEquals(applied.plan.origin, applied.plan.returnMovements.fold(applied.plan.endpoint, Vec3::add))
        assertEquals(
            applied.plan.endpoint,
            applied.plan.outboundMovements.fold(applied.plan.origin, Vec3::add),
        )
    }

    @Test
    fun `vertical target replan keeps reference packet count and preserves confirmed prefix`() {
        val session = session(endpoint = Vec3(30.0, 60.0, 0.0))
        val originalPlan = session.plan
        repeat(2) { assertTrue(session.recordOutboundMovementConfirmed()) }

        val applied = assertInstanceOf(
            MaceClipReachReplanResult.Applied::class.java,
            session.replanTerminal(Vec3(30.0, 64.0, 0.0), ACCEPT_ALL_ANCHORS),
        )

        assertEquals(originalPlan.outboundMovements.size, applied.plan.outboundMovements.size)
        assertEquals(originalPlan.outboundMovements.take(2), applied.plan.outboundMovements.take(2))
        assertEquals(applied.plan.origin, applied.plan.returnMovements.fold(applied.plan.endpoint, Vec3::add))
    }

    @Test
    fun `out of sphere replan rejects without changing a confirmed prefix`() {
        val session = session(endpoint = Vec3(30.0, 60.0, 0.0))
        val originalPlan = session.plan
        repeat(2) { assertTrue(session.recordOutboundMovementConfirmed()) }

        val rejected = assertInstanceOf(
            MaceClipReachReplanResult.Rejected::class.java,
            session.replanTerminal(Vec3(120.0, 64.0, 0.0), ACCEPT_ALL_ANCHORS),
        )

        assertEquals(MaceClipReachReplanBlockReason.PLAN_BLOCKED, rejected.reason)
        assertEquals(MaceClipReachBlockReason.DISTANCE_EXCEEDED, rejected.planBlockReason)
        assertEquals(originalPlan, session.plan)
        assertEquals(MaceClipReachSessionOutcome.REPLAN_REJECTED, session.outcome)
    }

    @Test
    fun `confirmed endpoint rejects terminal replan and prevents a stale-target strike`() {
        val session = session()
        repeat(session.plan.outboundMovements.size) {
            assertTrue(session.recordOutboundMovementConfirmed())
        }

        val rejected = assertInstanceOf(
            MaceClipReachReplanResult.Rejected::class.java,
            session.replanTerminal(Vec3(45.0, 64.0, 0.0), ACCEPT_ALL_ANCHORS),
        )

        assertEquals(MaceClipReachReplanBlockReason.TERMINAL_CONFIRMED, rejected.reason)
        assertEquals(MaceClipReachSessionOutcome.REPLAN_REJECTED, session.outcome)
        assertFalse(session.commitStrike(2, targetAlive = true))
    }

    @Test
    fun `strike commit makes the exact return sticky against target death and late replans`() {
        val session = session()
        val originalPlan = session.plan
        assertTrue(session.commitStrike(1, targetAlive = true))

        val rejected = assertInstanceOf(
            MaceClipReachReplanResult.Rejected::class.java,
            session.replanTerminal(Vec3(45.0, 64.0, 0.0), ACCEPT_ALL_ANCHORS),
        )

        assertEquals(MaceClipReachReplanBlockReason.STRIKE_COMMITTED, rejected.reason)
        assertEquals(MaceClipReachSessionOutcome.ACTIVE, session.outcome)
        assertEquals(originalPlan, session.plan)
        assertTrue(session.strikeCommitted)
        assertEquals(MaceClipReachSessionOutcome.ACTIVE, session.evaluate(2, targetAlive = false))
        assertEquals(MaceClipReachSessionOutcome.COMPLETED, session.complete())
    }

    @Test
    fun `invalid terminal replan exposes the planner reason and preserves the original route`() {
        val session = session()
        val originalPlan = session.plan

        val rejected = assertInstanceOf(
            MaceClipReachReplanResult.Rejected::class.java,
            session.replanTerminal(Vec3(600.0, 64.0, 0.0), ACCEPT_ALL_ANCHORS),
        )

        assertEquals(MaceClipReachReplanBlockReason.PLAN_BLOCKED, rejected.reason)
        assertEquals(MaceClipReachBlockReason.DISTANCE_EXCEEDED, rejected.planBlockReason)
        assertEquals(MaceClipReachSessionOutcome.REPLAN_REJECTED, session.outcome)
        assertEquals(originalPlan, session.plan)
        assertFalse(session.commitStrike(2, targetAlive = true))
    }

    private fun session(
        startedAtTick: Long = 0,
        endpoint: Vec3 = Vec3(30.0, 64.0, 0.0),
    ): MaceClipReachSession = MaceClipReachSession(
        initialPlan = readyPlan(endpoint),
        startedAtTick = startedAtTick,
    )

    private fun readyPlan(endpoint: Vec3): MaceClipReachPlan {
        val result = MaceClipReachPlanner.plan(
            MaceClipReachPlanRequest(
                origin = Vec3(0.0, 64.0, 0.0),
                endpoint = endpoint,
                dimensionBounds = MaceClipReachDimensionBounds(-64.0, 320.0),
                profile = MaceClipReachProfileTest.validatedProfile(),
                use = MaceClipReachUse.NORMAL,
                anchorValidator = ACCEPT_ALL_ANCHORS,
            ),
        )
        return assertInstanceOf(MaceClipReachPlanResult.Ready::class.java, result).plan
    }

    private companion object {
        val ACCEPT_ALL_ANCHORS = MaceClipReachAnchorValidator { _, _ -> true }
    }
}
