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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillTargetConfigurationTest {

    @Test
    fun `defaults require a manual attack request and use crosshair targeting`() {
        assertEquals(SpearKillActivationMode.Manual, DEFAULT_SPEAR_KILL_ACTIVATION_MODE)
        assertEquals(SpearKillTargetSource.Crosshair, DEFAULT_SPEAR_KILL_TARGET_SOURCE)
        assertFalse(
            isSpearKillActivationSatisfied(
                activationMode = DEFAULT_SPEAR_KILL_ACTIVATION_MODE,
                attackRequested = false,
                useKeyDown = true,
            ),
        )
    }

    @Test
    fun `Manual activation requires an attack request while HoldUse does not`() {
        assertTrue(requiresSpearKillAttackRequest(SpearKillActivationMode.Manual))
        assertFalse(requiresSpearKillAttackRequest(SpearKillActivationMode.HoldUse))
        assertFalse(isSpearKillActivationSatisfied(SpearKillActivationMode.Manual, false, true))
        assertTrue(isSpearKillActivationSatisfied(SpearKillActivationMode.Manual, true, true))
        assertFalse(isSpearKillActivationSatisfied(SpearKillActivationMode.HoldUse, true, false))
        assertTrue(isSpearKillActivationSatisfied(SpearKillActivationMode.HoldUse, false, true))
    }

    @Test
    fun `KillAura inheritance overrides every configured SpearKill activation mode`() {
        SpearKillActivationMode.entries.forEach { activationMode ->
            assertTrue(
                isSpearKillActivationSatisfied(
                    activationMode = activationMode,
                    attackRequested = false,
                    useKeyDown = false,
                    inheritedKillAuraRequest = true,
                ),
                activationMode.name,
            )
        }
    }

    @Test
    fun `KillAura inheritance starts an idle held spear and preserves an existing spear use`() {
        assertEquals(
            SpearKillInheritedUseAction.START_MAIN_HAND,
            resolveSpearKillInheritedUseAction(true, true, true, false, false),
        )
        assertEquals(
            SpearKillInheritedUseAction.START_OFF_HAND,
            resolveSpearKillInheritedUseAction(true, false, true, false, false),
        )
        assertEquals(
            SpearKillInheritedUseAction.KEEP_CURRENT_USE,
            resolveSpearKillInheritedUseAction(true, true, false, true, true),
        )
    }

    @Test
    fun `KillAura inheritance never steals unrelated item use or releases borrowed spear use`() {
        assertEquals(
            SpearKillInheritedUseAction.NONE,
            resolveSpearKillInheritedUseAction(true, true, false, true, false),
        )
        assertFalse(shouldStopSpearKillInheritedUse(false, true, true, true))
        assertFalse(shouldStopSpearKillInheritedUse(true, true, false, true))
        assertFalse(shouldStopSpearKillInheritedUse(true, true, true, false))
        assertTrue(shouldStopSpearKillInheritedUse(true, true, true, true))
    }

    @Test
    fun `KillAura owned spear use remains held without a physical use key`() {
        assertTrue(shouldPreserveSpearKillInheritedUse(true, true, true, true))
        assertFalse(shouldPreserveSpearKillInheritedUse(false, true, true, true))
        assertFalse(shouldPreserveSpearKillInheritedUse(true, false, true, true))
        assertFalse(shouldPreserveSpearKillInheritedUse(true, true, false, true))
        assertFalse(shouldPreserveSpearKillInheritedUse(true, true, true, false))
    }

    @Test
    fun `disabling KillAura deactivates SpearKill and returns its active route`() {
        assertEquals(
            SpearKillKillAuraReleaseAction.DEACTIVATE_AND_RETURN,
            resolveSpearKillKillAuraReleaseAction(
                spearKillEnabled = true,
                killAuraOwnsAttempt = true,
                routeActive = true,
                killAuraPreparationActive = false,
                inheritedUseActive = true,
            ),
        )
    }

    @Test
    fun `disabling KillAura releases inherited preparation and spear use`() {
        assertEquals(
            SpearKillKillAuraReleaseAction.CANCEL_INHERITED_PREPARATION,
            resolveSpearKillKillAuraReleaseAction(
                spearKillEnabled = false,
                killAuraOwnsAttempt = false,
                routeActive = false,
                killAuraPreparationActive = true,
                inheritedUseActive = true,
            ),
        )
        assertEquals(
            SpearKillKillAuraReleaseAction.RELEASE_INHERITED_USE,
            resolveSpearKillKillAuraReleaseAction(
                spearKillEnabled = false,
                killAuraOwnsAttempt = false,
                routeActive = false,
                killAuraPreparationActive = false,
                inheritedUseActive = true,
            ),
        )
    }

    @Test
    fun `disabling KillAura deactivates an unrelated SpearKill route instead of chaining targets`() {
        assertEquals(
            SpearKillKillAuraReleaseAction.DEACTIVATE_AND_RETURN,
            resolveSpearKillKillAuraReleaseAction(
                spearKillEnabled = true,
                killAuraOwnsAttempt = false,
                routeActive = true,
                killAuraPreparationActive = false,
                inheritedUseActive = false,
            ),
        )
        assertEquals(
            SpearKillKillAuraReleaseAction.DEACTIVATE_AND_RETURN,
            resolveSpearKillKillAuraReleaseAction(
                spearKillEnabled = true,
                killAuraOwnsAttempt = false,
                routeActive = true,
                killAuraPreparationActive = false,
                inheritedUseActive = true,
            ),
        )
    }

    @Test
    fun `disabling KillAura deactivates idle SpearKill`() {
        assertEquals(
            SpearKillKillAuraReleaseAction.DEACTIVATE,
            resolveSpearKillKillAuraReleaseAction(
                spearKillEnabled = true,
                killAuraOwnsAttempt = false,
                routeActive = false,
                killAuraPreparationActive = false,
                inheritedUseActive = false,
            ),
        )
    }

    @Test
    fun `Manual activation keeps one click armed until launch or spear-use release`() {
        val latched = nextSpearKillManualAttackRequestLatch(
            activationMode = SpearKillActivationMode.Manual,
            holdingSpear = true,
            isUsingSpear = true,
            useInputHeld = true,
            wasLatched = false,
            attackPressed = true,
        )

        assertTrue(latched)
        assertTrue(
            nextSpearKillManualAttackRequestLatch(
                activationMode = SpearKillActivationMode.Manual,
                holdingSpear = true,
                isUsingSpear = true,
                useInputHeld = true,
                wasLatched = latched,
                attackPressed = false,
            ),
        )
        assertFalse(
            nextSpearKillManualAttackRequestLatch(
                activationMode = SpearKillActivationMode.Manual,
                holdingSpear = true,
                isUsingSpear = true,
                useInputHeld = false,
                wasLatched = latched,
                attackPressed = false,
            ),
        )
        assertFalse(
            nextSpearKillManualAttackRequestLatch(
                activationMode = SpearKillActivationMode.HoldUse,
                holdingSpear = true,
                isUsingSpear = true,
                useInputHeld = true,
                wasLatched = false,
                attackPressed = true,
            ),
        )
    }

    @Test
    fun `HoldUse launches on the first safe charged tick without an attack request`() {
        val holdUseSatisfied = isSpearKillActivationSatisfied(
            activationMode = SpearKillActivationMode.HoldUse,
            attackRequested = false,
            useKeyDown = true,
        )

        assertFalse(shouldStartSpearKillAttempt(false, holdUseSatisfied, true, 3, 3, 20))
        assertTrue(shouldStartSpearKillAttempt(false, holdUseSatisfied, true, 4, 3, 20))
        assertFalse(shouldStartSpearKillAttempt(false, holdUseSatisfied, true, 20, 3, 20))
        assertFalse(shouldStartSpearKillAttempt(true, holdUseSatisfied, true, 4, 3, 20))
    }

    @Test
    fun `KillAura can acquire SpearKill range before the spear is charged`() {
        val acquisitionAvailable = isSpearKillKillAuraAcquisitionAvailable(
            moduleEnabled = true,
            moduleRunning = true,
            delegationEnabled = true,
            holdingSpear = true,
            routeBlocked = false,
        )

        assertTrue(acquisitionAvailable)
        assertFalse(
            isSpearKillKillAuraAttackArmed(
                acquisitionAvailable = acquisitionAvailable,
                usingSpear = false,
                activationRequested = false,
                hasKineticWeapon = false,
            ),
        )
        assertTrue(
            isSpearKillKillAuraAttackArmed(
                acquisitionAvailable = acquisitionAvailable,
                usingSpear = true,
                activationRequested = true,
                hasKineticWeapon = true,
            ),
        )
    }

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
