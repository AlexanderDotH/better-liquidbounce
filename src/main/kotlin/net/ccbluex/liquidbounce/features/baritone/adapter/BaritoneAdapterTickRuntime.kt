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

import baritone.api.event.events.TickEvent
import baritone.api.event.events.type.EventState
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogLevel
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationPhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseReason
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightRuntimeInput
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.FlightRuntimePosition

internal fun BaritoneAdapterContext.onAdapterTick(event: TickEvent) {
    if (event.state != EventState.PRE) return
    startFlightForNativePathIfNeeded()
    val pauseState = pauseController.tick(detectAdapterConflicts())
    if (adapterWorldAvailable()) {
        val userInput = pauseState.cause?.reason == BaritonePauseReason.USER_INPUT
        val runtimeResult = flightCoordinator.tick(
            BaritoneFlightRuntimeInput(
                playerPosition = baritone.playerContext.player().position().let {
                    FlightRuntimePosition(it.x, it.y, it.z)
                },
                path = observeAdapterFlightPath(),
                userInput = userInput,
                paused = pauseState.paused && !userInput,
                completedWalkPathBlocks = completedWalkingPathBlocks(),
            ),
        )
        handleAdapterFlightSignal(runtimeResult.signal)
    }
    terminateCanceledAdapterPathIfIdle()
    if (event.count % ROUTE_UPDATE_INTERVAL_TICKS == 0) refreshAdapterRoute()
}

private fun BaritoneAdapterContext.startFlightForNativePathIfNeeded() {
    val navigationIdle = flightCoordinator.snapshot().phase == BaritoneNavigationPhase.IDLE
    if (adapterWorldAvailable() && observedPhase !in setOf(BaritonePhase.ARRIVED, BaritonePhase.FAILED) &&
        nativePathingRelevant() && navigationIdle
    ) {
        automationActivation.observedPathStart()
        flightCoordinator.startTask(navigationMode())
    }
}

private fun BaritoneAdapterContext.detectAdapterConflicts() = runCatching(conflictDetector::detect).getOrElse {
    appendAdapterLog(BaritoneLogLevel.ERROR, "Unable to evaluate Baritone conflicts: ${it.message.orEmpty()}")
    emptyList()
}

private fun BaritoneAdapterContext.terminateCanceledAdapterPathIfIdle() {
    if (pathCancellationPending && !nativePathingRelevant()) {
        flightCoordinator.terminate()
        activeTask = null
        observedPhase = BaritonePhase.IDLE
        pathCancellationPending = false
    }
}

private const val ROUTE_UPDATE_INTERVAL_TICKS = 4
