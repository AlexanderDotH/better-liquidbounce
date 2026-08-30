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

package net.ccbluex.liquidbounce.utils.inventory

import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.inventory.ContainerInput

internal object ContainerClickExecutor {

    fun perform(action: InventoryAction.Click): Boolean {
        val slotId = action.slot.getIdForServer(action.screen) ?: return false
        interaction.handleContainerInput(
            action.screen?.syncId ?: 0,
            slotId,
            action.button,
            action.actionType,
            player,
        )
        InventoryRuntimeHooks.recordClickedSlot(slotId)
        return true
    }

    fun performMiss(action: InventoryAction.Click): Boolean {
        val slot = action.slot as? ContainerItemSlot ?: return false
        val screen = action.screen ?: return false
        val closestEmptySlot = screen.getSlotsInContainer()
            .filter { it.itemStack.isEmpty }
            .minByOrNull(slot::distance) ?: return false

        val slotId = closestEmptySlot.getIdForServer(screen)
        interaction.handleContainerInput(screen.syncId, slotId, 0, ContainerInput.PICKUP, player)
        InventoryRuntimeHooks.recordClickedSlot(slotId)
        return true
    }
}
