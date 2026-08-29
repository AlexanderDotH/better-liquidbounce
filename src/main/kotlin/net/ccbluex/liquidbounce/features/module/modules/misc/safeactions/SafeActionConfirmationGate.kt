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
package net.ccbluex.liquidbounce.features.module.modules.misc.safeactions

internal enum class SafeActionConfirmationDecision {
    BLOCK,
    BLOCK_AND_NOTIFY,
    ALLOW,
}

internal class SafeActionConfirmationGate<T> {

    var pendingAction: T? = null
        private set

    fun request(action: T, freshPress: Boolean): SafeActionConfirmationDecision {
        if (!freshPress) return SafeActionConfirmationDecision.BLOCK

        if (pendingAction == action) {
            pendingAction = null
            return SafeActionConfirmationDecision.ALLOW
        }

        pendingAction = action
        return SafeActionConfirmationDecision.BLOCK_AND_NOTIFY
    }

    fun reset() {
        pendingAction = null
    }

    fun invalidateWhen(predicate: (T) -> Boolean) {
        val action = pendingAction ?: return
        if (predicate(action)) reset()
    }
}
