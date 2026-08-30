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
package net.ccbluex.liquidbounce.features.clicking

import net.ccbluex.liquidbounce.features.clicking.pattern.ClickPattern
import net.ccbluex.liquidbounce.features.clicking.pattern.ClickPatternContext
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.test.assertEquals

class ClickScheduleTest {

    @Test
    fun `refill preserves rolling cycle order and regenerates only the exhausted half`() {
        val pattern = SequencedPattern()
        val schedule = ClickSchedule(cycleLength = 4)

        schedule.refill(pattern, 1..1, TEST_CONTEXT)

        assertEquals(listOf(2, 2, 2, 2, 1, 1, 1, 1), schedule.values(8))

        repeat(4) {
            schedule.advanceAndRefill(pattern, 1..1, TEST_CONTEXT)
        }

        assertEquals(listOf(1, 1, 1, 1, 3, 3, 3, 3), schedule.values(8))
        assertEquals(3, pattern.fillCount)
    }

    @Test
    fun `ticks until click keeps the original two tick prediction window`() {
        val schedule = ClickSchedule(cycleLength = 4)
        val secondTickPattern = object : ClickPattern {
            override fun fill(clickArray: IntArray, cps: IntRange, context: ClickPatternContext) {
                clickArray[1] = 1
            }
        }

        schedule.refill(secondTickPattern, 1..1, TEST_CONTEXT)

        assertEquals(1, schedule.ticksUntilClick)
        schedule.advanceAndRefill(secondTickPattern, 1..1, TEST_CONTEXT)
        assertEquals(0, schedule.ticksUntilClick)
        schedule.advanceAndRefill(secondTickPattern, 1..1, TEST_CONTEXT)
        assertEquals(2, schedule.ticksUntilClick)
    }

    private fun ClickSchedule.values(count: Int) = List(count, ::getClickAmount)

    private class SequencedPattern : ClickPattern {
        var fillCount = 0
            private set

        override fun fill(clickArray: IntArray, cps: IntRange, context: ClickPatternContext) {
            clickArray.fill(++fillCount)
        }
    }

    private companion object {
        val TEST_CONTEXT = object : ClickPatternContext {
            override val random = Random(0L)
        }
    }
}
