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
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FightBotRemoteWeaponContractTest {

    @Test
    fun `mace automation requires an allowed source and chooses the nearest hotbar slot`() {
        assertNull(selectFightBotMaceUseSource(FightBotMaceAutomation.Off, true, 4, listOf(3)))
        assertNull(selectFightBotMaceUseSource(FightBotMaceAutomation.HeldMace, false, 4, listOf(3)))
        assertEquals(
            FightBotMaceUseSource.MainHand,
            selectFightBotMaceUseSource(FightBotMaceAutomation.HeldMace, true, 4, emptyList()),
        )
        assertEquals(
            FightBotMaceUseSource.Hotbar(6),
            selectFightBotMaceUseSource(FightBotMaceAutomation.HeldOrHotbar, false, 7, listOf(1, 6)),
        )
    }

    @Test
    fun `remote weapon selection retains routes and otherwise prefers held weapons`() {
        val localHotbarMace: FightBotMaceUseSource? = FightBotMaceUseSource.Hotbar(2)

        assertEquals(
            FightBotRemoteWeapon.Mace,
            selectFightBotRemoteWeapon(
                maceSource = null,
                spearSource = FightBotSpearUseSource.MainHand,
                maceRouteActive = true,
            ),
        )
        assertEquals(
            FightBotRemoteWeapon.Spear,
            selectFightBotRemoteWeapon(
                maceSource = localHotbarMace,
                spearSource = FightBotSpearUseSource.Offhand,
            ),
        )
        assertEquals(
            FightBotRemoteWeapon.Mace,
            selectFightBotRemoteWeapon(
                maceSource = localHotbarMace,
                spearSource = FightBotSpearUseSource.Hotbar(1),
            ),
        )
    }
}
