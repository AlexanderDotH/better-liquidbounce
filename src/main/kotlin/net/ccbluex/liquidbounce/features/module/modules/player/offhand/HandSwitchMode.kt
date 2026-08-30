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

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.client.isNewerThanOrEquals1_16
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.network.sendHeldItemChange
import net.ccbluex.liquidbounce.utils.network.sendSwapItemWithOffhand

internal enum class HandSwitchMode(override val tag: String) : Tagged {
    /**
     * Pickup, but it performs a SWAP_ITEM_WITH_OFFHAND action whenever possible to send fewer packets.
     * Works on all versions.
     */
    SMART("Smart") {
        override fun performSwitch(from: ItemSlot) = HandSwitchExecutor.performSmart(from)
    },

    /**
     * Performs a switch action, works on 1.16. The best method on newer servers.
     */
    SWITCH("Switch") {
        override fun performSwitch(from: ItemSlot) = listOf(
            InventoryAction.Click.performSwap(from = from, to = HotbarItemSlot.OFFHAND)
        )
    },

    /**
     * Performs 2-3 pickup actions. Works on all versions.
     */
    PICKUP("PickUp") {
        override fun performSwitch(from: ItemSlot) = HandSwitchExecutor.performPickup(from)
    },

    /**
     * Chooses the switch action based on the version. Only works if ViaFabricPlus is installed.
     */
    AUTOMATIC("Automatic") {
        override fun performSwitch(from: ItemSlot): List<InventoryAction.Click> =
            if (isNewerThanOrEquals1_16) SWITCH.performSwitch(from) else PICKUP.performSwitch(from)
    };

    abstract fun performSwitch(from: ItemSlot): List<InventoryAction.Click>
}

internal object PickupSwitchPlanner {
    fun plan(from: ItemSlot, offhandOccupied: Boolean): List<InventoryAction.Click> = buildList(3) {
        this += InventoryAction.Click.performPickup(slot = from)
        this += InventoryAction.Click.performPickup(slot = HotbarItemSlot.OFFHAND)
        if (offhandOccupied) {
            this += InventoryAction.Click.performPickup(slot = from)
        }
    }
}

private object HandSwitchExecutor : MinecraftShortcuts {
    fun performSmart(from: ItemSlot): List<InventoryAction.Click> {
        if (from !is HotbarItemSlot) {
            return performPickup(from)
        }

        val selectedSlot = player.inventory.selectedSlot
        val targetSlot = from.inventorySlot
        if (selectedSlot != targetSlot) {
            network.sendHeldItemChange(targetSlot)
        }
        network.sendSwapItemWithOffhand()
        if (selectedSlot != targetSlot) {
            network.sendHeldItemChange(selectedSlot)
        }
        return emptyList()
    }

    fun performPickup(from: ItemSlot): List<InventoryAction.Click> =
        PickupSwitchPlanner.plan(from, offhandOccupied = !player.offhandItem.isEmpty)
}
