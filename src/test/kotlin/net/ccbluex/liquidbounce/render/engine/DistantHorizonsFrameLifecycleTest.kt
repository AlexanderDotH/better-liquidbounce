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
package net.ccbluex.liquidbounce.render.engine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DistantHorizonsFrameLifecycleTest {

    @Test
    fun `capture is ready only for the current frame and dimensions`() {
        val lifecycle = DistantHorizonsFrameLifecycle()

        lifecycle.beginFrame(41L)
        val capture = lifecycle.capture(1920, 1080)

        assertEquals(41L, capture?.frameToken)
        assertEquals(
            DistantHorizonsSourceReadiness.READY,
            lifecycle.readiness(installed = true, supported = true, 1920, 1080, 41L),
        )
        assertEquals(
            DistantHorizonsSourceReadiness.WRONG_SIZE,
            lifecycle.readiness(installed = true, supported = true, 1280, 720, 41L),
        )
    }

    @Test
    fun `a previous DH capture is stale when DH did not render in the new frame`() {
        val lifecycle = DistantHorizonsFrameLifecycle()

        lifecycle.beginFrame(10L)
        lifecycle.capture(1920, 1080)
        lifecycle.beginFrame(11L)

        assertEquals(
            DistantHorizonsSourceReadiness.STALE,
            lifecycle.readiness(installed = true, supported = true, 1920, 1080, 11L),
        )
        assertEquals(1L, lifecycle.frameAge(11L))
        assertEquals(10L, lifecycle.recentCapture(11L, maximumFrameAge = 1)?.frameToken)
        assertNull(lifecycle.recentCapture(12L, maximumFrameAge = 1))
    }

    @Test
    fun `resize and world invalidation discard the previous capture`() {
        val lifecycle = DistantHorizonsFrameLifecycle()

        lifecycle.beginFrame(7L)
        lifecycle.capture(1920, 1080)
        lifecycle.invalidate()

        assertNull(lifecycle.capturedFrame())
        assertEquals(
            DistantHorizonsSourceReadiness.INITIALIZING,
            lifecycle.readiness(installed = true, supported = true, 1920, 1080, 7L),
        )
    }

    @Test
    fun `absent and unsupported APIs fail closed before capture state`() {
        val lifecycle = DistantHorizonsFrameLifecycle()

        assertEquals(
            DistantHorizonsSourceReadiness.ABSENT,
            lifecycle.readiness(installed = false, supported = false, 1920, 1080, 1L),
        )
        assertEquals(
            DistantHorizonsSourceReadiness.UNSUPPORTED,
            lifecycle.readiness(installed = true, supported = false, 1920, 1080, 1L),
        )
    }

    @Test
    fun `GPU resource cache refreshes on device identity change and explicit invalidation`() {
        val cache = DistantHorizonsDeviceResourceCache<String>()
        var creations = 0

        assertEquals("device-1", cache.resolve(1) { "device-${++creations}" })
        assertEquals("device-1", cache.resolve(1) { "device-${++creations}" })
        assertEquals("device-2", cache.resolve(2) { "device-${++creations}" })
        cache.invalidate()
        assertEquals("device-3", cache.resolve(2) { "device-${++creations}" })
    }
}
