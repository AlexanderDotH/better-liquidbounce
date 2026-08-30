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

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneControlAction
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLifecycleEvent
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSnapshot
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskRequest

internal fun BaritoneAdapterContext.submitAdapterTask(
    task: BaritoneTaskRequest,
): BaritoneResult<BaritoneSnapshot> = executeAdapterOperation("task") {
    requireAdapterWorld()
    automationActivation.afterSuccess { taskDispatcher.submit(task) }
    flightCoordinator.startTask(navigationMode())
    activeTask = task
    observedPhase = if (pauseController.current().paused) BaritonePhase.PAUSED else BaritonePhase.CALCULATING
    lastFailure = null
    refreshAdapterRoute()
    adapterSnapshot()
}.alsoAdapterFailure(::rememberAdapterTaskFailure)

internal fun BaritoneAdapterContext.controlAdapter(
    action: BaritoneControlAction,
): BaritoneResult<BaritoneSnapshot> = executeAdapterOperation("action") {
    when (action) {
        BaritoneControlAction.PAUSE -> pauseController.pauseManually()
        BaritoneControlAction.RESUME -> pauseController.resumeManually()
        BaritoneControlAction.CANCEL -> cancelAdapterTask()
    }
    adapterSnapshot()
}

internal fun BaritoneAdapterContext.applyAdapterLifecycle(event: BaritoneLifecycleEvent): BaritoneResult<Unit> =
    executeAdapterOperation {
        if (event == BaritoneLifecycleEvent.DIMENSION_CHANGE) {
            flightCoordinator.dimensionChanged()
        } else {
            flightCoordinator.terminate()
        }
        lifecyclePolicy.apply(event)
        if (event != BaritoneLifecycleEvent.DIMENSION_CHANGE) resetAdapterTaskState()
    }

internal fun BaritoneAdapterContext.clearAdapterKeys(): BaritoneResult<Unit> = executeAdapterOperation {
    baritone.inputOverrideHandler.clearAllKeys()
}

internal fun BaritoneAdapterContext.cancelAdapterTask() {
    flightCoordinator.terminate()
    baritone.pathingBehavior.cancelEverything()
    baritone.inputOverrideHandler.clearAllKeys()
    pauseController.reset()
    resetAdapterTaskState()
    invalidateAdapterRoute(forceRevision = true)
}

internal fun BaritoneAdapterContext.requireAdapterWorld() {
    if (!adapterWorldAvailable()) {
        throw BaritoneAdapterException(BaritoneErrorCode.INVALID_STATE, "No Minecraft world is loaded")
    }
}

private fun BaritoneAdapterContext.resetAdapterTaskState() {
    activeTask = null
    observedPhase = BaritonePhase.IDLE
    lastFailure = null
    pathCancellationPending = false
}
