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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

internal object MerchantAcquisitionPolicy {
    fun canAcquire(
        tick: Int,
        suppressedUntilTick: Int,
        guiOpen: Boolean,
        inventoryMenuActive: Boolean,
        safeHandAvailable: Boolean,
        hasActiveRule: Boolean,
    ): Boolean = tick >= suppressedUntilTick && !guiOpen && inventoryMenuActive && safeHandAvailable && hasActiveRule
}

internal object MerchantRotationGate {
    fun canInteract(
        state: MerchantSessionState,
        targetId: Int,
        angleDifference: Float,
        threshold: Float,
    ): Boolean {
        val rotating = state as? MerchantSessionState.Rotating ?: return false
        return rotating.targetId == targetId && angleDifference <= threshold
    }
}

internal object MerchantScreenClaimPolicy {
    fun canClaim(modeRunning: Boolean, activeMenuMatches: Boolean): Boolean =
        modeRunning && activeMenuMatches
}

internal enum class MerchantSessionEndCause {
    TARGET_LOST,
    TIMEOUT,
    UNEXPECTED_GUI,
    TRADE_BLOCKED,
    SERVER_CLOSE,
    DISABLE_OR_MODE_SWITCH,
    WORLD_CHANGE,
    DISCONNECT,
}

internal data class MerchantCleanupDecision(val closeOwnedMenu: Boolean, val rememberRetry: Boolean)

internal object MerchantCleanupPolicy {
    fun forCause(cause: MerchantSessionEndCause): MerchantCleanupDecision = when (cause) {
        MerchantSessionEndCause.SERVER_CLOSE -> MerchantCleanupDecision(false, true)
        MerchantSessionEndCause.DISCONNECT -> MerchantCleanupDecision(false, false)
        MerchantSessionEndCause.DISABLE_OR_MODE_SWITCH,
        MerchantSessionEndCause.WORLD_CHANGE -> MerchantCleanupDecision(true, false)
        else -> MerchantCleanupDecision(true, true)
    }
}
