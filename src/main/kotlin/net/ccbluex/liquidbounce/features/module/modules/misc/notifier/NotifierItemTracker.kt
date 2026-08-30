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
package net.ccbluex.liquidbounce.features.module.modules.misc.notifier

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.ccbluex.liquidbounce.utils.item.isConsumable
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import java.util.UUID

internal class NotifierItemTracker {

    private val itemConsumptionCache = Object2ObjectOpenHashMap<UUID, ItemConsumptionState>()
    private val heldItemCache = Object2ObjectOpenHashMap<UUID, HeldItemState>()
    private val observedPlayers = ObjectOpenHashSet<UUID>()

    fun clear() {
        itemConsumptionCache.clear()
        heldItemCache.clear()
        observedPlayers.clear()
    }

    fun update(
        players: Iterable<Player>,
        isBot: (Player) -> Boolean,
        trackConsumption: Boolean,
        consumptionFormat: String,
        trackHeldItems: Boolean,
        heldItemFormat: String,
        trackedItems: Set<Item>,
        emit: (String) -> Unit,
    ) {
        observedPlayers.clear()
        players.filterIsInstance<RemotePlayer>().filterNot(isBot).forEach { player ->
            observedPlayers += player.uuid
            if (trackConsumption) handleItemConsumption(player, consumptionFormat, emit)
            if (trackHeldItems) handleHeldItems(player, heldItemFormat, trackedItems, emit)
        }
        retainObserved(trackConsumption, itemConsumptionCache)
        retainObserved(trackHeldItems, heldItemCache)
    }

    private fun handleItemConsumption(player: RemotePlayer, format: String, emit: (String) -> Unit) {
        if (!player.isUsingItem) {
            val state = itemConsumptionCache.remove(player.uuid)
            if (state != null && state.isComplete) {
                emit(format.format(player.gameProfile.name, state.itemStack.hoverName.string))
            }
            return
        }

        val useItem = player.useItem
        if (!useItem.isTrackedConsumable()) {
            itemConsumptionCache.remove(player.uuid)
            return
        }

        val state = itemConsumptionCache[player.uuid]
        if (state == null || !ItemStack.isSameItemSameComponents(state.itemStack, useItem)) {
            itemConsumptionCache[player.uuid] = ItemConsumptionState(
                useItem.copy(), useItem.getUseDuration(player), player.ticksUsingItem,
            )
            return
        }
        state.lastTicksUsingItem = maxOf(state.lastTicksUsingItem, player.ticksUsingItem)
    }

    private fun handleHeldItems(
        player: RemotePlayer,
        format: String,
        trackedItems: Set<Item>,
        emit: (String) -> Unit,
    ) {
        val currentState = HeldItemState(
            player.mainHandItem.takeIf { it.isTrackedHeldItem(trackedItems) },
            player.offhandItem.takeIf { it.isTrackedHeldItem(trackedItems) },
        )
        val previousState = heldItemCache[player.uuid]
        if (currentState.isEmpty) {
            heldItemCache.remove(player.uuid)
            return
        }

        emitChangedHeldItem(player, currentState.mainHand, previousState?.mainHand, InteractionHand.MAIN_HAND, format, emit)
        emitChangedHeldItem(player, currentState.offHand, previousState?.offHand, InteractionHand.OFF_HAND, format, emit)
        heldItemCache[player.uuid] = currentState
    }

    private fun emitChangedHeldItem(
        player: RemotePlayer,
        current: ItemStack?,
        previous: ItemStack?,
        hand: InteractionHand,
        format: String,
        emit: (String) -> Unit,
    ) {
        if (current == null || current.isSameHeldItem(previous)) return
        emit(format.format(player.gameProfile.name, current.hoverName.string, current.count, hand))
    }

    private fun <V> retainObserved(enabled: Boolean, cache: MutableMap<UUID, V>) {
        if (enabled) cache.keys.retainAll(observedPlayers) else cache.clear()
    }
}

private fun ItemStack.isTrackedConsumable(): Boolean = isConsumable &&
    (useAnimation == ItemUseAnimation.EAT || useAnimation == ItemUseAnimation.DRINK)

private fun ItemStack.isTrackedHeldItem(trackedItems: Set<Item>): Boolean = !isEmpty && item in trackedItems

private fun ItemStack.isSameHeldItem(other: ItemStack?): Boolean = other != null &&
    ItemStack.isSameItemSameComponents(this, other) && count == other.count

private data class ItemConsumptionState(
    val itemStack: ItemStack,
    val useDuration: Int,
    var lastTicksUsingItem: Int,
) {
    val isComplete: Boolean
        get() = useDuration > 0 && lastTicksUsingItem >= useDuration - 1
}

private data class HeldItemState(val mainHand: ItemStack?, val offHand: ItemStack?) {
    val isEmpty: Boolean
        get() = mainHand == null && offHand == null
}
