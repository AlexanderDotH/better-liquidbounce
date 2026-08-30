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
package net.ccbluex.liquidbounce.features.block.placer

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

internal fun BlockPlacer.handleTargetUpdate() {
    if (ticksToWait > 0) {
        ticksToWait--
    } else if (ranAction) {
        ranAction = false
        ticksToWait = cooldown.random()
    }

    val inventoryOpen = !ignoreOpenInventory && mc.gui.screen() is AbstractContainerScreen<*>
    val usingItem = !ignoreUsingItem && player.isUsingItem
    if (inventoryOpen || usingItem || blocks.isEmpty()) return

    val itemStack = slotFinder(null)?.itemStack ?: return
    inaccessible.clear()
    rotationMode.activeMode.onTickStart()
    if (scheduleCurrentPlacements(itemStack)) return

    if (support.enabled && support.chronometer.hasElapsed(support.delay.toLong())) {
        findSupportPath(itemStack)
    }
}

internal fun BlockPlacer.handleMovementInput(event: MovementInputEvent) {
    if (sneakTimes > 0) {
        sneakTimes--
        event.sneak = true
    }
}
