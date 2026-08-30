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

internal class ClickSchedule(
    private val cycleLength: Int = DEFAULT_CYCLE_LENGTH,
) {

    private val rolling = RollingClickArray(cycleLength, ITERATIONS)

    val ticksUntilClick: Int
        get() = (0 until rolling.iterations).firstOrNull(::willClickAt) ?: rolling.iterations

    fun getClickAmount(tick: Int): Int = rolling.get(tick)

    fun willClickAt(tick: Int): Boolean = getClickAmount(tick) > 0

    fun advanceAndRefill(pattern: ClickPattern, cps: IntRange, context: ClickPatternContext) {
        if (rolling.advance()) {
            rolling.push(createCycle(pattern, cps, context))
        }
    }

    fun refill(pattern: ClickPattern, cps: IntRange, context: ClickPatternContext) {
        rolling.clear()
        repeat(rolling.iterations) {
            rolling.push(createCycle(pattern, cps, context))
            rolling.advance(cycleLength)
        }
    }

    fun debugString(): String = rolling.array.withIndex().joinToString { (index, clicks) ->
        if (index == rolling.head) "*$clicks" else clicks.toString()
    }

    private fun createCycle(pattern: ClickPattern, cps: IntRange, context: ClickPatternContext): IntArray {
        return IntArray(cycleLength).also { pattern.fill(it, cps, context) }
    }

    private companion object {
        const val DEFAULT_CYCLE_LENGTH = 20
        const val ITERATIONS = 2
    }
}
