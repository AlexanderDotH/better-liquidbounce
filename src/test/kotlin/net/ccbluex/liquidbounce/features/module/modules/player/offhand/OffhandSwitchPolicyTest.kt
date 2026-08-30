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
package net.ccbluex.liquidbounce.features.module.modules.player.offhand

import net.ccbluex.liquidbounce.utils.inventory.ContainerItemSlot
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.minecraft.world.inventory.ContainerInput
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OffhandSwitchPolicyTest {

    @Test
    fun `switch modes retain their persisted tags and order`() {
        assertEquals(
            listOf("Smart", "Switch", "PickUp", "Automatic"),
            HandSwitchMode.entries.map(HandSwitchMode::tag),
        )
    }

    @Test
    fun `pickup planning retains cursor cleanup and action ordering`() {
        val source = ContainerItemSlot(7)

        val emptyOffhand = PickupSwitchPlanner.plan(source, offhandOccupied = false)
        val occupiedOffhand = PickupSwitchPlanner.plan(source, offhandOccupied = true)

        assertEquals(List(2) { ContainerInput.PICKUP }, emptyOffhand.map { it.actionType })
        assertTrue(emptyOffhand.all { it.button == 0 && it.screen == null })
        assertSame(source, emptyOffhand[0].slot)
        assertSame(HotbarItemSlot.OFFHAND, emptyOffhand[1].slot)
        assertEquals(List(3) { ContainerInput.PICKUP }, occupiedOffhand.map { it.actionType })
        assertTrue(occupiedOffhand.all { it.button == 0 && it.screen == null })
        assertSame(source, occupiedOffhand[0].slot)
        assertSame(HotbarItemSlot.OFFHAND, occupiedOffhand[1].slot)
        assertSame(source, occupiedOffhand[2].slot)
    }

    @Test
    fun `module retains setting mode and scheduling order`() {
        assertOrdered(
            moduleSource,
            "treeAll(",
            "Totem,",
            "Crystal,",
            "Gapple,",
            "Strength,",
            "Block,",
        )
        assertTrue("handler<ScheduleInventoryActionEvent>(priority = 100)" in moduleSource)
        assertOrdered(
            equipmentModeSource,
            "TOTEM(",
            "STRENGTH(",
            "GAPPLE(",
            "CRYSTAL(",
            "BLOCK(",
            "BACK(",
            "NONE(",
        )
        assertOrdered(
            moduleSource,
            "canScheduleInventoryActions()",
            "EquipmentMode.entries.firstOrNull(EquipmentMode::shouldEquip)",
            "EventManager.callEvent(RefreshArrayListEvent)",
            "Totem.switchBack.hasElapsed",
            "chronometer.hasElapsed(activeMode.getDelay()",
            "switchMode.performSwitch(slot)",
            "Totem.send(actions)",
            "it.schedule(inventoryConstraints, actions)",
        )
        assertEquals(2, Regex("chronometer\\.reset\\(\\)").findAll(moduleSource).count())
    }

    @Test
    fun `gapple sword policy addresses the nested setting through its classifier`() {
        assertOrdered(
            equipmentModeSource,
            "val gapple = ModuleOffhand.Gapple",
            "val whileHoldingSword = ModuleOffhand.Gapple.WhileHoldingSword",
            "if (!gapple.enabled)",
            "player.mainHandItem.isSword && whileHoldingSword.enabled",
            "!whileHoldingSword.onlyWhileKa || ModuleKillAura.running",
        )
    }

    private fun assertOrdered(source: String, vararg fragments: String) {
        val positions = fragments.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing offhand contract fragment")
        assertEquals(positions.sorted(), positions, "Offhand contract order changed")
    }

    private companion object {
        val moduleSource: String = Files.readString(
            Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/offhand/ModuleOffhand.kt")
        )
        val equipmentModeSource: String = Files.readString(
            Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/offhand/EquipmentMode.kt")
        )
    }
}
