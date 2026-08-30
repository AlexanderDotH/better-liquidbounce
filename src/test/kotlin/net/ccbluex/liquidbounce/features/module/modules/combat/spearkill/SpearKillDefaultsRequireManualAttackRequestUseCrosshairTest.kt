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

class SpearKillDefaultsRequireManualAttackRequestUseCrosshairTest {

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
    fun `disabling KillAura cancels only the route owned by KillAura`() {
        assertEquals(
            SpearKillKillAuraReleaseAction.CANCEL_INHERITED_ROUTE,
            resolveSpearKillKillAuraReleaseAction(
                killAuraOwnsAttempt = true,
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
                killAuraOwnsAttempt = false,
                killAuraPreparationActive = true,
                inheritedUseActive = true,
            ),
        )
        assertEquals(
            SpearKillKillAuraReleaseAction.RELEASE_INHERITED_USE,
            resolveSpearKillKillAuraReleaseAction(
                killAuraOwnsAttempt = false,
                killAuraPreparationActive = false,
                inheritedUseActive = true,
            ),
        )
    }

    @Test
    fun `disabling KillAura leaves unrelated and idle SpearKill ownership untouched`() {
        assertEquals(
            SpearKillKillAuraReleaseAction.NONE,
            resolveSpearKillKillAuraReleaseAction(
                killAuraOwnsAttempt = false,
                killAuraPreparationActive = false,
                inheritedUseActive = false,
            ),
        )
        assertEquals(
            SpearKillKillAuraReleaseAction.RELEASE_INHERITED_USE,
            resolveSpearKillKillAuraReleaseAction(
                killAuraOwnsAttempt = false,
                killAuraPreparationActive = false,
                inheritedUseActive = true,
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
}
