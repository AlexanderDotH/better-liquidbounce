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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketSessionPortAdapter
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillReturnRecoveryTest {

    @Test
    fun `packet recovery visits original and moved origins three times before physical reset`() {
        val originalOrigin = Vec3(10.0, 64.0, 2.0)
        val movedOrigin = Vec3(16.0, 64.0, 5.0)
        val authoritativePosition = Vec3(30.0, 68.0, -4.0)
        val recovery = SpearKillReturnRecoveryTracker(maxPacketAttempts = 3)
        recovery.begin(originalOrigin)
        recovery.observeCombatPosition(movedOrigin)

        repeat(3) { index ->
            val attempt = recovery.nextAction(authoritativePosition)
                as SpearKillReturnRecoveryAction.PacketAttempt

            assertEquals(index + 1, attempt.number)
            assertEquals(listOf(originalOrigin, movedOrigin), attempt.checkpoints)
            assertVec3Equals(movedOrigin, attempt.destination, 1e-9)
            assertVec3Equals(
                authoritativePosition.subtract(movedOrigin),
                attempt.authoritativeOffset,
                1e-9,
            )
        }

        val reset = recovery.nextAction(authoritativePosition)
            as SpearKillReturnRecoveryAction.PhysicalReset
        assertVec3Equals(movedOrigin, reset.position, 1e-9)
        assertEquals(3, recovery.packetAttempts)
    }

    @Test
    fun `first recovery attempt freezes the combat origin used by later retries`() {
        val originalOrigin = Vec3(10.0, 64.0, 2.0)
        val combatOrigin = Vec3(14.0, 64.0, 2.0)
        val laterMovement = Vec3(18.0, 64.0, 2.0)
        val recovery = SpearKillReturnRecoveryTracker()
        recovery.begin(originalOrigin)
        recovery.observeCombatPosition(combatOrigin)

        recovery.nextAction(Vec3(24.0, 64.0, 2.0))
        recovery.observeCombatPosition(laterMovement)
        val retry = recovery.nextAction(Vec3(20.0, 64.0, 2.0))
            as SpearKillReturnRecoveryAction.PacketAttempt

        assertEquals(listOf(originalOrigin, combatOrigin), retry.checkpoints)
        assertVec3Equals(combatOrigin, retry.destination, 1e-9)
    }

    @Test
    fun `arrival confirmations are emitted once and in route order`() {
        val originalOrigin = Vec3(10.0, 64.0, 2.0)
        val movedOrigin = Vec3(15.0, 64.0, 4.0)
        val recovery = SpearKillReturnRecoveryTracker()
        recovery.begin(originalOrigin)
        recovery.observeCombatPosition(movedOrigin)
        recovery.nextAction(Vec3(25.0, 64.0, 2.0))

        assertNull(recovery.consumeArrivalConfirmation(movedOrigin))
        assertEquals(originalOrigin, recovery.consumeArrivalConfirmation(originalOrigin))
        assertNull(recovery.consumeArrivalConfirmation(originalOrigin))
        assertEquals(movedOrigin, recovery.consumeArrivalConfirmation(movedOrigin))
        assertNull(recovery.consumeArrivalConfirmation(movedOrigin))
    }

    @Test
    fun `unchanged combat origin produces one recovery checkpoint`() {
        val origin = Vec3(10.0, 64.0, 2.0)
        val recovery = SpearKillReturnRecoveryTracker()
        recovery.begin(origin)
        recovery.observeCombatPosition(origin.add(1.0E-5, 0.0, 0.0))

        val attempt = recovery.nextAction(Vec3(20.0, 64.0, 2.0))
            as SpearKillReturnRecoveryAction.PacketAttempt

        assertEquals(listOf(origin), attempt.checkpoints)
        assertVec3Equals(origin, attempt.destination, 1e-9)
    }

    @Test
    fun `recovery movement builder composes exact legs through every checkpoint`() {
        val authoritativePosition = Vec3(24.0, 68.0, -3.0)
        val originalOrigin = Vec3(10.0, 64.0, 2.0)
        val movedOrigin = Vec3(16.0, 64.0, 5.0)

        val movements = buildSpearKillReturnRecoveryMovements(
            authoritativePosition = authoritativePosition,
            checkpoints = listOf(originalOrigin, movedOrigin),
            planLeg = { from, to -> listOf(to.subtract(from)) },
        )!!

        assertEquals(
            listOf(
                originalOrigin.subtract(authoritativePosition),
                movedOrigin.subtract(originalOrigin),
            ),
            movements,
        )
        assertVec3Equals(movedOrigin, movements.fold(authoritativePosition, Vec3::add), 1e-9)
    }

    @Test
    fun `recovery movement builder rejects a leg that misses its checkpoint`() {
        val movements = buildSpearKillReturnRecoveryMovements(
            authoritativePosition = Vec3.ZERO,
            checkpoints = listOf(Vec3(4.0, 0.0, 0.0)),
            planLeg = { _, _ -> listOf(Vec3(3.0, 0.0, 0.0)) },
        )

        assertNull(movements)
    }

    @Test
    fun `packet first exact recovery never exposes a physical position reset`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.beginPacketExactRecoveryFrom(
            authoritativeOffset = Vec3(-6.0, 0.0, 0.0),
            recoveryMovements = listOf(Vec3(2.0, 0.0, 0.0), Vec3(4.0, 0.0, 0.0)),
        )

        assertTrue(session.recovering)
        assertFalse(session.physicalReturnConfigured)
        assertNull(session.consumePhysicalPositionOffset())
        while (session.active) {
            session.prepareNextStep()?.let { session.confirmStep(delivered = true) }
            assertNull(session.consumePhysicalPositionOffset())
        }

        assertFalse(session.recovering)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `each delivered return step restarts the stall window`() {
        assertEquals(40, nextSpearKillRecoveryStallTicks(currentTicks = 39, madeProgress = false))
        assertEquals(0, nextSpearKillRecoveryStallTicks(currentTicks = 40, madeProgress = true))
        assertEquals(1, nextSpearKillRecoveryStallTicks(currentTicks = 0, madeProgress = false))
    }

    @Test
    fun `combat origin tracking stops only after physical return claims the player`() {
        val origin = Vec3(10.0, 64.0, 2.0)
        val positioner = SpearKillPhysicalReturnPositioner()

        assertFalse(positioner.followingReturn)
        assertNull(positioner.resolve(origin, origin, Vec3(4.0, 0.0, 0.0)))
        assertFalse(positioner.followingReturn)
        positioner.resolve(origin, origin.add(2.0, 0.0, 0.0), Vec3(2.0, 0.0, 0.0))
        assertTrue(positioner.followingReturn)
        positioner.clear()
        assertFalse(positioner.followingReturn)
    }
}
