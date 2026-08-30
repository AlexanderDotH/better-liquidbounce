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

import baritone.api.pathing.path.IPathExecutor
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationPhase
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightPathObservation
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.FlightRuntimePosition
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.RuntimePathSegment
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.observeBaritoneFlightPath

internal fun BaritoneAdapterContext.adapterWorldAvailable(): Boolean =
    baritone.playerContext.world() != null && baritone.playerContext.player() != null

internal fun BaritoneAdapterContext.pathingRelevant(): Boolean {
    val taskPending = activeTask != null && observedPhase !in TERMINAL_PHASES
    return taskPending || nativePathingRelevant() || flightCoordinator.snapshot().phase !in setOf(
        BaritoneNavigationPhase.IDLE,
        BaritoneNavigationPhase.WAITING_FOR_PATH,
    )
}

internal fun BaritoneAdapterContext.nativePathingRelevant(): Boolean {
    val behavior = baritone.pathingBehavior
    val elytra = baritone.elytraProcess
    val externalProcess = baritone.pathingControlManager.mostRecentInControl()
        .filter { process -> process !== pauseProcess && process !== flightPauseProcess }
        .filter { process -> runCatching(process::isActive).getOrDefault(false) }
        .filter { process -> runCatching(process::isActive).getOrDefault(false) }
        .isPresent
    return behavior.hasPath() || behavior.inProgress.isPresent || behavior.isPathing ||
        elytra.isActive || elytra.currentDestination() != null || externalProcess
}

internal fun BaritoneAdapterContext.adapterNavigationSnapshot() = flightCoordinator.snapshot().let { navigation ->
    val hasRuntimeState = navigation.phase != BaritoneNavigationPhase.IDLE ||
        navigation.activeMode != null || navigation.detail != null
    if (hasRuntimeState) {
        navigation
    } else {
        navigation.copy(
            requestedMode = navigationMode(),
            restartsRemaining = flightRuntimeConfig().maxRestarts,
        )
    }
}

internal fun BaritoneAdapterContext.observeAdapterFlightPath(): BaritoneFlightPathObservation =
    observeBaritoneFlightPath(
        current = baritone.pathingBehavior.current?.toRuntimeSegment(),
        next = baritone.pathingBehavior.next?.toRuntimeSegment(),
        elytraDestination = baritone.elytraProcess.currentDestination()?.let { destination ->
            FlightRuntimePosition(destination.x + 0.5, destination.y.toDouble(), destination.z + 0.5)
        },
    )

private fun IPathExecutor.toRuntimeSegment(): RuntimePathSegment = RuntimePathSegment(
    positions = path.positions().map { position ->
        FlightRuntimePosition(position.x + 0.5, position.y.toDouble(), position.z + 0.5)
    },
    currentIndex = position,
)

internal fun BaritoneAdapterContext.completedWalkingPathBlocks(): Int {
    val fallback = flightCoordinator.snapshot().phase == BaritoneNavigationPhase.WALK_FALLBACK
    val current = baritone.pathingBehavior.current
    if (!fallback || current == null) {
        walkingFallbackObserved = false
        walkingExecutor = current
        walkingExecutorPosition = current?.position ?: 0
        return 0
    }
    if (!walkingFallbackObserved || walkingExecutor !== current) {
        walkingFallbackObserved = true
        walkingExecutor = current
        walkingExecutorPosition = current.position
        return 0
    }
    val delta = (current.position - walkingExecutorPosition).coerceAtLeast(0)
    walkingExecutorPosition = current.position
    return delta
}

private val TERMINAL_PHASES = setOf(
    net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase.IDLE,
    net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase.ARRIVED,
    net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase.FAILED,
)
