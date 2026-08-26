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

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VClipLandingIndicatorTest {

    @Test
    fun `upward selection renders at the exact teleport destination`() {
        val indicator = VClipLandingIndicator.resolve(
            origin = VClipPosition(12.75, 120.5, -4.25),
            target = VClipPosition(14.75, 130.5, -2.25),
            direction = VClipDirection.UP,
        )!!

        assertEquals(14.0, indicator.renderPosition.x)
        assertEquals(130.5, indicator.renderPosition.y)
        assertEquals(-3.0, indicator.renderPosition.z)
        assertEquals(10.0, indicator.verticalDistance)
        assertEquals(VClipDirection.UP, indicator.direction)
    }

    @Test
    fun `downward selection renders at the exact teleport destination`() {
        val indicator = VClipLandingIndicator.resolve(
            origin = VClipPosition(12.75, 120.5, -4.25),
            target = VClipPosition(10.75, 70.5, -6.25),
            direction = VClipDirection.DOWN,
        )!!

        assertEquals(10.0, indicator.renderPosition.x)
        assertEquals(70.5, indicator.renderPosition.y)
        assertEquals(-7.0, indicator.renderPosition.z)
        assertEquals(50.0, indicator.verticalDistance)
        assertEquals(VClipDirection.DOWN, indicator.direction)
    }

    @Test
    fun `missing or opposite-direction destination has no selection`() {
        val origin = VClipPosition(0.0, 64.0, 0.0)

        assertNull(VClipLandingIndicator.resolve(origin, null, VClipDirection.UP))
        assertNull(VClipLandingIndicator.resolve(origin, origin.copy(y = 63.0), VClipDirection.UP))
        assertNull(VClipLandingIndicator.resolve(origin, origin.copy(y = 65.0), VClipDirection.DOWN))
    }

    @Test
    fun `upward and downward selections use their corresponding target distances`() {
        val origin = VClipPosition(0.0, 120.0, 0.0)
        val upward = VClipLandingIndicator.resolve(
            origin,
            origin.copy(y = 185.0),
            VClipDirection.UP,
        )!!
        val downward = VClipLandingIndicator.resolve(
            origin,
            origin.copy(y = 30.0),
            VClipDirection.DOWN,
        )!!

        assertEquals(VClipLandingIndicator.colorForDistance(65.0), upward.color)
        assertEquals(VClipLandingIndicator.colorForDistance(90.0), downward.color)
    }

    @Test
    fun `landing color is green at 50 blocks orange at 80 and red from 100 blocks`() {
        assertEquals(Color4b(0x20, 0xC2, 0x06), VClipLandingIndicator.colorForDistance(50.0))
        assertEquals(Color4b.ORANGE, VClipLandingIndicator.colorForDistance(80.0))
        assertEquals(Color4b(0xD7, 0x09, 0x09), VClipLandingIndicator.colorForDistance(100.0))
        assertEquals(Color4b(0xD7, 0x09, 0x09), VClipLandingIndicator.colorForDistance(100.01))
    }

    @Test
    fun `landing color transitions smoothly between the requested distance anchors`() {
        val safe = Color4b(0x20, 0xC2, 0x06)
        val danger = Color4b(0xD7, 0x09, 0x09)

        assertEquals(safe.interpolateTo(Color4b.ORANGE, 0.5), VClipLandingIndicator.colorForDistance(65.0))
        assertEquals(Color4b.ORANGE.interpolateTo(danger, 0.5), VClipLandingIndicator.colorForDistance(90.0))
    }
}
