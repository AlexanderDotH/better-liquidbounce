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
package net.ccbluex.liquidbounce.features.module.modules.world.packetmine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PacketMineTickSchedulerTest {

    @Test
    fun `finish waits one complete tick before scheduling the next start`() {
        val scheduler = PacketMineTickScheduler()
        scheduler.advanceTick()

        assertFalse(scheduler.shouldFinish(readyTick = 1L, postBreakDelay = 6))

        scheduler.advanceTick()

        assertTrue(scheduler.shouldFinish(readyTick = 1L, postBreakDelay = 6))
        assertEquals(8L, scheduler.nextAllowedStartTick)
        assertFalse(scheduler.canStart())
    }

    @Test
    fun `reset clears only the chained break delay`() {
        val scheduler = PacketMineTickScheduler()
        scheduler.advanceTick()
        scheduler.advanceTick()
        scheduler.shouldFinish(readyTick = 1L, postBreakDelay = 6)

        scheduler.resetStartDelay()

        assertEquals(2L, scheduler.tick)
        assertEquals(0L, scheduler.nextAllowedStartTick)
        assertTrue(scheduler.canStart())
    }
}
