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
package net.ccbluex.liquidbounce.features.trialchamber

import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.VaultBlock
import net.minecraft.world.level.block.entity.vault.VaultState

/** Owns the short-lived client interactions that confirm visits and local vault unlocks. */
internal class ResourceInteractionTracker(
    private val resourceState: TrialResourceState,
) : MinecraftShortcuts {

    private var pendingMenuVisit: TrialMenuVisitAttempt? = null
    private var pendingVaultUnlock: PendingVaultUnlock? = null

    fun observeBlockInteraction(position: BlockPos, hand: InteractionHand, currentTick: Long) {
        val level = mc.level ?: return
        val player = mc.player ?: return
        val resourcePosition = position.toResourcePosition()

        resourceState.beginMenuVisit(resourcePosition, currentTick)?.let { attempt ->
            pendingMenuVisit = attempt
        }

        val state = level.getBlockState(position)
        if (state.block !is VaultBlock || state.getValue(VaultBlock.STATE) != VaultState.ACTIVE) return
        val expectedKey = if (state.getValue(VaultBlock.OMINOUS)) Items.OMINOUS_TRIAL_KEY else Items.TRIAL_KEY
        if (!player.getItemInHand(hand).`is`(expectedKey)) return
        if (resourceState.beginLocalVaultUnlock(resourcePosition)) {
            pendingVaultUnlock = PendingVaultUnlock(resourcePosition, currentTick)
        }
    }

    fun confirmMenuVisit(menuType: MenuType<*>, currentTick: Long) {
        val attempt = pendingMenuVisit ?: return
        val compatible = when (attempt.resourceId.kind) {
            TrialResourceKind.CHEST -> menuType === MenuType.GENERIC_9x3 || menuType === MenuType.GENERIC_9x6
            TrialResourceKind.BARREL -> menuType === MenuType.GENERIC_9x3
            TrialResourceKind.DISPENSER -> menuType === MenuType.GENERIC_3x3
            else -> false
        }
        if (compatible) resourceState.confirmMenuVisit(attempt, currentTick)
        pendingMenuVisit = null
    }

    fun observeVaultUnlock(currentTick: Long) {
        val pending = pendingVaultUnlock ?: return
        if (currentTick - pending.startedAtTick > VAULT_UNLOCK_OBSERVATION_TICKS) {
            pendingVaultUnlock = null
            return
        }
        val state = mc.level?.getBlockState(pending.position.toBlockPos()) ?: return
        if (state.block !is VaultBlock) {
            pendingVaultUnlock = null
            return
        }
        if (state.getValue(VaultBlock.STATE) == VaultState.UNLOCKING ||
            state.getValue(VaultBlock.STATE) == VaultState.EJECTING
        ) {
            resourceState.completeLocalVaultUnlock(pending.position)
            pendingVaultUnlock = null
        }
    }

    fun clearMenuVisit() {
        pendingMenuVisit = null
    }

    fun reset() {
        pendingMenuVisit = null
        pendingVaultUnlock = null
    }

    private data class PendingVaultUnlock(
        val position: TrialResourcePosition,
        val startedAtTick: Long,
    )

    private companion object {
        const val VAULT_UNLOCK_OBSERVATION_TICKS = 40L
    }
}
