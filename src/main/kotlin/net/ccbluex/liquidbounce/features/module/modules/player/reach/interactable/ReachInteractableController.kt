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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionSettings
import net.minecraft.world.phys.Vec3

/** Exclusive movement ownership used by the controller without depending on the process registry. */
internal fun interface ControllerMovementOwnership {
    fun tryAcquire(owner: String): ControllerMovementLease?
}

internal interface ControllerMovementLease : AutoCloseable {
    val active: Boolean
}

/** Extended target acquisition and lock validation boundary. */
internal interface ControllerTargetPort<T : Any> {
    fun acquire(settings: InteractableSettingsSnapshot): ControllerTargetResult<T>
    fun validate(target: T): Boolean
}

internal sealed interface ControllerTargetResult<out T : Any> {
    data class Acquired<T : Any>(val target: T) : ControllerTargetResult<T>
    data class Rejected(val reason: String) : ControllerTargetResult<Nothing>
}

/** Incremental route planning boundary. Implementations must remain on their creator thread. */
internal fun interface ControllerRoutePort<T : Any, R : Any, S : Any> {
    fun begin(target: T, origin: Vec3, settings: InteractableSettingsSnapshot): ControllerRouteTask<R, S>
}

internal interface ControllerRouteTask<R : Any, S : Any> {
    fun advance(nodes: Int): ControllerRouteProgress<R, S>
    fun cancel()
}

internal sealed interface ControllerRouteProgress<out R : Any, out S : Any> {
    data class Running<S : Any>(val snapshot: S) : ControllerRouteProgress<Nothing, S>
    data class Ready<R : Any, S : Any>(
        val route: R,
        val snapshot: S? = null,
    ) : ControllerRouteProgress<R, S>
    data class Failed(val reason: String) : ControllerRouteProgress<Nothing, Nothing>
}

internal sealed interface InteractableControllerMessage {
    data object MovementBusy : InteractableControllerMessage
    data class TargetRejected(val reason: String) : InteractableControllerMessage
    data class RouteFailed(val reason: String) : InteractableControllerMessage
}

/** Pure session lifecycle boundary. Its adapter executes session effects after every call. */
internal interface ControllerSessionPort<T : Any, R : Any> {
    val active: Boolean

    fun beginPlanning(
        target: T,
        origin: Vec3,
        settings: InteractableSessionSettings,
        tick: Int,
    ): Boolean

    fun acceptRoute(route: R, tick: Int)
    fun tick(tick: Int)
    fun abort(cause: InteractableSessionCause, tick: Int)
    fun hardReset(cause: InteractableSessionCause)
}

/**
 * Coordinates ownership, target locking, incremental planning, and the pure session state machine.
 * Minecraft I/O is supplied by adapters and cannot leak into this policy.
 */
internal class ReachInteractableController<T : Any, R : Any, S : Any>(
    private val ownership: ControllerMovementOwnership,
    private val targets: ControllerTargetPort<T>,
    private val routes: ControllerRoutePort<T, R, S>,
    private val session: ControllerSessionPort<T, R>,
) {
    private var movementLease: ControllerMovementLease? = null
    private var routeTask: ControllerRouteTask<R, S>? = null
    private var capturedSettings: InteractableSettingsSnapshot? = null
    private var lockedTarget: T? = null

    var renderSnapshot: S? = null
        private set
    var lastMessage: InteractableControllerMessage? = null
        private set

    val active: Boolean
        get() = session.active || movementLease?.active == true

    fun claim(
        normalInteractionAvailable: Boolean,
        origin: Vec3,
        settings: InteractableSettingsSnapshot,
        tick: Int,
    ): Boolean {
        if (normalInteractionAvailable || active) return false
        val lease = ownership.tryAcquire(MOVEMENT_OWNER) ?: return false.also {
            lastMessage = InteractableControllerMessage.MovementBusy
        }
        val target = when (val resolution = targets.acquire(settings)) {
            is ControllerTargetResult.Acquired -> resolution.target
            is ControllerTargetResult.Rejected -> return false.also {
                lastMessage = InteractableControllerMessage.TargetRejected(resolution.reason)
                lease.close()
            }
        }
        val sessionSettings = settings.toSessionSettings()
        if (!session.beginPlanning(target, origin, sessionSettings, tick)) {
            lease.close()
            return false
        }

        return runCatching {
            movementLease = lease
            capturedSettings = settings
            lockedTarget = target
            routeTask = routes.begin(target, origin, settings)
            lastMessage = null
            true
        }.getOrElse {
            session.abort(InteractableSessionCause.PLANNING_FAILED, tick)
            releaseIfIdle(force = true)
            false
        }
    }

    fun tick(tick: Int) {
        session.tick(tick)
        if (!session.active) {
            clearPlanning()
            releaseIfIdle()
            return
        }

        val task = routeTask ?: return
        val settings = capturedSettings ?: return
        when (val progress = task.advance(settings.routing.nodesPerTick)) {
            is ControllerRouteProgress.Running -> if (settings.routing.renderPath) {
                renderSnapshot = progress.snapshot
            }
            is ControllerRouteProgress.Ready -> {
                routeTask = null
                if (settings.routing.renderPath && progress.snapshot != null) {
                    renderSnapshot = progress.snapshot
                }
                session.acceptRoute(progress.route, tick)
            }
            is ControllerRouteProgress.Failed -> {
                lastMessage = InteractableControllerMessage.RouteFailed(progress.reason)
                clearPlanning()
                session.abort(InteractableSessionCause.PLANNING_FAILED, tick)
            }
        }
        releaseIfIdle()
    }

    fun validateTarget(): Boolean = lockedTarget?.let(targets::validate) == true

    fun abort(cause: InteractableSessionCause, tick: Int) {
        clearPlanning()
        session.abort(cause, tick)
        releaseIfIdle()
    }

    fun hardReset(cause: InteractableSessionCause) {
        clearPlanning()
        session.hardReset(cause)
        releaseIfIdle(force = true)
    }

    /** Reconciles a terminal session transition triggered outside the controller tick. */
    fun reconcileOwnership() {
        releaseIfIdle()
    }

    private fun clearPlanning() {
        routeTask?.cancel()
        routeTask = null
        renderSnapshot = null
    }

    private fun releaseIfIdle(force: Boolean = false) {
        if (!force && session.active) return
        movementLease?.close()
        movementLease = null
        capturedSettings = null
        lockedTarget = null
        renderSnapshot = null
    }

    private companion object {
        const val MOVEMENT_OWNER = "ReachInteractable"
    }
}

private fun InteractableSettingsSnapshot.toSessionSettings() = InteractableSessionSettings(
    openRetries = openRetries,
    openTimeoutTicks = openTimeoutTicks,
    routeTimeoutTicks = routeTimeoutTicks,
    holdTimeoutTicks = holdTimeoutTicks,
)
