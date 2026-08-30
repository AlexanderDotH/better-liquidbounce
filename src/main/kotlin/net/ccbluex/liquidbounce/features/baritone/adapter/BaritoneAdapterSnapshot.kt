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

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCapability
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationMode
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseCause
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseReason
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneProgress
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSnapshot
import kotlin.math.ceil
import kotlin.math.sqrt

internal fun BaritoneAdapterContext.adapterSnapshot(): BaritoneSnapshot {
    val pauseState = pauseController.current()
    val navigation = adapterNavigationSnapshot()
    val flying = navigation.activeMode == BaritoneNavigationMode.FLY
    return BaritoneSnapshot(
        revision = revisions.next(),
        availability = BaritoneCapability.AVAILABLE,
        status = currentAdapterPhase(pauseState.cause),
        task = activeTask,
        etaSeconds = if (flying) flightCoordinator.estimatedSeconds() else estimatedAdapterSeconds(),
        progress = if (flying) flightCoordinator.progress() else adapterProgress(),
        pauseReason = pauseState.cause,
        settings = adapterSettings(),
        waypoints = adapterWaypoints(),
        logs = logBuffer.entries(),
        failure = lastFailure,
        navigation = navigation,
    )
}

private fun BaritoneAdapterContext.currentAdapterPhase(pauseCause: BaritonePauseCause?): BaritonePhase {
    if (!adapterWorldAvailable()) return BaritonePhase.NO_WORLD
    if (pauseCause != null && (pauseCause.reason == BaritonePauseReason.MANUAL || pathingRelevant())) {
        return BaritonePhase.PAUSED
    }
    if (lastFailure != null && observedPhase == BaritonePhase.FAILED) return BaritonePhase.FAILED
    val behavior = baritone.pathingBehavior
    if (behavior.inProgress.isPresent) return BaritonePhase.CALCULATING
    if (behavior.isPathing) return BaritonePhase.PATHING
    navigationBaritonePhase(flightCoordinator.snapshot().phase)?.let { return it }
    return observedPhase.takeIf { activeTask != null } ?: BaritonePhase.IDLE
}

private fun BaritoneAdapterContext.estimatedAdapterSeconds(): Long? = baritone.pathingBehavior.estimatedTicksToGoal()
    .filter { it.isFinite() && it >= 0.0 }
    .map { ceil(it / TICKS_PER_SECOND).toLong() }
    .orElse(null)

private fun BaritoneAdapterContext.adapterProgress(): BaritoneProgress? {
    val executor = baritone.pathingBehavior.current ?: return when (observedPhase) {
        BaritonePhase.ARRIVED -> BaritoneProgress(1.0, 0.0)
        else -> null
    }
    val positions = executor.path.positions()
    if (positions.isEmpty()) return null
    val index = executor.position.coerceIn(0, positions.lastIndex)
    val fraction = if (positions.size == 1) 1.0 else index.toDouble() / positions.lastIndex
    var remaining = 0.0
    for (position in index until positions.lastIndex) {
        val first = positions[position]
        val second = positions[position + 1]
        val x = (second.x - first.x).toDouble()
        val y = (second.y - first.y).toDouble()
        val z = (second.z - first.z).toDouble()
        remaining += sqrt(x * x + y * y + z * z)
    }
    return BaritoneProgress(fraction, remaining, executor.path.numNodesConsidered.toLong())
}

private const val TICKS_PER_SECOND = 20.0
