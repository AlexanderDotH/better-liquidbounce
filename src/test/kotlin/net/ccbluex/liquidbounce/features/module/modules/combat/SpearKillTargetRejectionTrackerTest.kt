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
package net.ccbluex.liquidbounce.features.module.modules.combat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillTargetRejectionTrackerTest {

    @Test
    fun `held activation retries a rejected target after the cooldown`() {
        val target = Any()
        val tracker = SpearKillTargetRejectionTracker<Any>(retryDelayTicks = 20)

        tracker.reject(target, currentTick = 100)

        assertTrue(tracker.isRejected(target, currentTick = 100))
        assertTrue(tracker.isRejected(target, currentTick = 119))
        assertFalse(tracker.isRejected(target, currentTick = 120))
    }

    @Test
    fun `successful target state clears rejection immediately`() {
        val target = Any()
        val tracker = SpearKillTargetRejectionTracker<Any>(retryDelayTicks = 20)
        tracker.reject(target, currentTick = 100)

        tracker.allow(target)

        assertFalse(tracker.isRejected(target, currentTick = 100))
    }
}
