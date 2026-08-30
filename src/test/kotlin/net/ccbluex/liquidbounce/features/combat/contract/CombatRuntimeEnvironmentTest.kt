/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.combat.contract

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatRuntimeEnvironmentTest {

    @Test
    fun `unbound providers fail closed`() = CombatRuntimeEnvironment.withProvidersForTest {
        assertFalse(CombatRuntimeEnvironment.wouldDoCriticalHit(ignoreSprint = true))
        assertFalse(CombatRuntimeEnvironment.hasActiveKillAuraTarget())
        assertFalse(CombatRuntimeEnvironment.isDetachedViewEnabled())
        assertFalse(CombatRuntimeEnvironment.shouldPauseRotation())
    }

    @Test
    fun `providers preserve critical target view and pause state`() {
        var forwardedIgnoreSprint: Boolean? = null

        CombatRuntimeEnvironment.withProvidersForTest(
            criticalHit = { ignoreSprint ->
                forwardedIgnoreSprint = ignoreSprint
                true
            },
            killAuraTarget = { true },
            freeCam = { false },
            freeLook = { true },
            rotationPaused = { true },
        ) {
            assertTrue(CombatRuntimeEnvironment.wouldDoCriticalHit(ignoreSprint = true))
            assertEquals(true, forwardedIgnoreSprint)
            assertTrue(CombatRuntimeEnvironment.hasActiveKillAuraTarget())
            assertTrue(CombatRuntimeEnvironment.isDetachedViewEnabled())
            assertTrue(CombatRuntimeEnvironment.shouldPauseRotation())
        }
    }

    @Test
    fun `either detached camera provider exposes self`() {
        CombatRuntimeEnvironment.withProvidersForTest(freeCam = { true }) {
            assertTrue(CombatRuntimeEnvironment.isDetachedViewEnabled())
        }
        CombatRuntimeEnvironment.withProvidersForTest(freeLook = { true }) {
            assertTrue(CombatRuntimeEnvironment.isDetachedViewEnabled())
        }
    }
}
