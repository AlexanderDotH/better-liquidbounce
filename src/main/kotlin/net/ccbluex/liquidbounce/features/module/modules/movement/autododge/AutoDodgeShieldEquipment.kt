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

import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldHand
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldInventoryLayout
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldInventorySnapshot
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldObservation
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldPolicy
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldRestoreKind
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldRoute
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldSession
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield.SpearShieldState
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.features.inventory.OffhandReservationManager
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.isPlayerInventory
import net.ccbluex.liquidbounce.utils.item.blocksAttacksComponent
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal class AutoDodgeShieldEquipment : MinecraftShortcuts {

    fun findRoute(): AutoDodgeShieldRouteSelection? {
        val offhand = player.offhandItem.shieldPolicy()?.let {
            AutoDodgeShieldRouteSelection(SpearShieldRoute.AlreadyEquipped(SpearShieldHand.OFF_HAND), it)
        }
        if (offhand != null) return offhand

        val mainHand = player.mainHandItem.shieldPolicy()?.let {
            AutoDodgeShieldRouteSelection(SpearShieldRoute.AlreadyEquipped(SpearShieldHand.MAIN_HAND), it)
        }
        return mainHand ?: findInventoryRoute()
    }

    private fun findInventoryRoute(): AutoDodgeShieldRouteSelection? {
        if (!HotbarItemSlot.OFFHAND.canBeSwapTarget ||
            !player.containerMenu.isPlayerInventory ||
            OffhandReservationManager.isReservedByOther(ModuleAutoDodge)) {
            return null
        }
        val source = Slots.HotbarAndInventory.firstNotNullOfOrNull { slot ->
            slot.shieldPolicy()?.let { policy -> slot to policy }
        } ?: return null
        val sourceId = source.first.getIdForServer(null) ?: return null
        val snapshot = SpearShieldInventorySnapshot(
            player.containerMenu.containerId,
            sourceId,
            source.first.itemStack.copy(),
            player.offhandItem.copy(),
        )
        return AutoDodgeShieldRouteSelection(SpearShieldRoute.SwapToOffhand(snapshot), source.second)
    }

    fun observation(
        state: SpearShieldState<ItemStack>,
        threatPresent: Boolean,
        aligned: Boolean,
    ) = SpearShieldObservation(
        tick = player.tickCount.toLong(),
        threatPresent = threatPresent,
        aligned = aligned,
        usingItem = player.isUsingItem,
        shieldUseActive = isShieldUseActive(),
        useKeyDown = mc.options.keyUse.isPressedOnAny,
        inventoryLayout = inventoryLayout(state),
    )

    fun inventoryLayout(state: SpearShieldState<ItemStack>): SpearShieldInventoryLayout {
        val snapshot = state.sessionOrNull()?.swapSnapshot() ?: return SpearShieldInventoryLayout.NOT_REQUIRED
        val sourceSlot = findSnapshotSourceSlot(snapshot.sourceSlot) ?: return SpearShieldInventoryLayout.CHANGED
        val brokenRestore = (state as? SpearShieldState.Restoring)?.kind ==
            SpearShieldRestoreKind.AFTER_SHIELD_BREAK
        return snapshot.classify(
            containerId = player.containerMenu.containerId,
            sourceStack = sourceSlot.itemStack,
            offhandStack = player.offhandItem,
            stacksMatch = ItemStack::matches,
            isEmpty = ItemStack::isEmpty,
            expectBrokenShieldRestored = brokenRestore,
        )
    }

    fun findSnapshotSourceSlot(sourceSlot: Int): ItemSlot? =
        Slots.HotbarAndInventory.firstOrNull { it.getIdForServer(null) == sourceSlot }

    fun isAligned(policy: SpearShieldPolicy, threat: SpearThreat) = policy.isAligned(
        RotationManager.serverRotation.yRot,
        threat.candidate.position.x - player.x,
        threat.candidate.position.z - player.z,
    )

    fun isShieldUseActive(): Boolean = player.isUsingItem && player.useItem.blocksAttacksComponent != null

    fun startShieldUse(hand: SpearShieldHand) {
        val interactionHand = when (hand) {
            SpearShieldHand.MAIN_HAND -> InteractionHand.MAIN_HAND
            SpearShieldHand.OFF_HAND -> InteractionHand.OFF_HAND
        }
        if (player.getItemInHand(interactionHand).shieldPolicy() == null) return
        val rotation = RotationManager.serverRotation
        useItem(interactionHand, rotation.yRot, rotation.xRot)
    }

    private fun ItemSlot.shieldPolicy() = itemStack.shieldPolicy()

    private fun ItemStack.shieldPolicy(): SpearShieldPolicy? {
        if (!this.`is`(Items.SHIELD) || !isItemEnabled(world.enabledFeatures()) ||
            player.cooldowns.isOnCooldown(this)) {
            return null
        }
        return SpearShieldPolicy.from(blocksAttacksComponent ?: return null, Spear.Shield.releaseDelay)
    }
}

internal data class AutoDodgeShieldRouteSelection(
    val route: SpearShieldRoute<ItemStack>,
    val policy: SpearShieldPolicy,
)

internal fun SpearShieldState<ItemStack>.sessionOrNull(): SpearShieldSession<ItemStack>? = when (this) {
    SpearShieldState.Idle -> null
    is SpearShieldState.Interrupting -> session
    is SpearShieldState.Equipping -> session
    is SpearShieldState.Blocking -> session
    is SpearShieldState.LoweredAwaitingRestore -> session
    is SpearShieldState.Restoring -> session
    is SpearShieldState.Aborted -> session
}

private fun SpearShieldSession<ItemStack>.swapSnapshot() =
    (route as? SpearShieldRoute.SwapToOffhand)?.snapshot

internal fun SpearShieldRoute<ItemStack>.needsOffhandReservation() = when (this) {
    is SpearShieldRoute.SwapToOffhand -> true
    is SpearShieldRoute.AlreadyEquipped -> hand == SpearShieldHand.OFF_HAND
}

internal fun SpearShieldState<ItemStack>.needsOffhandReservation(): Boolean =
    this !is SpearShieldState.Idle && this !is SpearShieldState.Aborted &&
        sessionOrNull()?.route?.needsOffhandReservation() == true
