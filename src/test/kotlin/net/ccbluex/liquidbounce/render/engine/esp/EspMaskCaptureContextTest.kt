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
import net.ccbluex.liquidbounce.common.EspMaskLayer
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
        val outer = EspMaskRequest.NONE.with(EspMaskLayer.PLAYER_GLOW, 0xFF11_2233.toInt())
        val inner = EspMaskRequest.NONE.with(EspMaskLayer.STORAGE_OUTLINE, 0xFF44_5566.toInt())

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
        val request = EspMaskRequest.NONE.with(EspMaskLayer.TARGET_GLOW, 0xFFAA_BBCC.toInt())

        assertThrows<IllegalStateException> {
            EspMaskCaptureContext.run(request) { error("boom") }
        }

        assertSame(EspMaskRequest.NONE, EspMaskCaptureContext.current())
    }

    @Test
    fun `player target and storage colors retain separate mask ownership`() {
        val request = EspMaskRequest.NONE
            .with(EspMaskLayer.PLAYER_GLOW, 0x4011_2233)
            .with(EspMaskLayer.TARGET_GLOW, 0xFFAA_BBCC.toInt())
            .with(EspMaskLayer.STORAGE_OUTLINE, 0x8044_5566.toInt())

        assertEquals(0xFF11_2233.toInt(), request.color(EspMaskLayer.PLAYER_GLOW))
        assertEquals(0xFFAA_BBCC.toInt(), request.color(EspMaskLayer.TARGET_GLOW))
        assertEquals(0xFF44_5566.toInt(), request.color(EspMaskLayer.STORAGE_OUTLINE))
    }

    @Test
    fun `transparent requests do not create a mask`() {
        assertSame(EspMaskRequest.NONE, EspMaskRequest.NONE.with(EspMaskLayer.PLAYER_GLOW, 0))
    }

    @Test
    fun `surface protection is independent from colored Player ESP`() {
        val request = EspMaskRequest.NONE.with(EspMaskLayer.PROTECTED_SURFACE, 0xFFFF_FFFF.toInt())

        assertEquals(0xFFFF_FFFF.toInt(), request.color(EspMaskLayer.PROTECTED_SURFACE))
        assertEquals(0, request.color(EspMaskLayer.PLAYER_GLOW))
    }
}
