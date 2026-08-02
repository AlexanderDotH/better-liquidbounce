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

package net.ccbluex.liquidbounce.render.engine.esp

import net.ccbluex.liquidbounce.common.EspMaskCaptureContext
import net.ccbluex.liquidbounce.common.EspMaskRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EspMaskCaptureContextTest {

    @Test
    fun `unbound context has no capture request`() {
        assertSame(EspMaskRequest.NONE, EspMaskCaptureContext.current())
    }

    @Test
    fun `nested capture restores the outer request`() {
        val outer = EspMaskRequest(0xFF11_2233.toInt(), 0)
        val inner = EspMaskRequest(0, 0xFF44_5566.toInt())

        EspMaskCaptureContext.run(outer) {
            assertEquals(outer, EspMaskCaptureContext.current())
            EspMaskCaptureContext.run(inner) {
                assertEquals(inner, EspMaskCaptureContext.current())
            }
            assertEquals(outer, EspMaskCaptureContext.current())
        }

        assertSame(EspMaskRequest.NONE, EspMaskCaptureContext.current())
    }

    @Test
    fun `exception cannot leak a capture request`() {
        val request = EspMaskRequest(0xFFAA_BBCC.toInt(), 0)

        assertThrows<IllegalStateException> {
            EspMaskCaptureContext.run(request) { error("boom") }
        }

        assertSame(EspMaskRequest.NONE, EspMaskCaptureContext.current())
    }

    @Test
    fun `mask effects merge independently and preserve the first color`() {
        val request = EspMaskRequest.NONE
            .withGlow(0x4011_2233)
            .withGlow(0xFFAA_BBCC.toInt())
            .withOutline(0x8044_5566.toInt())

        assertEquals(0xFF11_2233.toInt(), request.glowColor)
        assertEquals(0xFF44_5566.toInt(), request.outlineColor)
    }

    @Test
    fun `transparent requests do not create a mask`() {
        assertSame(EspMaskRequest.NONE, EspMaskRequest.NONE.withGlow(0).withOutline(0))
    }
}
