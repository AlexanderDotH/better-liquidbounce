/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.integration.inventory

import net.ccbluex.liquidbounce.common.interop.PlayerInventoryDataPayload
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

@JvmRecord
internal data class TrackedPlayerInventoryData(
    val armor: List<ItemStack>,
    val main: List<ItemStack>,
    val crafting: List<ItemStack>,
    val enderChest: List<ItemStack>,
) : PlayerInventoryDataPayload {

    companion object {
        fun fromPlayer(player: Player, enderChest: List<ItemStack>) = TrackedPlayerInventoryData(
            armor = listOf(
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD,
            ).map { player.getItemBySlot(it).copy() },
            main = player.inventory.nonEquipmentItems.map(ItemStack::copy),
            crafting = player.inventoryMenu.craftSlots.items.map(ItemStack::copy),
            enderChest = enderChest,
        )
    }
}
