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
package net.ccbluex.liquidbounce.utils.network

enum class BlockSlotSwitchPolicy(
    private val requireSelectedSlot: Boolean,
    private val restoreServerSlot: Boolean,
) {
    RESTORE_AFTER_USE(requireSelectedSlot = false, restoreServerSlot = true),
    KEEP_SELECTED(requireSelectedSlot = false, restoreServerSlot = false),
    REQUIRE_SELECTED(requireSelectedSlot = true, restoreServerSlot = false),
    ;

    fun canUseSlot(slotChanged: Boolean): Boolean = !requireSelectedSlot || !slotChanged

    fun shouldRestoreServerSlot(slotChanged: Boolean, mainHand: Boolean): Boolean =
        restoreServerSlot && slotChanged && mainHand
}
