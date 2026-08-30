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
package net.ccbluex.liquidbounce.features.module.modules.player.fastuse

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.opposite
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items

internal class FastUseCrossbow(parent: EventListener) : ToggleableValueGroup(parent, "Crossbow", false) {

    private val tickCooldown by int("TickCooldown", 1, 1..20)

    @Suppress("unused")
    val tickHandler = handler<GameTickEvent> {
        if (player.isUsingItem && player.activeItem.`is`(Items.CROSSBOW) && player.tickCount % tickCooldown == 0) {
            val hand = player.usedItemHand
            val containerId = player.inventoryMenu.containerId
            val slot = player.inventory.selectedSlot + Inventory.INVENTORY_SIZE

            interaction.handleContainerInput(
                containerId,
                slot,
                Inventory.SLOT_OFFHAND,
                ContainerInput.SWAP,
                player,
            )

            interaction.useItem(player, hand.opposite)

            interaction.handleContainerInput(
                containerId,
                slot,
                Inventory.SLOT_OFFHAND,
                ContainerInput.SWAP,
                player,
            )

            interaction.useItem(player, hand)
        }
    }
}
