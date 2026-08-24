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
package net.ccbluex.liquidbounce.features.module.modules.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MaceKillTargetSelectionTest {

    @Test
    fun `nearest viable target wins over a distant target closer to the crosshair`() {
        val nearby = Any()
        val distantAligned = Any()

        val selected = selectMaceKillCombatTarget(
            candidates = listOf(
                MaceKillCombatTargetCandidate(nearby, distance = 8.0, crosshairAngle = 35f),
                MaceKillCombatTargetCandidate(distantAligned, distance = 120.0, crosshairAngle = 1f),
            ),
            retainedTarget = null,
            hasAttackEndpoint = { true },
        )

        assertEquals(nearby, selected)
    }

    @Test
    fun `selection skips a boxed target without an attack endpoint`() {
        val boxed = Any()
        val reachable = Any()

        val selected = selectMaceKillCombatTarget(
            candidates = listOf(
                MaceKillCombatTargetCandidate(boxed, distance = 6.0, crosshairAngle = 2f),
                MaceKillCombatTargetCandidate(reachable, distance = 10.0, crosshairAngle = 15f),
            ),
            retainedTarget = null,
            hasAttackEndpoint = { it === reachable },
        )

        assertEquals(reachable, selected)
    }

    @Test
    fun `selection retains a viable target across small distance changes`() {
        val retained = Any()
        val slightlyCloser = Any()

        val selected = selectMaceKillCombatTarget(
            candidates = listOf(
                MaceKillCombatTargetCandidate(slightlyCloser, distance = 9.0, crosshairAngle = 2f),
                MaceKillCombatTargetCandidate(retained, distance = 10.0, crosshairAngle = 15f),
            ),
            retainedTarget = retained,
            hasAttackEndpoint = { true },
        )

        assertEquals(retained, selected)
    }

    @Test
    fun `selection switches when another viable target is meaningfully closer`() {
        val retained = Any()
        val muchCloser = Any()

        val selected = selectMaceKillCombatTarget(
            candidates = listOf(
                MaceKillCombatTargetCandidate(muchCloser, distance = 5.0, crosshairAngle = 30f),
                MaceKillCombatTargetCandidate(retained, distance = 10.0, crosshairAngle = 1f),
            ),
            retainedTarget = retained,
            hasAttackEndpoint = { true },
        )

        assertEquals(muchCloser, selected)
    }
}
