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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill



import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MaceKillTargetSelectionTest {

    @Test
    fun `active KillAura delegation never evaluates the configured local selector`() {
        var localSelectorEvaluated = false

        val selected = selectMaceKillDelegatedTarget(
            killAuraAuthoritative = true,
            killAuraTarget = null,
            localTarget = {
                localSelectorEvaluated = true
                "cursor-target"
            },
        )

        assertNull(selected)
        assertFalse(localSelectorEvaluated)
    }

    @Test
    fun `configured selector remains available while KillAura delegation is inactive`() {
        val selected = selectMaceKillDelegatedTarget(
            killAuraAuthoritative = false,
            killAuraTarget = "kill-aura-target",
            localTarget = { "combat-target" },
        )

        assertEquals("combat-target", selected)
    }

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
