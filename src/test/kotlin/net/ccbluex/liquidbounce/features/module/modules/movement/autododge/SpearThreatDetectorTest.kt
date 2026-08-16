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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpearThreatDetectorTest {

    private val stationaryTarget = SpearThreatTargetSnapshot(
        boundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
        velocity = Vec3.ZERO,
    )

    @Test
    fun `active spear use beyond packet threat range does not trigger global evasion`() {
        val detector = SpearThreatDetector()
        val farAwayUser = candidate(
            entityId = 1,
            position = Vec3(10_000.0, 0.0, 0.0),
            lookDirection = Vec3(1.0, 0.0, 0.0),
            isUsingSpear = true,
        )

        val threat = detector.update(stationaryTarget, listOf(farAwayUser), aimMargin = 0.0, threatMemoryTicks = 0)

        assertNull(threat)
    }

    @Test
    fun `active spear use inside packet threat range evades without trusting remote aim`() {
        val packetCapableUser = candidate(
            entityId = 1,
            position = Vec3(0.0, 320.0, 0.0),
            lookDirection = Vec3.ZERO,
            isUsingSpear = true,
            spearUseTicks = 1,
        )

        val threat = SpearThreatDetector().update(
            stationaryTarget,
            listOf(packetCapableUser),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )

        assertEquals(SpearThreatKind.USING_PACKET_CAPABLE, threat?.kind)
        assertEquals(SpearThreatResponse.EVADE, threat?.response)
        assertFalse(threat?.trustsAttackerLook ?: true)
        assertTrue(threat.requiresJuke)
        assertTrue(threat.requiresTeleport)
    }

    @Test
    fun `active spear use evades for the entire valid charge and stops after damage window`() {
        fun detect(useTicks: Int) = SpearThreatDetector().update(
            stationaryTarget,
            listOf(
                candidate(
                    entityId = 1,
                    isUsingSpear = true,
                    spearUseTicks = useTicks,
                    spearDelayTicks = 10,
                    spearDamageUseDurationTicks = 30,
                )
            ),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )

        val started = detect(useTicks = 0)
        val charging = detect(useTicks = 9)
        val arming = detect(useTicks = 10)
        val ready = detect(useTicks = 11)
        val expired = detect(useTicks = 30)

        assertEquals(SpearThreatResponse.EVADE, started?.response)
        assertEquals(SpearThreatResponse.EVADE, charging?.response)
        assertEquals(SpearThreatResponse.EVADE, arming?.response)
        assertEquals(SpearThreatResponse.EVADE, ready?.response)
        assertEquals(SpearThreatResponse.MONITOR, expired?.response)
        assertTrue(started.requiresTeleport)
        assertTrue(charging.requiresJuke)
        assertTrue(arming.requiresTeleport)
        assertTrue(ready.requiresTeleport)
        assertFalse(expired.requiresJuke)
    }

    @Test
    fun `spear position jump is treated as an emergency commit without trusting remote aim`() {
        val jumped = candidate(
            entityId = 1,
            lookDirection = Vec3.ZERO,
            isUsingSpear = true,
            spearUseTicks = 5,
            spearDelayTicks = 10,
            hasSignificantPositionJump = true,
        )

        val threat = SpearThreatDetector().update(
            stationaryTarget,
            listOf(jumped),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )

        assertEquals(SpearThreatKind.ATTACK_COMMITTED, threat?.kind)
        assertEquals(SpearThreatResponse.EMERGENCY, threat?.response)
        assertFalse(threat?.trustsAttackerLook ?: true)
        assertTrue(threat.requiresTeleport)
    }

    @Test
    fun `held spear triggers only when its look ray intersects the target`() {
        val aimed = candidate(entityId = 1, isHoldingSpear = true)
        val lookingAway = candidate(
            entityId = 2,
            lookDirection = Vec3(-1.0, 0.0, 0.0),
            isHoldingSpear = true,
        )

        val aimedThreat = SpearThreatDetector().update(
            stationaryTarget,
            listOf(aimed),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )
        val lookingAwayThreat = SpearThreatDetector().update(
            stationaryTarget,
            listOf(lookingAway),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )

        assertEquals(SpearThreatKind.HOLDING_AIMED, aimedThreat?.kind)
        assertEquals(SpearThreatResponse.EVADE, aimedThreat?.response)
        assertTrue(aimedThreat.requiresTeleport)
        assertNull(lookingAwayThreat)
    }

    @Test
    fun `visibility grace reacts before a freshly loaded spear holder synchronizes aim or use`() {
        val freshlyVisible = candidate(
            entityId = 1,
            lookDirection = Vec3(-1.0, 0.0, 0.0),
            isHoldingSpear = true,
            visibilityAgeTicks = 3,
        )

        val threat = SpearThreatDetector().update(
            stationaryTarget,
            listOf(freshlyVisible),
            aimMargin = 0.0,
            visibilityGraceTicks = 8,
            threatMemoryTicks = 0,
        )

        assertEquals(SpearThreatKind.HOLDING_NEWLY_VISIBLE, threat?.kind)
        assertEquals(SpearThreatResponse.EVADE, threat?.response)
        assertTrue(threat.requiresTeleport)
    }

    @Test
    fun `visibility grace expires at its configured tick and zero disables it`() {
        val atBoundary = candidate(
            entityId = 1,
            lookDirection = Vec3(-1.0, 0.0, 0.0),
            isHoldingSpear = true,
            visibilityAgeTicks = 8,
        )
        val firstVisibleTick = atBoundary.copy(visibilityAgeTicks = 0)

        val expired = SpearThreatDetector().update(
            stationaryTarget,
            listOf(atBoundary),
            aimMargin = 0.0,
            visibilityGraceTicks = 8,
            threatMemoryTicks = 0,
        )
        val disabled = SpearThreatDetector().update(
            stationaryTarget,
            listOf(firstVisibleTick),
            aimMargin = 0.0,
            visibilityGraceTicks = 0,
            threatMemoryTicks = 0,
        )

        assertNull(expired)
        assertNull(disabled)
    }

    @Test
    fun `aimed spear telegraph outranks a nearer visibility grace threat`() {
        val newlyVisible = candidate(
            entityId = 1,
            position = Vec3(-1.0, 0.0, 0.0),
            lookDirection = Vec3(-1.0, 0.0, 0.0),
            isHoldingSpear = true,
            visibilityAgeTicks = 0,
        )
        val aimed = candidate(
            entityId = 2,
            position = Vec3(-20.0, 0.0, 0.0),
            isHoldingSpear = true,
        )

        val threat = SpearThreatDetector().update(
            stationaryTarget,
            listOf(newlyVisible, aimed),
            aimMargin = 0.0,
            visibilityGraceTicks = 8,
            threatMemoryTicks = 0,
        )

        assertEquals(2, threat?.candidate?.entityId)
        assertEquals(SpearThreatKind.HOLDING_AIMED, threat?.kind)
    }

    @Test
    fun `aim includes the target swept over the next two ticks`() {
        val movingTarget = stationaryTarget.copy(velocity = Vec3(0.0, 0.0, 1.0))
        val aimedAtFuturePosition = candidate(
            entityId = 1,
            position = Vec3(-5.0, 0.0, 2.0),
            isHoldingSpear = true,
        )

        val threat = SpearThreatDetector().update(
            movingTarget,
            listOf(aimedAtFuturePosition),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )

        assertEquals(1, threat?.candidate?.entityId)
    }

    @Test
    fun `aim margin expands the swept target box`() {
        val nearMiss = candidate(
            entityId = 1,
            position = Vec3(-5.0, 0.0, 1.0),
            isHoldingSpear = true,
        )

        val withoutMargin = SpearThreatDetector().update(
            stationaryTarget,
            listOf(nearMiss),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )
        val withMargin = SpearThreatDetector().update(
            stationaryTarget,
            listOf(nearMiss),
            aimMargin = 0.75,
            threatMemoryTicks = 0,
        )

        assertNull(withoutMargin)
        assertNotNull(withMargin)
    }

    @Test
    fun `self dead removed and bot candidates are excluded`() {
        val candidates = listOf(
            candidate(entityId = 1, position = Vec3(-1.0, 0.0, 0.0), isUsingSpear = true, isSelf = true),
            candidate(entityId = 2, position = Vec3(-2.0, 0.0, 0.0), isUsingSpear = true, isAlive = false),
            candidate(entityId = 3, position = Vec3(-3.0, 0.0, 0.0), isUsingSpear = true, isRemoved = true),
            candidate(entityId = 4, position = Vec3(-4.0, 0.0, 0.0), isUsingSpear = true, isBot = true),
            candidate(entityId = 5, position = Vec3(-20.0, 0.0, 0.0), isUsingSpear = true),
        )

        val threat = SpearThreatDetector().update(
            stationaryTarget,
            candidates,
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )

        assertEquals(5, threat?.candidate?.entityId)
    }

    @Test
    fun `friend and teammate candidates remain eligible`() {
        val friendAndTeammate = candidate(
            entityId = 1,
            isUsingSpear = true,
            isFriend = true,
            isTeammate = true,
        )

        val threat = SpearThreatDetector().update(
            stationaryTarget,
            listOf(friendAndTeammate),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )

        assertEquals(1, threat?.candidate?.entityId)
    }

    @Test
    fun `ready aimed spear outranks charging and held aimed threats`() {
        val heldAndAimed = candidate(entityId = 1, position = Vec3(-1.0, 0.0, 0.0), isHoldingSpear = true)
        val charging = candidate(
            entityId = 2,
            position = Vec3(-2.0, 0.0, 0.0),
            lookDirection = Vec3(-1.0, 0.0, 0.0),
            isUsingSpear = true,
            spearUseTicks = 5,
            spearDelayTicks = 10,
        )
        val readyAndAimed = candidate(entityId = 3, position = Vec3(-20.0, 0.0, 0.0), isUsingSpear = true)

        val threat = SpearThreatDetector().update(
            stationaryTarget,
            listOf(heldAndAimed, charging, readyAndAimed),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )

        assertEquals(3, threat?.candidate?.entityId)
        assertEquals(SpearThreatKind.USING_AIMED, threat?.kind)
        assertEquals(SpearThreatResponse.EVADE, threat?.response)
    }

    @Test
    fun `ready aimed use outranks a nearer held and aimed candidate`() {
        val heldAndAimed = candidate(entityId = 1, position = Vec3(-1.0, 0.0, 0.0), isHoldingSpear = true)
        val using = candidate(
            entityId = 2,
            position = Vec3(-20.0, 0.0, 0.0),
            isUsingSpear = true,
        )

        val threat = SpearThreatDetector().update(
            stationaryTarget,
            listOf(heldAndAimed, using),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )

        assertEquals(2, threat?.candidate?.entityId)
        assertEquals(SpearThreatKind.USING_AIMED, threat?.kind)
    }

    @Test
    fun `nearest candidate wins within the same threat kind`() {
        val farther = candidate(entityId = 1, position = Vec3(-10.0, 0.0, 0.0), isHoldingSpear = true)
        val nearer = candidate(entityId = 2, position = Vec3(-3.0, 0.0, 0.0), isHoldingSpear = true)

        val threat = SpearThreatDetector().update(
            stationaryTarget,
            listOf(farther, nearer),
            aimMargin = 0.0,
            threatMemoryTicks = 0,
        )

        assertEquals(2, threat?.candidate?.entityId)
    }

    @Test
    fun `selected threat remains for the configured number of absent ticks`() {
        val detector = SpearThreatDetector()
        val selected = candidate(entityId = 1, isUsingSpear = true)

        detector.update(stationaryTarget, listOf(selected), aimMargin = 0.0, threatMemoryTicks = 2)
        val firstAbsentTick = detector.update(stationaryTarget, emptyList(), aimMargin = 0.0, threatMemoryTicks = 2)
        val secondAbsentTick = detector.update(stationaryTarget, emptyList(), aimMargin = 0.0, threatMemoryTicks = 2)
        val expired = detector.update(stationaryTarget, emptyList(), aimMargin = 0.0, threatMemoryTicks = 2)

        assertEquals(1, firstAbsentTick?.candidate?.entityId)
        assertEquals(1, secondAbsentTick?.candidate?.entityId)
        assertNull(expired)
    }

    @Test
    fun `only a higher ranked threat replaces a remembered selection immediately`() {
        val detector = SpearThreatDetector()
        val selected = candidate(
            entityId = 1,
            position = Vec3(-10.0, 0.0, 0.0),
            lookDirection = Vec3(-1.0, 0.0, 0.0),
            isUsingSpear = true,
            spearUseTicks = 5,
            spearDelayTicks = 10,
        )
        val lowerRank = candidate(entityId = 2, position = Vec3(-1.0, 0.0, 0.0), isHoldingSpear = true)
        val higherRank = candidate(entityId = 3, position = Vec3(-20.0, 0.0, 0.0), isUsingSpear = true)

        detector.update(stationaryTarget, listOf(selected), aimMargin = 0.0, threatMemoryTicks = 5)
        val stillSelected = detector.update(
            stationaryTarget,
            listOf(lowerRank),
            aimMargin = 0.0,
            threatMemoryTicks = 5,
        )
        val replaced = detector.update(
            stationaryTarget,
            listOf(higherRank),
            aimMargin = 0.0,
            threatMemoryTicks = 5,
        )

        assertEquals(1, stillSelected?.candidate?.entityId)
        assertEquals(3, replaced?.candidate?.entityId)
    }

    @Test
    fun `nearer threat replaces a remembered selection within the same kind`() {
        val detector = SpearThreatDetector()
        val farther = candidate(entityId = 1, position = Vec3(-10.0, 0.0, 0.0), isHoldingSpear = true)
        val nearer = candidate(entityId = 2, position = Vec3(-2.0, 0.0, 0.0), isHoldingSpear = true)

        detector.update(stationaryTarget, listOf(farther), aimMargin = 0.0, threatMemoryTicks = 5)
        val replaced = detector.update(
            stationaryTarget,
            listOf(nearer),
            aimMargin = 0.0,
            threatMemoryTicks = 5,
        )

        assertEquals(2, replaced?.candidate?.entityId)
    }

    @Test
    fun `equally ranked and equally distant candidate does not churn the remembered selection`() {
        val detector = SpearThreatDetector()
        val selected = candidate(entityId = 10, position = Vec3(-5.0, 0.0, 0.0), isHoldingSpear = true)
        val tied = candidate(
            entityId = 1,
            position = Vec3(5.0, 0.0, 0.0),
            lookDirection = Vec3(-1.0, 0.0, 0.0),
            isHoldingSpear = true,
        )

        detector.update(stationaryTarget, listOf(selected), aimMargin = 0.0, threatMemoryTicks = 5)
        val retained = detector.update(
            stationaryTarget,
            listOf(tied),
            aimMargin = 0.0,
            threatMemoryTicks = 5,
        )

        assertEquals(10, retained?.candidate?.entityId)
    }

    @Test
    fun `position jump refreshes only an already selected threat`() {
        val detector = SpearThreatDetector()
        val selected = candidate(entityId = 1, isUsingSpear = true)
        val jumpedWithoutTelegraph = candidate(
            entityId = 1,
            position = Vec3(20.0, 0.0, 0.0),
            hasSignificantPositionJump = true,
        )

        val unrelatedJump = SpearThreatDetector().update(
            stationaryTarget,
            listOf(jumpedWithoutTelegraph),
            aimMargin = 0.0,
            threatMemoryTicks = 1,
        )
        detector.update(stationaryTarget, listOf(selected), aimMargin = 0.0, threatMemoryTicks = 1)
        val refreshed = detector.update(
            stationaryTarget,
            listOf(jumpedWithoutTelegraph),
            aimMargin = 0.0,
            threatMemoryTicks = 1,
        )
        val rememberedAfterRefresh = detector.update(
            stationaryTarget,
            emptyList(),
            aimMargin = 0.0,
            threatMemoryTicks = 1,
        )
        val expired = detector.update(stationaryTarget, emptyList(), aimMargin = 0.0, threatMemoryTicks = 1)

        assertNull(unrelatedJump)
        assertEquals(20.0, refreshed?.candidate?.position?.x)
        assertEquals(1, rememberedAfterRefresh?.candidate?.entityId)
        assertNull(expired)
    }

    @Test
    fun `reset discards a remembered threat`() {
        val detector = SpearThreatDetector()
        detector.update(
            stationaryTarget,
            listOf(candidate(entityId = 1, isUsingSpear = true)),
            aimMargin = 0.0,
            threatMemoryTicks = 5,
        )

        detector.reset()
        val threat = detector.update(stationaryTarget, emptyList(), aimMargin = 0.0, threatMemoryTicks = 5)

        assertNull(threat)
    }

    private fun candidate(
        entityId: Int,
        position: Vec3 = Vec3(-5.0, 0.0, 0.0),
        lookDirection: Vec3 = Vec3(1.0, 0.0, 0.0),
        isHoldingSpear: Boolean = false,
        isUsingSpear: Boolean = false,
        spearUseTicks: Int = if (isUsingSpear) 11 else 0,
        spearDelayTicks: Int? = if (isUsingSpear) 10 else null,
        spearDamageUseDurationTicks: Int? = if (isUsingSpear) 30 else null,
        isAlive: Boolean = true,
        isRemoved: Boolean = false,
        isBot: Boolean = false,
        isSelf: Boolean = false,
        isFriend: Boolean = false,
        isTeammate: Boolean = false,
        hasSignificantPositionJump: Boolean = false,
        visibilityAgeTicks: Int = Int.MAX_VALUE,
    ) = SpearThreatCandidate(
        entityId = entityId,
        name = "player-$entityId",
        position = position,
        eyePosition = position.add(0.0, 1.62, 0.0),
        lookDirection = lookDirection,
        isHoldingSpear = isHoldingSpear,
        isUsingSpear = isUsingSpear,
        spearUseTicks = spearUseTicks,
        spearDelayTicks = spearDelayTicks,
        spearDamageUseDurationTicks = spearDamageUseDurationTicks,
        isAlive = isAlive,
        isRemoved = isRemoved,
        isBot = isBot,
        isSelf = isSelf,
        isFriend = isFriend,
        isTeammate = isTeammate,
        hasSignificantPositionJump = hasSignificantPositionJump,
        visibilityAgeTicks = visibilityAgeTicks,
    )
}
