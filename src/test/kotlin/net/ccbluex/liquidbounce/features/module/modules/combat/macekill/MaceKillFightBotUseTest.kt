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
import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.FightBotRemoteWeapon
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.FightBotSpearUseSource
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.selectFightBotRemoteWeapon
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.selectFightBotRouteTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.*
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillFightBotUseTest {

    @Test
    fun `held mace is preferred without acquiring a silent hotbar lease`() {
        assertEquals(
            FightBotMaceUseSource.MainHand,
            selectMaceUseSource(
                policy = MaceUsePolicy.HeldOrHotbar,
                mainHandMace = true,
                selectedHotbarSlot = 4,
                hotbarMaceSlots = listOf(3, 5),
            ),
        )
    }

    @Test
    fun `HeldOrHotbar chooses the nearest hotbar mace only when no mace is held`() {
        assertEquals(
            FightBotMaceUseSource.Hotbar(5),
            selectMaceUseSource(
                policy = MaceUsePolicy.HeldOrHotbar,
                mainHandMace = false,
                selectedHotbarSlot = 6,
                hotbarMaceSlots = listOf(1, 5, 8),
            ),
        )
        assertNull(
            selectMaceUseSource(
                policy = MaceUsePolicy.HeldMace,
                mainHandMace = false,
                selectedHotbarSlot = 6,
                hotbarMaceSlots = listOf(5),
            ),
        )
    }

    @Test
    fun `Off never selects or silently swaps to a mace`() {
        assertNull(
            selectMaceUseSource(
                policy = MaceUsePolicy.Off,
                mainHandMace = true,
                selectedHotbarSlot = 0,
                hotbarMaceSlots = listOf(0),
            ),
        )
    }

    @Test
    fun `an active route is retained until its terminal cleanup`() {
        assertEquals(
            FightBotRemoteWeapon.Mace,
            selectFightBotRemoteWeapon(
                maceSource = null,
                spearSource = FightBotSpearUseSource.MainHand,
                maceRouteActive = true,
                spearRouteActive = false,
            ),
        )
        assertEquals(
            FightBotRemoteWeapon.Spear,
            selectFightBotRemoteWeapon(
                maceSource = FightBotMaceUseSource.MainHand,
                spearSource = null,
                maceRouteActive = false,
                spearRouteActive = true,
            ),
        )
    }

    @Test
    fun `target handoff follows the active mace route and otherwise preserves spear`() {
        assertEquals(
            "MaceTarget",
            selectFightBotRouteTarget(maceRouteTarget = "MaceTarget", spearRouteTarget = "SpearTarget"),
        )
        assertEquals(
            "SpearTarget",
            selectFightBotRouteTarget(maceRouteTarget = null, spearRouteTarget = "SpearTarget"),
        )
        assertNull(selectFightBotRouteTarget<String>(maceRouteTarget = null, spearRouteTarget = null))
    }

    @Test
    fun `a valid held weapon wins over the other weapons hotbar route`() {
        assertEquals(
            FightBotRemoteWeapon.Spear,
            selectFightBotRemoteWeapon(
                maceSource = FightBotMaceUseSource.Hotbar(2),
                spearSource = FightBotSpearUseSource.Offhand,
            ),
        )
        assertEquals(
            FightBotRemoteWeapon.Mace,
            selectFightBotRemoteWeapon(
                maceSource = FightBotMaceUseSource.MainHand,
                spearSource = FightBotSpearUseSource.Hotbar(2),
            ),
        )
    }

    @Test
    fun `mace wins only the no-held-weapon hotbar tie`() {
        assertEquals(
            FightBotRemoteWeapon.Mace,
            selectFightBotRemoteWeapon(
                maceSource = FightBotMaceUseSource.Hotbar(7),
                spearSource = FightBotSpearUseSource.Hotbar(1),
            ),
        )
        assertEquals(
            FightBotRemoteWeapon.Spear,
            selectFightBotRemoteWeapon(
                maceSource = null,
                spearSource = FightBotSpearUseSource.Hotbar(1),
            ),
        )
    }

    @Test
    fun `only ready and active MaceKill states reserve KillAura subsystems`() {
        assertFalse(MaceKillFightBotState.Unavailable.reservesKillAuraSubsystems)
        assertTrue(MaceKillFightBotState.Ready.reservesKillAuraSubsystems)
        assertTrue(MaceKillFightBotState.RouteActive.reservesKillAuraSubsystems)
        assertFalse(MaceKillFightBotState.Rejected.reservesKillAuraSubsystems)
    }

    @Test
    fun `route rejection remains sticky until FightBot releases the target`() {
        assertFalse(MaceKillFightBotState.Unavailable.retainsRejectedTarget)
        assertFalse(MaceKillFightBotState.Ready.retainsRejectedTarget)
        assertFalse(MaceKillFightBotState.RouteActive.retainsRejectedTarget)
        assertTrue(MaceKillFightBotState.Rejected.retainsRejectedTarget)
    }

    @Test
    fun `every terminal path releases only an owned mace hotbar lease`() {
        MaceKillFightBotTerminal.entries.forEach { terminal ->
            val cleanup = fightBotMaceCleanup(
                terminal = terminal,
                source = FightBotMaceUseSource.Hotbar(4),
            )

            assertEquals(terminal, cleanup.terminal)
            assertTrue(cleanup.resetSilentSlot, terminal.name)
        }

        val heldCleanup = fightBotMaceCleanup(
            terminal = MaceKillFightBotTerminal.TargetLoss,
            source = FightBotMaceUseSource.MainHand,
        )
        assertFalse(heldCleanup.resetSilentSlot)
    }
}
