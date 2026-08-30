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
package net.ccbluex.liquidbounce.features.module.modules.world.autotool

import net.ccbluex.liquidbounce.utils.client.SilentHotbarSelectionPolicy
import net.minecraft.world.entity.player.Inventory

internal fun selectAutoToolInventorySwapTarget(
    selectionPolicy: SilentHotbarSelectionPolicy,
    visibleSlot: Int,
    serverSlot: Int,
    emptySlots: List<Int>,
): Int {
    require(Inventory.isHotbarSlot(visibleSlot)) { "Invalid visible hotbar slot: $visibleSlot" }
    require(Inventory.isHotbarSlot(serverSlot)) { "Invalid server hotbar slot: $serverSlot" }
    val preserveVisibleSlot = selectionPolicy.shouldKeepClientSlotVisible
    emptySlots.firstOrNull { !preserveVisibleSlot || it != visibleSlot }?.let { return it }
    if (!preserveVisibleSlot || serverSlot != visibleSlot) return serverSlot
    return (visibleSlot + 1) % Inventory.SELECTION_SIZE
}
