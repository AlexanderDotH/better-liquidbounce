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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EspGlowFrameStateTest {

    @Test
    fun `first mask preparation clears and later contributions append until next frame`() {
        val state = EspGlowFrameState()

        assertTrue(state.prepareMask())
        assertFalse(state.prepareMask())
        assertFalse(state.hasContribution)

        val subtleBlockGlow = EspGlowStyle(
            radius = 6f,
            softness = 0.6f,
            intensity = 0.4f,
            coreSize = 0.25f,
            opacity = 0.35f,
        )
        state.contribute(subtleBlockGlow)

        assertTrue(state.hasContribution)
        assertEquals(subtleBlockGlow, state.style)

        state.contribute(
            EspGlowStyle(
                radius = 18f,
                softness = 1.2f,
                intensity = 1.4f,
                coreSize = 2f,
                opacity = 0.8f,
            )
        )
        assertEquals(18f, state.style.radius)
        assertEquals(0.8f, state.style.opacity)

        state.reset()

        assertFalse(state.hasContribution)
        assertTrue(state.prepareMask())
    }

    @Test
    fun `five percent tracer halo remains independent from stronger full world glow`() {
        val lanes = EspGlowFrameLanes()
        val fullWorldGlow = EspGlowStyle(
            radius = 24f,
            softness = 1.5f,
            intensity = 1.17f,
            coreSize = 3f,
            opacity = 1f,
        )
        val tracerHalo = EspGlowStyle(
            radius = 4f,
            softness = 0.68f,
            intensity = 0.52f,
            coreSize = 0f,
            opacity = 0.05f,
        )

        lanes.contribute(EspGlowContributionRole.FULL, fullWorldGlow)
        lanes.contribute(EspGlowContributionRole.HALO_ONLY, tracerHalo)

        assertEquals(fullWorldGlow, lanes.full.style)
        assertEquals(tracerHalo, lanes.haloOnly.style)
        assertTrue(lanes.hasAnyContribution)

        lanes.reset()

        assertFalse(lanes.full.hasContribution)
        assertFalse(lanes.haloOnly.hasContribution)
        assertFalse(lanes.hasAnyContribution)
    }
}
