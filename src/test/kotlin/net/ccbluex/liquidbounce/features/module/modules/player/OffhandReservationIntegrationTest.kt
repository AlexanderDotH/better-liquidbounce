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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.ModuleInventoryCleaner
import net.ccbluex.liquidbounce.features.module.modules.player.offhand.ModuleOffhand
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.features.inventory.OffhandReservationManager
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OffhandReservationIntegrationTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @BeforeTest
    @AfterTest
    fun resetReservation() {
        OffhandReservationManager.clear()
    }

    @Test
    fun `Offhand yields its scheduler to another reservation owner`() {
        val emergencyOwner = Any()

        assertTrue(ModuleOffhand.canScheduleInventoryActions())
        assertTrue(OffhandReservationManager.reserve(emergencyOwner, Priority.IMPORTANT_FOR_USER_SAFETY))

        assertFalse(ModuleOffhand.canScheduleInventoryActions())
    }

    @Test
    fun `InventoryCleaner protects a reserved offhand from removal and filling`() {
        assertTrue(OffhandReservationManager.reserve(Any(), Priority.IMPORTANT_FOR_USER_SAFETY))

        val template = ModuleInventoryCleaner.cleanupTemplateFromSettings

        assertTrue(HotbarItemSlot.OFFHAND in template.forbiddenSlots)
        assertTrue(HotbarItemSlot.OFFHAND in template.forbiddenSlotsToFill)
    }
}
