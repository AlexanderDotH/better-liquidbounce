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
package net.ccbluex.liquidbounce.features.module.modules.misc.inventorytracker.contract

import java.util.UUID

internal fun interface InventoryTrackerCommandActions {

    fun open(playerName: String): Boolean
}

internal object InventoryTrackerCommandBridge {

    private val unavailableActions = InventoryTrackerCommandActions {
        error("InventoryTracker command actions have not been installed")
    }

    private var actions: InventoryTrackerCommandActions = unavailableActions

    var viewedPlayer: UUID? = null

    fun install(actions: InventoryTrackerCommandActions) {
        this.actions = actions
    }

    fun open(playerName: String): Boolean = actions.open(playerName)

    fun <T> withActionsForTest(actions: InventoryTrackerCommandActions, block: () -> T): T {
        val previousActions = this.actions
        val previousViewedPlayer = viewedPlayer
        this.actions = actions
        return try {
            block()
        } finally {
            this.actions = previousActions
            viewedPlayer = previousViewedPlayer
        }
    }
}
