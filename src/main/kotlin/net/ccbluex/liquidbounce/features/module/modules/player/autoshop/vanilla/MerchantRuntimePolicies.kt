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

import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantPlanningStep

internal object MerchantAcquisitionPolicy {
    fun canAcquire(
        tick: Int,
        suppressedUntilTick: Int,
        guiOpen: Boolean,
        inventoryMenuActive: Boolean,
        safeHandAvailable: Boolean,
        hasActiveRule: Boolean,
        interactionInputActive: Boolean,
    ): Boolean = tick >= suppressedUntilTick && !guiOpen && inventoryMenuActive && safeHandAvailable &&
        hasActiveRule && !interactionInputActive
}

internal object MerchantTradeCadencePolicy {
    fun shouldWaitForCps(step: MerchantPlanningStep, cpsReady: Boolean): Boolean =
        step is MerchantPlanningStep.Attempt && !cpsReady
}

internal class MerchantPlanningStepCache {
    private var cachedStep: MerchantPlanningStep? = null

    fun getOrPlan(plan: () -> MerchantPlanningStep): MerchantPlanningStep =
        cachedStep ?: plan().also { cachedStep = it }

    fun invalidate() {
        cachedStep = null
    }
}

internal class MerchantAbandonedOpeningGuard(private val timeoutTicks: Int) {
    private var expiresAtTick = Int.MIN_VALUE

    fun remember(wasOpening: Boolean, tick: Int) {
        if (wasOpening) {
            expiresAtTick = tick + timeoutTicks
        }
    }

    fun consumeMerchantScreen(tick: Int): Boolean {
        if (expiresAtTick == Int.MIN_VALUE || tick >= expiresAtTick) {
            expiresAtTick = Int.MIN_VALUE
            return false
        }

        expiresAtTick = Int.MIN_VALUE
        return true
    }

    fun reset() {
        expiresAtTick = Int.MIN_VALUE
    }
}

internal class MerchantTradeFeedbackGate {
    private var purchaseNotified = false

    fun shouldNotifyPurchase(): Boolean {
        if (purchaseNotified) {
            return false
        }

        purchaseNotified = true
        return true
    }

    fun reset() {
        purchaseNotified = false
    }
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
    USER_INTERACTION,
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
