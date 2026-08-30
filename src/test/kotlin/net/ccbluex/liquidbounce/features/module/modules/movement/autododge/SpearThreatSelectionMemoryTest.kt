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

class SpearThreatSelectionMemoryTest {
    private val stationaryTarget = SpearThreatTargetSnapshot(
        boundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
        velocity = Vec3.ZERO,
    )

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
