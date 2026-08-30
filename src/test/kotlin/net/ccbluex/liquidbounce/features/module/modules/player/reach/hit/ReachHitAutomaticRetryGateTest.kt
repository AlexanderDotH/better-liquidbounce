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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReachHitAutomaticRetryGateTest {

    @Test
    fun `automatic Reach Hit failures back off without delaying a different target`() {
        val retryGate = ReachHitAutomaticRetryGate(retryDelayTicks = 10)

        assertTrue(retryGate.canAttempt(targetId = 7, currentTick = 100))
        retryGate.recordFailure(targetId = 7, currentTick = 100)
        assertFalse(retryGate.canAttempt(targetId = 7, currentTick = 109))
        assertTrue(retryGate.canAttempt(targetId = 7, currentTick = 110))
        assertTrue(retryGate.canAttempt(targetId = 8, currentTick = 101))
    }

    @Test
    fun `automatic Reach Hit retry gate resets after success or explicit clear`() {
        val retryGate = ReachHitAutomaticRetryGate(retryDelayTicks = 10)

        retryGate.recordFailure(targetId = 7, currentTick = 100)
        retryGate.recordSuccess()
        assertTrue(retryGate.canAttempt(targetId = 7, currentTick = 101))

        retryGate.recordFailure(targetId = 7, currentTick = 102)
        retryGate.clear()
        assertTrue(retryGate.canAttempt(targetId = 7, currentTick = 103))
    }
}
