/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FightBotNamedTargetCompatibilityTest {

    @Test
    fun `configured opponent wins over a closer eligible player`() {
        val candidate = selectConfiguredFightBotTarget(
            username = "MainClient",
            candidates = listOf("NearbyPlayer", "MainClient", "AnotherPlayer"),
            usernameOf = { it },
            isEligible = { true },
        )

        assertEquals("MainClient", candidate)
    }

    @Test
    fun `configured opponent matching ignores case and surrounding whitespace`() {
        val candidate = selectConfiguredFightBotTarget(
            username = "  mainclient  ",
            candidates = listOf("MAINCLIENT"),
            usernameOf = { it },
            isEligible = { true },
        )

        assertEquals("MAINCLIENT", candidate)
    }

    @Test
    fun `blank configured opponent never falls back to an arbitrary player`() {
        val candidate = selectConfiguredFightBotTarget(
            username = "   ",
            candidates = listOf("MainClient"),
            usernameOf = { it },
            isEligible = { true },
        )

        assertNull(candidate)
    }

    @Test
    fun `matching but rejected opponent is not replaced by another player`() {
        val candidate = selectConfiguredFightBotTarget(
            username = "MainClient",
            candidates = listOf("MainClient", "NearbyPlayer"),
            usernameOf = { it },
            isEligible = { it != "MainClient" },
        )

        assertNull(candidate)
    }

    @Test
    fun `configured opponent is retained when a different entity enters the crosshair`() {
        val target = selectKillAuraTargetForFightBot(
            handoff = FightBotHandoffState.Locked,
            lockedTarget = "MainClient",
            trackedTarget = "MainClient",
            crosshairTarget = "Bystander",
        )

        assertEquals("MainClient", target)
    }
}
