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
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura

import net.ccbluex.liquidbounce.utils.network.BlockSlotSwitchPolicy
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SwitchModePolicyTest {

    @Test
    fun `switch modes preserve tags order and neutral packet policies`() {
        assertEquals(listOf("Silent", "Normal", "None"), SwitchMode.entries.map(SwitchMode::tag))
        assertEquals(BlockSlotSwitchPolicy.RESTORE_AFTER_USE, SwitchMode.SILENT.slotSwitchPolicy)
        assertEquals(BlockSlotSwitchPolicy.KEEP_SELECTED, SwitchMode.NORMAL.slotSwitchPolicy)
        assertEquals(BlockSlotSwitchPolicy.REQUIRE_SELECTED, SwitchMode.NONE.slotSwitchPolicy)
    }
}
