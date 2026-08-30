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

class SpearKillKillAuraCannotAcquireRangeAvailabilityGateTest {

    @Test
    fun `KillAura cannot acquire SpearKill range without every availability gate`() {
        listOf(
            isSpearKillKillAuraAcquisitionAvailable(false, true, true, true, false),
            isSpearKillKillAuraAcquisitionAvailable(true, false, true, true, false),
            isSpearKillKillAuraAcquisitionAvailable(true, true, false, true, false),
            isSpearKillKillAuraAcquisitionAvailable(true, true, true, false, false),
            isSpearKillKillAuraAcquisitionAvailable(true, true, true, true, true),
        ).forEach { available -> assertFalse(available) }
    }

    @Test
    fun `Combat is automatic while Crosshair is manual`() {
        assertFalse(isSpearKillTargetSourceAutomatic(SpearKillTargetSource.Crosshair))
        assertTrue(isSpearKillTargetSourceAutomatic(SpearKillTargetSource.Combat))
        assertEquals(setOf("Crosshair", "Combat"), SpearKillTargetSource.entries.map { it.tag }.toSet())
    }

    @Test
    fun `candidate eligibility fails closed for every target safety gate`() {
        assertTrue(
            isSpearKillTargetCandidateEligible(
                isCombatSafe = true,
                isAlive = true,
                isInCurrentWorld = true,
                isWithinRange = true,
                isRejected = false,
            ),
        )

        listOf(
            isSpearKillTargetCandidateEligible(
                isCombatSafe = false,
                isAlive = true,
                isInCurrentWorld = true,
                isWithinRange = true,
                isRejected = false,
            ),
            isSpearKillTargetCandidateEligible(
                isCombatSafe = true,
                isAlive = false,
                isInCurrentWorld = true,
                isWithinRange = true,
                isRejected = false,
            ),
            isSpearKillTargetCandidateEligible(
                isCombatSafe = true,
                isAlive = true,
                isInCurrentWorld = false,
                isWithinRange = true,
                isRejected = false,
            ),
            isSpearKillTargetCandidateEligible(
                isCombatSafe = true,
                isAlive = true,
                isInCurrentWorld = true,
                isWithinRange = false,
                isRejected = false,
            ),
            isSpearKillTargetCandidateEligible(
                isCombatSafe = true,
                isAlive = true,
                isInCurrentWorld = true,
                isWithinRange = true,
                isRejected = true,
            ),
        ).forEach { eligible -> assertFalse(eligible) }
    }

    @Test
    fun `automatic sources reject self friend teammate and AntiBot candidates through combat safety`() {
        listOf("self", "friend", "teammate", "antibot").forEach { rejectedKind ->
            assertFalse(
                isSpearKillTargetCandidateEligible(
                    isCombatSafe = false,
                    isAlive = true,
                    isInCurrentWorld = true,
                    isWithinRange = true,
                    isRejected = false,
                ),
                rejectedKind,
            )
        }
    }

    @Test
    fun `configured source remains authoritative and an active route keeps its lock`() {
        assertEquals(
            "combat",
            selectSpearKillTargetForSource(
                targetSource = SpearKillTargetSource.Combat,
                lookRayTarget = { "look" },
                combatTarget = { "combat" },
            ),
        )
        assertEquals("locked", preferLockedSpearKillTarget("locked", "retargeted"))
        assertEquals("selected", preferLockedSpearKillTarget(null, "selected"))
    }

    @Test
    fun `a transient route retry keeps the selected target locked`() {
        assertEquals(
            "locked",
            activeSpearKillTargetLock(
                lockedTarget = "locked",
                routeActive = false,
                routePreparationActive = true,
            ),
        )
        assertEquals(
            null,
            activeSpearKillTargetLock(
                lockedTarget = "locked",
                routeActive = false,
                routePreparationActive = false,
            ),
        )
    }

    @Test
    fun `locked target remains valid inside attack reach and at the outer hysteresis edge`() {
        assertTrue(isSpearKillLockedTargetEligible(
            isCombatSafe = true,
            isAlive = true,
            isInCurrentWorld = true,
            distance = 2.25,
            maximumDistance = 50.0,
            hysteresis = 0.75,
            isRejected = false,
        ))
        assertTrue(isSpearKillLockedTargetEligible(
            isCombatSafe = true,
            isAlive = true,
            isInCurrentWorld = true,
            distance = 50.75,
            maximumDistance = 50.0,
            hysteresis = 0.75,
            isRejected = false,
        ))
        assertFalse(isSpearKillLockedTargetEligible(
            isCombatSafe = true,
            isAlive = true,
            isInCurrentWorld = true,
            distance = 50.751,
            maximumDistance = 50.0,
            hysteresis = 0.75,
            isRejected = false,
        ))
    }
}
