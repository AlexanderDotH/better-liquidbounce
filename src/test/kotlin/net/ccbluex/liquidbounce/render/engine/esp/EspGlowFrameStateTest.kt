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
    fun `portal player storage and base finder keep independent masks and styles`() {
        val sources = EspGlowFrameSources()
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

        assertTrue(sources.prepareMask(EspGlowSource.PLAYER_ESP))
        assertTrue(sources.prepareMask(EspGlowSource.BLOCK_ESP))
        assertTrue(sources.prepareMask(EspGlowSource.STORAGE_ESP))
        assertTrue(sources.prepareMask(EspGlowSource.BASE_FINDER))

        sources.contribute(EspGlowSource.PLAYER_ESP, fullWorldGlow)
        sources.contribute(EspGlowSource.BLOCK_ESP, tracerHalo)
        sources.contribute(EspGlowSource.STORAGE_ESP, tracerHalo.copy(opacity = 0.35f))
        sources.contribute(EspGlowSource.BASE_FINDER, fullWorldGlow.copy(radius = 18f))

        assertEquals(fullWorldGlow, sources.state(EspGlowSource.PLAYER_ESP).style)
        assertEquals(tracerHalo, sources.state(EspGlowSource.BLOCK_ESP).style)
        assertEquals(0.35f, sources.state(EspGlowSource.STORAGE_ESP).style.opacity)
        assertEquals(18f, sources.state(EspGlowSource.BASE_FINDER).style.radius)
        assertFalse(sources.prepareMask(EspGlowSource.PLAYER_ESP))
        assertTrue(sources.hasAnyContribution)

        sources.reset()

        assertFalse(sources.state(EspGlowSource.PLAYER_ESP).hasContribution)
        assertFalse(sources.state(EspGlowSource.BLOCK_ESP).hasContribution)
        assertFalse(sources.hasAnyContribution)
    }

    @Test
    fun `Block ESP yields to BlockOverlay and nested model owners`() {
        val activeSources = listOf(
            EspGlowSource.BASE_FINDER,
            EspGlowSource.BLOCK_ESP,
            EspGlowSource.BLOCK_OUTLINE,
            EspGlowSource.STORAGE_ESP,
            EspGlowSource.TARGET_GLOW,
            EspGlowSource.PLAYER_ESP,
        )

        assertEquals(
            listOf(
                EspGlowSource.BLOCK_OUTLINE,
                EspGlowSource.STORAGE_ESP,
                EspGlowSource.TARGET_GLOW,
                EspGlowSource.PLAYER_ESP,
            ),
            EspGlowProtectionPlan.exclusionSources(EspGlowSource.BLOCK_ESP, activeSources),
        )
    }

    @Test
    fun `large early masks cannot suppress later nested ESP owners`() {
        val activeSources = listOf(
            EspGlowSource.BASE_FINDER,
            EspGlowSource.BLOCK_ESP,
            EspGlowSource.BLOCK_OUTLINE,
            EspGlowSource.STORAGE_ESP,
        )

        assertEquals(
            listOf(
                EspGlowSource.BLOCK_ESP,
                EspGlowSource.BLOCK_OUTLINE,
                EspGlowSource.STORAGE_ESP,
            ),
            EspGlowProtectionPlan.exclusionSources(EspGlowSource.BASE_FINDER, activeSources),
        )
        assertFalse(
            EspGlowSource.BASE_FINDER in
                EspGlowProtectionPlan.exclusionSources(EspGlowSource.STORAGE_ESP, activeSources)
        )
    }

    @Test
    fun `BlockOverlay mask protects its surface even when its own Glow pass is disabled`() {
        val sources = EspGlowFrameSources()

        sources.prepareMask(EspGlowSource.BLOCK_OUTLINE)

        assertEquals(listOf(EspGlowSource.BLOCK_OUTLINE), sources.maskSources)
        assertTrue(sources.activeSources.isEmpty())
        assertFalse(sources.hasAnyContribution)
    }

    @Test
    fun `tracer yields to a visible Player ESP surface`() {
        assertEquals(
            listOf(EspGlowSource.PLAYER_ESP),
            EspGlowProtectionPlan.exclusionSources(
                EspGlowSource.TRACERS,
                listOf(EspGlowSource.PLAYER_ESP),
            ),
        )
    }
}
