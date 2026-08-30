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
package net.ccbluex.liquidbounce.features.baritone.adapter

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneError
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightRuntimeSignal

internal fun BaritoneAdapterContext.handleAdapterFlightSignal(signal: BaritoneFlightRuntimeSignal?) {
    signal ?: return
    pathCancellationPending = false
    when (signal) {
        BaritoneFlightRuntimeSignal.Arrived -> {
            flightCoordinator.terminate()
            observedPhase = BaritonePhase.ARRIVED
            lastFailure = null
        }
        is BaritoneFlightRuntimeSignal.FailTask -> rememberAdapterFlightFailure(signal.detail)
        is BaritoneFlightRuntimeSignal.CancelTask -> rememberAdapterFlightFailure(signal.detail)
    }
    baritone.pathingBehavior.cancelEverything()
    baritone.inputOverrideHandler.clearAllKeys()
    invalidateAdapterRoute(forceRevision = true)
}

private fun BaritoneAdapterContext.rememberAdapterFlightFailure(detail: String) {
    observedPhase = BaritonePhase.FAILED
    lastFailure = BaritoneError(BaritoneErrorCode.INVALID_STATE, detail)
}
