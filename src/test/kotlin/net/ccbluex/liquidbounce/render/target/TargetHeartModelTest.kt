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
package net.ccbluex.liquidbounce.render.target

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TargetHeartModelTest {

    @Test
    fun `dynamic heart slots preserve full partial and absorption ordering`() {
        val slots = buildList {
            addTargetHeartSlots(health = 5f, absorption = 3f, dynamicCount = true, configuredHeartCount = 10)
        }

        assertEquals(
            listOf(
                TargetHeartSlot(TargetHeartType.HEALTH, 1f),
                TargetHeartSlot(TargetHeartType.HEALTH, 1f),
                TargetHeartSlot(TargetHeartType.HEALTH, 0.5f),
                TargetHeartSlot(TargetHeartType.ABSORPTION, 1f),
                TargetHeartSlot(TargetHeartType.ABSORPTION, 0.5f),
            ),
            slots,
        )
    }

    @Test
    fun `fixed heart count still appends absorption`() {
        val slots = buildList {
            addTargetHeartSlots(health = 1f, absorption = 2f, dynamicCount = false, configuredHeartCount = 2)
        }

        assertEquals(3, slots.size)
        assertEquals(listOf(TargetHeartType.HEALTH, TargetHeartType.HEALTH, TargetHeartType.ABSORPTION), slots.map { it.type })
    }

    @Test
    fun `placement overlap uses wrapped angle distance and height distance`() {
        val placement = TargetHeartPlacement(359f, 0.5f)

        assertTrue(placement.overlaps(TargetHeartPlacement(1f, 0.55f), 3f, 0.1f))
        assertFalse(placement.overlaps(TargetHeartPlacement(10f, 0.55f), 3f, 0.1f))
        assertFalse(placement.overlaps(TargetHeartPlacement(1f, 0.8f), 3f, 0.1f))
    }
}
