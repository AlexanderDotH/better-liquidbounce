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
package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VelocityReduceTargetSelectorTest {

    @Test
    fun `lag without permission keeps the current targets without searching`() {
        val search = RecordingSearch(crosshairTarget = "crosshair", fallbackTarget = "fallback")
        val selector = VelocityReduceTargetSelector(search)
        val current = VelocityReduceTargets(attackTarget = "attack", renderTarget = "render")

        val result = selector.select(
            current = current,
            canLag = false,
            lagging = true,
            killAuraTarget = "aura",
            lagRange = 2f..6f,
            interactionRange = 3.5,
        )

        assertEquals(current, result)
        assertFalse(search.checkedKillAuraRange)
        assertTrue(search.crosshairRanges.isEmpty())
        assertTrue(search.fallbackRanges.isEmpty())
    }

    @Test
    fun `near KillAura target becomes the attack and render target before lag`() {
        val search = RecordingSearch(killAuraTargetWithinRange = true)
        val selector = VelocityReduceTargetSelector(search)

        val result = selector.select(
            current = VelocityReduceTargets(attackTarget = null, renderTarget = null),
            canLag = true,
            lagging = false,
            killAuraTarget = "aura",
            lagRange = 2f..6f,
            interactionRange = 3.5,
        )

        assertEquals(VelocityReduceTargets("aura", "aura"), result)
        assertTrue(search.checkedKillAuraRange)
        assertTrue(search.crosshairRanges.isEmpty())
    }

    @Test
    fun `distant KillAura target only replaces the render target before lag`() {
        val search = RecordingSearch(killAuraTargetWithinRange = false)
        val selector = VelocityReduceTargetSelector(search)

        val result = selector.select(
            current = VelocityReduceTargets(attackTarget = "attack", renderTarget = "render"),
            canLag = true,
            lagging = false,
            killAuraTarget = "aura",
            lagRange = 2f..6f,
            interactionRange = 3.5,
        )

        assertEquals(VelocityReduceTargets("attack", "aura"), result)
    }

    @Test
    fun `missing crosshair target uses the nearest render fallback before lag`() {
        val search = RecordingSearch(crosshairTarget = null, fallbackTarget = "fallback")
        val selector = VelocityReduceTargetSelector(search)

        val result = selector.select(
            current = VelocityReduceTargets(attackTarget = "old", renderTarget = "old-render"),
            canLag = true,
            lagging = false,
            killAuraTarget = null,
            lagRange = 2f..6f,
            interactionRange = 3.5,
        )

        assertEquals(VelocityReduceTargets(null, "fallback"), result)
        assertEquals(listOf(2.0), search.crosshairRanges)
        assertEquals(listOf(36.0), search.fallbackRanges)
    }

    @Test
    fun `crosshair target found during lag keeps the tracked render target`() {
        val search = RecordingSearch(crosshairTarget = "crosshair", fallbackTarget = "fallback")
        val selector = VelocityReduceTargetSelector(search)

        val result = selector.select(
            current = VelocityReduceTargets(attackTarget = null, renderTarget = "tracked"),
            canLag = true,
            lagging = true,
            killAuraTarget = null,
            lagRange = 2f..6f,
            interactionRange = 3.5,
        )

        assertEquals(VelocityReduceTargets("crosshair", "tracked"), result)
        assertTrue(search.fallbackRanges.isEmpty())
    }

    @Test
    fun `crosshair search uses interaction range when lag is unavailable`() {
        val search = RecordingSearch(crosshairTarget = "crosshair")
        val selector = VelocityReduceTargetSelector(search)

        selector.select(
            current = VelocityReduceTargets(attackTarget = null, renderTarget = null),
            canLag = false,
            lagging = false,
            killAuraTarget = null,
            lagRange = 2f..6f,
            interactionRange = 3.5,
        )

        assertEquals(listOf(3.5), search.crosshairRanges)
    }

    private class RecordingSearch(
        private val killAuraTargetWithinRange: Boolean = false,
        private val crosshairTarget: String? = null,
        private val fallbackTarget: String? = null,
    ) : VelocityReduceTargetSearch<String> {
        var checkedKillAuraRange = false
            private set
        val crosshairRanges = mutableListOf<Double>()
        val fallbackRanges = mutableListOf<Double>()

        override fun isWithinLagStart(target: String, maxSquaredDistance: Float): Boolean {
            checkedKillAuraRange = true
            return killAuraTargetWithinRange
        }

        override fun findCrosshair(maxRange: Double): String? {
            crosshairRanges += maxRange
            return crosshairTarget
        }

        override fun findFallback(maxSquaredDistance: Double): String? {
            fallbackRanges += maxSquaredDistance
            return fallbackTarget
        }
    }
}
