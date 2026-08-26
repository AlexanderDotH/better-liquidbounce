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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VClipMiddleClickInputTest {

    @Test
    fun `holding middle click allows repeated clips without another press`() {
        val input = VClipMiddleClickInput()

        input.press()
        assertTrue(input.isHeld)
        assertEquals(VClipDirection.UP, input.resolveDirection(true, false, repeatDelayTicks = 2))
        assertNull(input.resolveDirection(true, false, repeatDelayTicks = 2))
        assertNull(input.resolveDirection(true, false, repeatDelayTicks = 2))
        assertEquals(VClipDirection.UP, input.resolveDirection(true, false, repeatDelayTicks = 2))
        assertTrue(input.isHeld)
    }

    @Test
    fun `held middle click can clip both directions without being pressed again`() {
        val input = VClipMiddleClickInput()

        input.press()
        assertEquals(VClipDirection.UP, input.resolveDirection(true, false, repeatDelayTicks = 0))
        assertEquals(VClipDirection.DOWN, input.resolveDirection(false, true, repeatDelayTicks = 0))
        assertTrue(input.isHeld)
    }

    @Test
    fun `simultaneous direction keys do not clip while modifier stays held`() {
        val input = VClipMiddleClickInput()

        input.press()
        assertNull(input.resolveDirection(true, true, repeatDelayTicks = 0))
        assertTrue(input.isHeld)
    }

    @Test
    fun `release restores normal controls and resets repeat cooldown`() {
        val input = VClipMiddleClickInput()

        input.press()
        assertEquals(VClipDirection.UP, input.resolveDirection(true, false, repeatDelayTicks = 5))
        input.release()
        assertFalse(input.isHeld)
        assertNull(input.resolveDirection(true, false, repeatDelayTicks = 5))

        input.press()
        assertEquals(VClipDirection.DOWN, input.resolveDirection(false, true, repeatDelayTicks = 5))
    }

    @Test
    fun `lifecycle reset releases held modifier`() {
        val input = VClipMiddleClickInput()

        input.press()
        input.reset()

        assertFalse(input.isHeld)
        assertNull(input.resolveDirection(true, false, repeatDelayTicks = 0))
    }

    @Test
    fun `smart lock preserves normal controls until middle click is held`() {
        assertEquals(
            VClipInputSuppression(jump = false, sneak = false),
            VClipInputSuppression.resolve(smartLockActive = true, modifierHeld = false),
        )
        assertEquals(
            VClipInputSuppression(jump = true, sneak = true),
            VClipInputSuppression.resolve(smartLockActive = true, modifierHeld = true),
        )
    }

    @Test
    fun `vclip without smart lock keeps suppressing jump and sneak`() {
        assertEquals(
            VClipInputSuppression(jump = true, sneak = true),
            VClipInputSuppression.resolve(smartLockActive = false, modifierHeld = false),
        )
    }
}
