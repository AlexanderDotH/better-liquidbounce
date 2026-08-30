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
package net.ccbluex.liquidbounce.features.module.modules.combat.criticals.modes

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CriticalsJumpWaitPolicyTest {

    @Test
    fun `falling player attacks immediately when a critical is already ready`() {
        assertFalse(
            shouldWaitForFallingCritical(
                fallDistance = 0.1,
                attackStrength = 0.91f,
                nextPossibleCrit = 4.0f,
                collisionTick = null,
            ),
        )
    }

    @Test
    fun `falling player waits only when cooldown recovers before landing`() {
        assertTrue(shouldWaitForFallingCritical(0.0, 0.5f, 4.9f, collisionTick = null))
        assertFalse(shouldWaitForFallingCritical(0.0, 0.5f, 4.9f, collisionTick = 3))
        assertTrue(shouldWaitForFallingCritical(0.0, 0.5f, 4.9f, collisionTick = 4))
    }

    @Test
    fun `rising player stops waiting only when landing precedes the apex`() {
        assertTrue(canWaitThroughRisingCritical(ticksTillFall = 5.25f, collisionTick = null))
        assertFalse(canWaitThroughRisingCritical(ticksTillFall = 5.25f, collisionTick = 4))
        assertTrue(canWaitThroughRisingCritical(ticksTillFall = 5.25f, collisionTick = 5))
    }
}
