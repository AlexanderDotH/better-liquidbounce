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

class SpearKillHoldUseLaunchesOnFirstSafeChargedTickTest {

    @Test
    fun `HoldUse launches on the first safe charged tick without an attack request`() {
        val holdUseSatisfied = isSpearKillActivationSatisfied(
            activationMode = SpearKillActivationMode.HoldUse,
            attackRequested = false,
            useKeyDown = true,
        )

        assertFalse(shouldStartSpearKillAttempt(false, holdUseSatisfied, true, 2, 3, 20))
        assertTrue(shouldStartSpearKillAttempt(false, holdUseSatisfied, true, 3, 3, 20))
        assertTrue(shouldStartSpearKillAttempt(false, holdUseSatisfied, true, 20, 3, 20))
        assertTrue(shouldStartSpearKillAttempt(false, holdUseSatisfied, true, 3, 3, 3))
        assertFalse(shouldStartSpearKillAttempt(true, holdUseSatisfied, true, 4, 3, 20))
    }

    @Test
    fun `HoldUse relaunches only when a different cursor target enters the held gesture`() {
        val firstTarget = Any()
        val secondTarget = Any()
        val launchedTarget = nextSpearKillHoldUseLaunchTarget(
            activationMode = SpearKillActivationMode.HoldUse,
            holdingSpear = true,
            useInputHeld = true,
            currentTarget = null,
            launchedTarget = firstTarget,
            launchStarted = true,
        )

        assertSame(firstTarget, launchedTarget)
        assertFalse(
            isSpearKillLaunchActivationSatisfied(
                activationMode = SpearKillActivationMode.HoldUse,
                activationRequested = true,
                previousLaunchTarget = launchedTarget,
                launchTarget = firstTarget,
                automaticRequest = false,
            ),
        )
        assertNull(
            selectSpearKillHoldUseLaunchTarget(
                activationMode = SpearKillActivationMode.HoldUse,
                useInputHeld = true,
                automaticRequest = false,
                previousLaunchTarget = launchedTarget,
                cursorTarget = firstTarget,
                configuredTarget = firstTarget,
            ),
        )

        val retargeted = selectSpearKillHoldUseLaunchTarget(
            activationMode = SpearKillActivationMode.HoldUse,
            useInputHeld = true,
            automaticRequest = false,
            previousLaunchTarget = launchedTarget,
            cursorTarget = secondTarget,
            configuredTarget = firstTarget,
        )
        assertSame(secondTarget, retargeted)
        assertTrue(
            isSpearKillLaunchActivationSatisfied(
                activationMode = SpearKillActivationMode.HoldUse,
                activationRequested = true,
                previousLaunchTarget = launchedTarget,
                launchTarget = retargeted,
                automaticRequest = false,
            ),
        )
    }

    @Test
    fun `releasing physical spear use clears the HoldUse target identity`() {
        val target = Any()
        val launchedTarget = nextSpearKillHoldUseLaunchTarget(
            activationMode = SpearKillActivationMode.HoldUse,
            holdingSpear = true,
            useInputHeld = true,
            currentTarget = null,
            launchedTarget = target,
            launchStarted = true,
        )
        val releasedTarget = nextSpearKillHoldUseLaunchTarget(
            activationMode = SpearKillActivationMode.HoldUse,
            holdingSpear = true,
            useInputHeld = false,
            currentTarget = launchedTarget,
            launchedTarget = null,
            launchStarted = false,
        )

        assertNull(releasedTarget)
        assertTrue(
            isSpearKillLaunchActivationSatisfied(
                activationMode = SpearKillActivationMode.HoldUse,
                activationRequested = true,
                previousLaunchTarget = releasedTarget,
                launchTarget = target,
                automaticRequest = false,
            ),
        )
    }

    @Test
    fun `automatic spear ownership bypasses a consumed manual HoldUse launch`() {
        val previousTarget = Any()
        val automaticTarget = Any()
        assertSame(
            automaticTarget,
            selectSpearKillHoldUseLaunchTarget(
                activationMode = SpearKillActivationMode.HoldUse,
                useInputHeld = true,
                automaticRequest = true,
                previousLaunchTarget = previousTarget,
                cursorTarget = null,
                configuredTarget = automaticTarget,
            ),
        )
        assertTrue(
            isSpearKillLaunchActivationSatisfied(
                activationMode = SpearKillActivationMode.HoldUse,
                activationRequested = true,
                previousLaunchTarget = previousTarget,
                launchTarget = previousTarget,
                automaticRequest = true,
            ),
        )
        assertTrue(
            isSpearKillLaunchActivationSatisfied(
                activationMode = SpearKillActivationMode.Manual,
                activationRequested = true,
                previousLaunchTarget = previousTarget,
                launchTarget = previousTarget,
                automaticRequest = false,
            ),
        )
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
}
