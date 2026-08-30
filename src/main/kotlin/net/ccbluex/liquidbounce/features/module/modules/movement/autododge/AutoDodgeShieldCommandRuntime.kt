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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldCommand
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldState
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.features.inventory.OffhandReservationManager
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.features.network.releaseUsingItemInTickLoop
import net.minecraft.world.item.ItemStack

internal class AutoDodgeShieldCommandRuntime(
    private val equipment: AutoDodgeShieldEquipment,
    private val warn: (String) -> Unit,
) : MinecraftShortcuts {

    private var pendingInventoryCommand: SpearShieldCommand<ItemStack>? = null
    private var ownsOffhandReservation = false

    fun execute(command: SpearShieldCommand<ItemStack>): Boolean = when (command) {
        SpearShieldCommand.ReserveOffhand -> reserveOffhand()
        SpearShieldCommand.ReleaseItemUse -> releaseItemUse()
        is SpearShieldCommand.SwapIntoOffhand -> queueInventory(command)
        is SpearShieldCommand.StartShieldUse -> {
            equipment.startShieldUse(command.hand)
            true
        }
        SpearShieldCommand.StopShieldUse -> releaseItemUse()
        is SpearShieldCommand.RestoreOffhand -> queueInventory(command)
        SpearShieldCommand.ReleaseOffhandReservation -> {
            releaseOffhandReservation()
            true
        }
    }

    private fun releaseItemUse(): Boolean {
        interaction.releaseUsingItemInTickLoop()
        return true
    }

    private fun queueInventory(command: SpearShieldCommand<ItemStack>): Boolean {
        pendingInventoryCommand = command
        return true
    }

    fun schedulePending(state: SpearShieldState<ItemStack>, event: ScheduleInventoryActionEvent) {
        val command = pendingInventoryCommand ?: return
        val snapshot = when (command) {
            is SpearShieldCommand.SwapIntoOffhand -> command.snapshot
            is SpearShieldCommand.RestoreOffhand -> command.snapshot
            else -> return
        }
        if (!canScheduleSpearShieldInventoryCommand(
                command,
                equipment.inventoryLayout(state),
                OffhandReservationManager.isReservedBy(ModuleAutoDodge),
            )) {
            return
        }
        val sourceSlot = equipment.findSnapshotSourceSlot(snapshot.sourceSlot) ?: return
        event.schedule(
            Spear.Shield.constraints,
            InventoryAction.Click.performSwap(from = sourceSlot, to = HotbarItemSlot.OFFHAND),
            priority = Priority.IMPORTANT_FOR_USER_SAFETY,
        )
        pendingInventoryCommand = null
    }

    fun clearPendingWhenTerminal(state: SpearShieldState<ItemStack>) {
        if (state is SpearShieldState.Idle || state is SpearShieldState.Aborted) {
            pendingInventoryCommand = null
        }
    }

    fun renewOrRelease(state: SpearShieldState<ItemStack>, stateName: String) {
        if (!state.needsOffhandReservation()) {
            releaseOffhandReservation()
            return
        }
        if (!reserveOffhand()) {
            warn("Lost AutoDodge spear shield offhand reservation during $stateName")
        }
    }

    fun reset() {
        pendingInventoryCommand = null
        releaseOffhandReservation()
    }

    fun reservationName(): String = when {
        OffhandReservationManager.isReservedBy(ModuleAutoDodge) -> "AutoDodge"
        OffhandReservationManager.isReserved -> "Other"
        else -> "-"
    }

    private fun reserveOffhand(): Boolean {
        val reserved = OffhandReservationManager.reserve(
            ModuleAutoDodge,
            Priority.IMPORTANT_FOR_USER_SAFETY,
        )
        ownsOffhandReservation = reserved
        return reserved
    }

    private fun releaseOffhandReservation() {
        if (ownsOffhandReservation) OffhandReservationManager.release(ModuleAutoDodge)
        ownsOffhandReservation = false
    }
}
