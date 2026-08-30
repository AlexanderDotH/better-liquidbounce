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

import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.world.inventory.MerchantMenu

internal fun AutoShopVanillaMode.currentOwnedMenu(containerId: Int): MerchantMenu? {
    val menu = ownedMenu ?: return null
    val localPlayer = mc.player ?: return null
    return menu.takeIf { it.containerId == containerId && localPlayer.containerMenu === it }
}

internal fun AutoShopVanillaMode.finishSession(cause: MerchantSessionEndCause, tick: Int) {
    endSession(cause, tick)
}

internal fun AutoShopVanillaMode.endSession(
    cause: MerchantSessionEndCause,
    tick: Int = mc.player?.tickCount ?: 0,
) {
    val decision = MerchantCleanupPolicy.forCause(cause)
    val menuToClose = ownedMenu
    ownedMenu = null
    roundRobinPass = null
    planningStepCache.invalidate()
    cpsGate.reset()
    feedbackGate.reset()
    sendingOwnedInteraction = false

    if (decision.rememberRetry) {
        session.finish(tick)
    } else {
        session.resetAll()
        abandonedOpeningGuard.reset()
        suppressAcquisitionUntilTick = Int.MIN_VALUE
    }

    val localPlayer = mc.player
    if (decision.closeOwnedMenu && menuToClose != null && localPlayer?.containerMenu === menuToClose) {
        localPlayer.closeContainer()
    }
}
