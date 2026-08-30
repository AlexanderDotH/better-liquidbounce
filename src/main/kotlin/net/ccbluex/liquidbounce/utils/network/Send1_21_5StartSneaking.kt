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


@file:JvmName("NetworkUtilsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.network

import net.ccbluex.liquidbounce.common.chat.ClientChatOutput
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.usesViaFabricPlus
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.shouldSwingHand
import net.ccbluex.liquidbounce.utils.inventory.InventoryRuntimeHooks
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.BlockHitResult

fun ClientCommonPacketListenerImpl.sendLegacyStartSneaking() {
    if (!usesViaFabricPlus) return

    sendPacket(PlayerSneakPacket.START)
}

fun ClientCommonPacketListenerImpl.sendLegacyStopSneaking() {
    if (!usesViaFabricPlus) return

    sendPacket(PlayerSneakPacket.STOP)
}

/**
 * Sends an open inventory packet with the help of ViaFabricPlus. This is only for older versions. (<= 1.11.2)
 */
fun ClientCommonPacketListenerImpl.sendLegacyOpenInventory() {
    if (InventoryRuntimeHooks.isInventoryOpenServerSide || !usesViaFabricPlus) {
        return
    }

    sendPacket(
        OpenInventorySilentlyPacket,
        onSuccess = { InventoryRuntimeHooks.setInventoryOpenServerSide(true) },
        onFailure = {
            ClientChatOutput.publish(markAsError("Failed to open inventory using ViaFabricPlus, report to developers!"))
        }
    )
}

fun ClientCommonPacketListenerImpl.sendStartSprinting() {
    send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING))
}

fun ClientCommonPacketListenerImpl.sendStopSprinting() {
    send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING))
}

fun ClientCommonPacketListenerImpl.sendSwapItemWithOffhand() {
    send(
        ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ZERO,
            Direction.DOWN,
        )
    )
}

fun ClientCommonPacketListenerImpl.sendHeldItemChange(slot: Int) {
    send(ServerboundSetCarriedItemPacket(slot))
}

fun ClientCommonPacketListenerImpl.sendCloseInventory() {
    send(ServerboundContainerClosePacket(0))
}

fun ClientPacketListener.sendChatOrCommand(message: String) =
    if (message.startsWith('/')) {
        sendCommand(message.substring(1))
    } else {
        sendChat(message)
    }

fun LocalPlayer.clickBlockWithSlot(
    rayTraceResult: BlockHitResult,
    slot: Int,
    swingMode: SwingMode,
    switchPolicy: BlockSlotSwitchPolicy = BlockSlotSwitchPolicy.RESTORE_AFTER_USE,
    sequenced: Boolean = true,
) {
    val hand = if (slot == Inventory.SLOT_OFFHAND) {
        InteractionHand.OFF_HAND
    } else {
        InteractionHand.MAIN_HAND
    }

    val prevHotbarSlot = this.inventory.selectedSlot
    val slotChanged = slot != prevHotbarSlot
    if (!selectBlockSlotForUse(slot, hand, switchPolicy)) return

    if (sequenced) {
        interaction.startPrediction(world) { sequence ->
            ServerboundUseItemOnPacket(hand, rayTraceResult, sequence)
        }
    } else {
        connection.send(ServerboundUseItemOnPacket(hand, rayTraceResult, 0))
    }

    val itemUsageContext = UseOnContext(this, hand, rayTraceResult)
    val actionResult = useItemOnBlock(slot, itemUsageContext)

    if (actionResult.shouldSwingHand()) {
        swingMode.swing(hand)
    }

    if (switchPolicy.shouldRestoreServerSlot(slotChanged, hand == InteractionHand.MAIN_HAND)) {
        connection.sendHeldItemChange(prevHotbarSlot)
    }

    this.inventory.selectedSlot = prevHotbarSlot
}

private fun LocalPlayer.selectBlockSlotForUse(
    slot: Int,
    hand: InteractionHand,
    switchPolicy: BlockSlotSwitchPolicy,
): Boolean {
    if (hand != InteractionHand.MAIN_HAND) return true
    val slotChanged = slot != this.inventory.selectedSlot
    if (!switchPolicy.canUseSlot(slotChanged)) return false

    this.inventory.selectedSlot = slot
    if (slotChanged) connection.sendHeldItemChange(slot)
    return true
}

private fun LocalPlayer.useItemOnBlock(
    slot: Int,
    itemUsageContext: UseOnContext,
): InteractionResult {
    val itemStack = this.inventory.getItem(slot)
    if (this.isCreative) {
        val count = itemStack.count
        val actionResult = itemStack.useOn(itemUsageContext)
        itemStack.count = count
        return actionResult
    }
    return itemStack.useOn(itemUsageContext)
}
