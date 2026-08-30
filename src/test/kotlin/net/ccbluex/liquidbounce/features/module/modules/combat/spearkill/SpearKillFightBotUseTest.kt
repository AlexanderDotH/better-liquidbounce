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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillFightBotUseTest {

    @Test
    fun `held spear is preferred over every hotbar spear`() {
        assertEquals(
            FightBotSpearUseSource.MainHand,
            selectFightBotSpearUseSource(
                automation = FightBotSpearAutomation.HeldOrHotbar,
                mainHandSpear = true,
                offhandSpear = true,
                selectedHotbarSlot = 4,
                hotbarSpearSlots = listOf(3, 5),
            ),
        )
    }

    @Test
    fun `HeldOrHotbar chooses the nearest hotbar spear only when neither hand holds one`() {
        assertEquals(
            FightBotSpearUseSource.Hotbar(5),
            selectFightBotSpearUseSource(
                automation = FightBotSpearAutomation.HeldOrHotbar,
                mainHandSpear = false,
                offhandSpear = false,
                selectedHotbarSlot = 6,
                hotbarSpearSlots = listOf(1, 5, 8),
            ),
        )
        assertNull(
            selectFightBotSpearUseSource(
                automation = FightBotSpearAutomation.HeldSpear,
                mainHandSpear = false,
                offhandSpear = false,
                selectedHotbarSlot = 6,
                hotbarSpearSlots = listOf(5),
            ),
        )
    }

    @Test
    fun `Off never selects or uses a spear`() {
        assertNull(
            selectFightBotSpearUseSource(
                automation = FightBotSpearAutomation.Off,
                mainHandSpear = true,
                offhandSpear = true,
                selectedHotbarSlot = 0,
                hotbarSpearSlots = listOf(0),
            ),
        )
    }

    @Test
    fun `KillAura subsystems are reserved only while charging or routing`() {
        assertFalse(SpearKillFightBotState.Unavailable.reservesKillAuraSubsystems)
        assertTrue(SpearKillFightBotState.Charging.reservesKillAuraSubsystems)
        assertTrue(SpearKillFightBotState.RouteActive.reservesKillAuraSubsystems)
        assertFalse(SpearKillFightBotState.Rejected.reservesKillAuraSubsystems)
    }

    @Test
    fun `route rejection remains sticky until FightBot releases the target`() {
        assertFalse(SpearKillFightBotState.Unavailable.retainsRejectedTarget)
        assertFalse(SpearKillFightBotState.Charging.retainsRejectedTarget)
        assertFalse(SpearKillFightBotState.RouteActive.retainsRejectedTarget)
        assertTrue(SpearKillFightBotState.Rejected.retainsRejectedTarget)
    }

    @Test
    fun `every terminal path releases only resources owned by the FightBot lease`() {
        SpearKillFightBotTerminal.entries.forEach { terminal ->
            val cleanup = fightBotSpearCleanup(
                terminal = terminal,
                startedUse = true,
                selectedSilentSlot = true,
            )

            assertEquals(terminal, cleanup.terminal)
            assertTrue(cleanup.stopUse, terminal.name)
            assertTrue(cleanup.resetSilentSlot, terminal.name)
        }

        val borrowedUse = fightBotSpearCleanup(
            terminal = SpearKillFightBotTerminal.TargetLoss,
            startedUse = false,
            selectedSilentSlot = false,
        )
        assertFalse(borrowedUse.stopUse)
        assertFalse(borrowedUse.resetSilentSlot)
    }

    @Test
    fun `cleanup never releases an unrelated item use`() {
        assertTrue(
            shouldStopFightBotSpearUse(
                startedUse = true,
                isUsingItem = true,
                isSameHand = true,
                isUsingSpear = true,
            ),
        )
        assertFalse(
            shouldStopFightBotSpearUse(
                startedUse = true,
                isUsingItem = true,
                isSameHand = true,
                isUsingSpear = false,
            ),
        )
        assertFalse(
            shouldStopFightBotSpearUse(
                startedUse = true,
                isUsingItem = true,
                isSameHand = false,
                isUsingSpear = true,
            ),
        )
    }
}
