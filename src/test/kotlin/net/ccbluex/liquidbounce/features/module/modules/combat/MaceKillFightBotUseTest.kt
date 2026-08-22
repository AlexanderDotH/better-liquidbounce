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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillFightBotUseTest {

    @Test
    fun `held mace is preferred without acquiring a silent hotbar lease`() {
        assertEquals(
            FightBotMaceUseSource.MainHand,
            selectFightBotMaceUseSource(
                automation = FightBotMaceAutomation.HeldOrHotbar,
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
            selectFightBotMaceUseSource(
                automation = FightBotMaceAutomation.HeldOrHotbar,
                mainHandMace = false,
                selectedHotbarSlot = 6,
                hotbarMaceSlots = listOf(1, 5, 8),
            ),
        )
        assertNull(
            selectFightBotMaceUseSource(
                automation = FightBotMaceAutomation.HeldMace,
                mainHandMace = false,
                selectedHotbarSlot = 6,
                hotbarMaceSlots = listOf(5),
            ),
        )
    }

    @Test
    fun `Off never selects or silently swaps to a mace`() {
        assertNull(
            selectFightBotMaceUseSource(
                automation = FightBotMaceAutomation.Off,
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
