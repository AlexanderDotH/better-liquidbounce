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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.minecraft.client.gui.screens.inventory.MerchantScreen
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.inventory.MenuType

internal fun AutoShopVanillaMode.handleInteractionPacket(
    origin: TransferOrigin,
    packet: ServerboundInteractPacket,
) {
    if (origin != TransferOrigin.OUTGOING) return
    if (sendingOwnedInteraction && packet.entityId == session.targetId) return
    prioritizeUserInteraction()
}

internal fun AutoShopVanillaMode.handleUserInteractionPacket(origin: TransferOrigin) {
    if (origin == TransferOrigin.OUTGOING) prioritizeUserInteraction()
}

internal fun AutoShopVanillaMode.handleOpenScreenPacket(
    origin: TransferOrigin,
    packet: ClientboundOpenScreenPacket,
) {
    if (origin != TransferOrigin.INCOMING || session.state !is MerchantSessionState.Opening) return
    mc.execute {
        if (!running || session.state !is MerchantSessionState.Opening) return@execute
        val tick = mc.player?.tickCount ?: return@execute
        if (packet.type == MenuType.MERCHANT) {
            session.expectMerchantContainer(packet.containerId, tick)
        } else {
            prioritizeUserInteraction(tick)
        }
    }
}

internal fun AutoShopVanillaMode.handleServerClose(
    origin: TransferOrigin,
    packet: ClientboundContainerClosePacket,
) {
    if (origin != TransferOrigin.INCOMING) return
    mc.execute {
        if (session.isOwnedContainer(packet.containerId)) {
            endSession(MerchantSessionEndCause.SERVER_CLOSE)
        }
    }
}

internal fun AutoShopVanillaMode.handleMerchantOffersPacket(
    origin: TransferOrigin,
    packet: ClientboundMerchantOffersPacket,
) {
    if (origin != TransferOrigin.INCOMING) return
    mc.execute {
        if (session.isOwnedContainer(packet.containerId)) planningStepCache.invalidate()
    }
}

private fun AutoShopVanillaMode.prioritizeUserInteraction(tick: Int = mc.player?.tickCount ?: 0) {
    val wasOpening = session.state is MerchantSessionState.Opening
    abandonedOpeningGuard.remember(wasOpening, tick)
    val graceTicks = if (wasOpening) {
        MerchantSessionCoordinator.DEFAULT_TIMEOUT_TICKS
    } else {
        USER_INTERACTION_GRACE_TICKS
    }
    suppressAcquisitionUntilTick = maxOf(suppressAcquisitionUntilTick, tick + graceTicks)
    if (session.state !== MerchantSessionState.Idle) {
        finishSession(MerchantSessionEndCause.USER_INTERACTION, tick)
    }
}

internal fun AutoShopVanillaMode.yieldToUserInteraction(tick: Int): Boolean {
    if (!interactionInputActive()) return false
    prioritizeUserInteraction(tick)
    return true
}

internal fun AutoShopVanillaMode.interactionInputActive(): Boolean =
    mc.options.keyUse.isPressedOnAny ||
        mc.options.keyAttack.isPressedOnAny ||
        mc.options.keyPickItem.isPressedOnAny

internal fun AutoShopVanillaMode.discardAbandonedMerchantScreen(screen: MerchantScreen): Boolean {
    val localPlayer = mc.player ?: return false
    if (localPlayer.containerMenu !== screen.menu ||
        !abandonedOpeningGuard.consumeMerchantScreen(localPlayer.tickCount)) {
        return false
    }
    localPlayer.closeContainer()
    return true
}

internal fun AutoShopVanillaMode.claimOwnedScreen(screen: MerchantScreen): Boolean {
    val localPlayer = mc.player ?: return false
    val menu = screen.menu
    if (!MerchantScreenClaimPolicy.canClaim(running, localPlayer.containerMenu === menu) ||
        !session.claimMerchantScreen(menu.containerId, localPlayer.tickCount)) {
        return false
    }
    ownedMenu = menu
    return true
}

private const val USER_INTERACTION_GRACE_TICKS = 2
