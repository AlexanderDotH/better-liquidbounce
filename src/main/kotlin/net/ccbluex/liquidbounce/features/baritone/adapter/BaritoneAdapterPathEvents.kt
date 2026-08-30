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

import baritone.api.event.events.PathEvent
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneError
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationPhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase

internal fun BaritoneAdapterContext.onAdapterPathEvent(event: PathEvent) {
    if (event in PATH_START_EVENTS && flightCoordinator.snapshot().phase == BaritoneNavigationPhase.IDLE) {
        automationActivation.observedPathStart()
        flightCoordinator.startTask(navigationMode())
    }
    if (event in PATH_START_EVENTS) pathCancellationPending = false
    when (event) {
        PathEvent.CALC_STARTED, PathEvent.NEXT_SEGMENT_CALC_STARTED -> observedPhase = BaritonePhase.CALCULATING
        PathEvent.CALC_FINISHED_NOW_EXECUTING,
        PathEvent.NEXT_SEGMENT_CALC_FINISHED,
        PathEvent.CONTINUING_ONTO_PLANNED_NEXT,
        PathEvent.SPLICING_ONTO_NEXT_EARLY -> observedPhase = BaritonePhase.PATHING
        PathEvent.AT_GOAL -> arriveAtAdapterGoal()
        PathEvent.CALC_FAILED, PathEvent.NEXT_CALC_FAILED -> failAdapterPath()
        PathEvent.CANCELED -> cancelAdapterPathEvent()
        PathEvent.PATH_FINISHED_NEXT_STILL_CALCULATING -> observedPhase = BaritonePhase.CALCULATING
        PathEvent.DISCARD_NEXT -> Unit
    }
    refreshAdapterRoute()
}

private fun BaritoneAdapterContext.arriveAtAdapterGoal() {
    observedPhase = BaritonePhase.ARRIVED
    flightCoordinator.terminate()
}

private fun BaritoneAdapterContext.failAdapterPath() {
    flightCoordinator.terminate()
    observedPhase = BaritonePhase.FAILED
    lastFailure = BaritoneError(BaritoneErrorCode.INVALID_STATE, "Baritone could not calculate a path")
}

private fun BaritoneAdapterContext.cancelAdapterPathEvent() {
    if (preservesResultAfterCancellation(observedPhase)) {
        pathCancellationPending = false
        return
    }
    pathCancellationPending = true
    if (!nativePathingRelevant()) {
        flightCoordinator.terminate()
        activeTask = null
        observedPhase = BaritonePhase.IDLE
        pathCancellationPending = false
    }
}

private val PATH_START_EVENTS = setOf(PathEvent.CALC_STARTED, PathEvent.NEXT_SEGMENT_CALC_STARTED)
