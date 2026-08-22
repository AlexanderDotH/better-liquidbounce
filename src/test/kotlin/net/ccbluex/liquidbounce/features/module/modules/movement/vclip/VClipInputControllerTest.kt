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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VClipInputControllerTest {

    @Test
    fun `space selects up and shift selects down`() {
        val controller = VClipInputController()

        assertEquals(
            VClipDirection.UP,
            controller.resolve(spacePressed = true, shiftPressed = false, repeatDelayTicks = 5),
        )
        controller.reset()
        assertEquals(
            VClipDirection.DOWN,
            controller.resolve(spacePressed = false, shiftPressed = true, repeatDelayTicks = 5),
        )
    }

    @Test
    fun `simultaneous space and shift never select a clip direction`() {
        val controller = VClipInputController()

        assertNull(controller.resolve(spacePressed = true, shiftPressed = true, repeatDelayTicks = 5))
    }

    @Test
    fun `holding space repeats only after the configured delay`() {
        val controller = VClipInputController()

        assertEquals(VClipDirection.UP, controller.resolve(true, false, repeatDelayTicks = 2))
        assertNull(controller.resolve(true, false, repeatDelayTicks = 2))
        assertNull(controller.resolve(true, false, repeatDelayTicks = 2))
        assertEquals(VClipDirection.UP, controller.resolve(true, false, repeatDelayTicks = 2))
    }
}
