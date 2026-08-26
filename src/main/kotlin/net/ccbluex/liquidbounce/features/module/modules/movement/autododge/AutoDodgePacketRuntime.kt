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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

internal enum class AutoDodgePacketThreatType {
    PROJECTILE,
    MACE,
    SPEAR,
}

internal enum class AutoDodgePacketRuntimeState(val debugName: String) {
    IDLE("Idle"),
    COOLDOWN("Cooldown"),
    LEASE_UNAVAILABLE("LeaseUnavailable"),
    CONNECTION_UNAVAILABLE("ConnectionUnavailable"),
    SAFETY_REJECTED("SafetyRejected"),
    BURST_REJECTED("BurstRejected"),
    ARMED("Armed"),
    SENDING_DESTINATION("SendingDestination"),
    HOLDING("Holding"),
    SENDING_RETURN("SendingReturn"),
    SEND_FAILED("SendFailed"),
    RETURNED("Returned"),
}

internal enum class AutoDodgePacketPreflightResult {
    READY,
    CONNECTION_UNAVAILABLE,
    SAFETY_REJECTED,
    BURST_REJECTED,
}

internal data class AutoDodgePacketEndpoint(
    val position: Vec3,
    val onGround: Boolean,
    val horizontalCollision: Boolean,
) {
    init {
        require(position.x.isFinite() && position.y.isFinite() && position.z.isFinite()) {
            "An Auto-Dodge packet endpoint must be finite"
        }
    }
}

internal data class AutoDodgePacketRuntimeRequest(
    val tick: Long,
    val cooldownTicks: Int,
    val holdTicks: Int,
    val selectedThreat: AutoDodgePacketThreatType,
    val threatEntityId: Int = 0,
    val predictedImpactTick: Long = tick,
    val dodgeAtTick: Long = tick,
    val returnNotBeforeTick: Long = predictedImpactTick + holdTicks,
    val destination: AutoDodgePacketEndpoint,
) {
    init {
        require(cooldownTicks in AUTO_DODGE_PACKET_MIN_COOLDOWN_TICKS..AUTO_DODGE_PACKET_MAX_COOLDOWN_TICKS) {
            "Auto-Dodge packet cooldown must be between $AUTO_DODGE_PACKET_MIN_COOLDOWN_TICKS and " +
                "$AUTO_DODGE_PACKET_MAX_COOLDOWN_TICKS ticks"
        }
        require(holdTicks in AUTO_DODGE_PACKET_MIN_HOLD_TICKS..AUTO_DODGE_PACKET_MAX_HOLD_TICKS) {
            "Auto-Dodge packet hold must be between $AUTO_DODGE_PACKET_MIN_HOLD_TICKS and " +
                "$AUTO_DODGE_PACKET_MAX_HOLD_TICKS ticks"
        }
        require(predictedImpactTick >= tick) {
            "Auto-Dodge predicted impact cannot precede the packet transmission tick"
        }
        require(dodgeAtTick <= tick) {
            "Auto-Dodge cannot start before its predicted dodge tick"
        }
        require(returnNotBeforeTick >= predictedImpactTick) {
            "Auto-Dodge cannot return before the predicted impact"
        }
    }

    val threatKey = AutoDodgePacketThreatKey(selectedThreat, threatEntityId)
}

internal data class AutoDodgePacketBurst(
    val origin: AutoDodgePacketEndpoint,
    val destination: AutoDodgePacketEndpoint,
    val destinationPacket: ServerboundMovePlayerPacket.Pos,
    val returnPacket: ServerboundMovePlayerPacket.Pos,
) {
    companion object {
        fun create(
            origin: AutoDodgePacketEndpoint,
            destination: AutoDodgePacketEndpoint,
        ) = AutoDodgePacketBurst(
            origin = origin,
            destination = destination,
            destinationPacket = destination.toPositionPacket(),
            returnPacket = origin.toPositionPacket(),
        )
    }
}

internal data class AutoDodgePacketRuntimeDebug(
    val state: AutoDodgePacketRuntimeState = AutoDodgePacketRuntimeState.IDLE,
    val selectedThreat: AutoDodgePacketThreatType? = null,
    val destination: Vec3? = null,
    val predictedImpactTick: Long? = null,
    val dodgeAtTick: Long? = null,
    val holdUntilTick: Long? = null,
    val lastSuccessfulBurstTick: Long? = null,
    val lastSuccessfulDestination: Vec3? = null,
)

/**
 * Holds one collision-preflighted server-side dodge before returning exactly to the captured origin.
 *
 * The exclusive movement lease spans the complete hold. Normal movement packets must be suppressed while
 * [suppressesMovementPackets] is true so they cannot restore the local position before the return tick.
 */
internal class AutoDodgePacketRuntime {

    var debug = AutoDodgePacketRuntimeDebug()
        private set

    private var activeHold: ActiveHold? = null

    val suppressesMovementPackets: Boolean
        get() = activeHold != null

    val activeThreatKey: AutoDodgePacketThreatKey?
        get() = activeHold?.threatKey

    fun arm(candidate: AutoDodgePacketCandidate) {
        if (activeHold != null) {
            updateState(AutoDodgePacketRuntimeState.HOLDING)
            return
        }
        debug = debug.copy(
            state = AutoDodgePacketRuntimeState.ARMED,
            selectedThreat = candidate.threatType,
            destination = candidate.destination,
            predictedImpactTick = candidate.impactSchedule.predictedImpactTick,
            dodgeAtTick = candidate.impactSchedule.dodgeAtTick,
            holdUntilTick = null,
        )
    }

    fun start(
        request: AutoDodgePacketRuntimeRequest,
        snapshotOrigin: () -> AutoDodgePacketEndpoint,
        acquireMovementLease: () -> AutoCloseable?,
        preflight: (AutoDodgePacketBurst) -> AutoDodgePacketPreflightResult,
        sendPacket: (ServerboundMovePlayerPacket.Pos) -> Unit,
    ): Boolean {
        if (activeHold != null) {
            updateState(AutoDodgePacketRuntimeState.HOLDING)
            return false
        }
        debug = debug.copy(
            state = AutoDodgePacketRuntimeState.IDLE,
            selectedThreat = request.selectedThreat,
            destination = request.destination.position,
            predictedImpactTick = request.predictedImpactTick,
            dodgeAtTick = request.dodgeAtTick,
            holdUntilTick = null,
        )
        if (!cooldownReady(request.tick, request.cooldownTicks)) {
            updateState(AutoDodgePacketRuntimeState.COOLDOWN)
            return false
        }

        val burst = AutoDodgePacketBurst.create(snapshotOrigin(), request.destination)
        val lease = acquireMovementLease()
        if (lease == null) {
            updateState(AutoDodgePacketRuntimeState.LEASE_UNAVAILABLE)
            return false
        }

        return startWithLease(request, burst, lease, preflight, sendPacket)
    }

    private fun startWithLease(
        request: AutoDodgePacketRuntimeRequest,
        burst: AutoDodgePacketBurst,
        lease: AutoCloseable,
        preflight: (AutoDodgePacketBurst) -> AutoDodgePacketPreflightResult,
        sendPacket: (ServerboundMovePlayerPacket.Pos) -> Unit,
    ): Boolean {
        if (burst.origin.position == burst.destination.position) {
            updateState(AutoDodgePacketRuntimeState.BURST_REJECTED)
            lease.close()
            return false
        }
        val preflightResult = try {
            preflight(burst)
        } catch (throwable: Throwable) {
            updateState(AutoDodgePacketRuntimeState.BURST_REJECTED)
            lease.close()
            throw throwable
        }
        if (preflightResult != AutoDodgePacketPreflightResult.READY) {
            updateState(preflightResult.runtimeState)
            lease.close()
            return false
        }

        updateState(AutoDodgePacketRuntimeState.SENDING_DESTINATION)
        try {
            sendPacket(burst.destinationPacket)
        } catch (throwable: Throwable) {
            updateState(AutoDodgePacketRuntimeState.SEND_FAILED)
            lease.close()
            throw throwable
        }

        val holdUntilTick = request.returnNotBeforeTick
        activeHold = ActiveHold(
            burst = burst,
            threatKey = request.threatKey,
            predictedImpactTick = request.predictedImpactTick,
            holdUntilTick = holdUntilTick,
            lease = lease,
        )
        debug = debug.copy(
            state = AutoDodgePacketRuntimeState.HOLDING,
            holdUntilTick = holdUntilTick,
        )
        return true
    }

    /** Extends, but never shortens, a hold when the same threat's predicted impact moves later. */
    fun extendHold(
        threatKey: AutoDodgePacketThreatKey,
        predictedImpactTick: Long,
        postImpactHoldTicks: Int,
    ): Boolean {
        require(postImpactHoldTicks in AUTO_DODGE_PACKET_MIN_HOLD_TICKS..AUTO_DODGE_PACKET_MAX_HOLD_TICKS) {
            "Auto-Dodge post-impact hold must be between $AUTO_DODGE_PACKET_MIN_HOLD_TICKS and " +
                "$AUTO_DODGE_PACKET_MAX_HOLD_TICKS ticks"
        }
        val hold = activeHold ?: return false
        if (hold.threatKey != threatKey) {
            return false
        }

        val extendedImpactTick = maxOf(hold.predictedImpactTick, predictedImpactTick)
        val extendedHoldUntilTick = maxOf(hold.holdUntilTick, extendedImpactTick + postImpactHoldTicks)
        if (extendedImpactTick != hold.predictedImpactTick || extendedHoldUntilTick != hold.holdUntilTick) {
            activeHold = hold.copy(
                predictedImpactTick = extendedImpactTick,
                holdUntilTick = extendedHoldUntilTick,
            )
            debug = debug.copy(
                predictedImpactTick = extendedImpactTick,
                holdUntilTick = extendedHoldUntilTick,
            )
        }
        return true
    }

    /** Returns true when an active hold consumed this update, including its terminal return update. */
    fun progressHold(
        tick: Long,
        preflight: (AutoDodgePacketBurst) -> AutoDodgePacketPreflightResult,
        sendPacket: (ServerboundMovePlayerPacket.Pos) -> Unit,
    ): Boolean {
        val hold = activeHold ?: return false
        val preflightResult = try {
            preflight(hold.burst)
        } catch (throwable: Throwable) {
            updateState(AutoDodgePacketRuntimeState.BURST_REJECTED)
            releaseActiveHold()
            throw throwable
        }
        if (preflightResult != AutoDodgePacketPreflightResult.READY) {
            updateState(preflightResult.runtimeState)
            releaseActiveHold()
            return true
        }
        if (tick < hold.holdUntilTick) {
            updateState(AutoDodgePacketRuntimeState.HOLDING)
            return true
        }

        updateState(AutoDodgePacketRuntimeState.SENDING_RETURN)
        try {
            sendPacket(hold.burst.returnPacket)
        } catch (throwable: Throwable) {
            updateState(AutoDodgePacketRuntimeState.SEND_FAILED)
            releaseActiveHold()
            throw throwable
        }

        releaseActiveHold()
        debug = debug.copy(
            state = AutoDodgePacketRuntimeState.RETURNED,
            holdUntilTick = null,
            lastSuccessfulBurstTick = tick,
            lastSuccessfulDestination = hold.burst.destination.position,
        )
        return true
    }

    fun reset(sendReturn: ((ServerboundMovePlayerPacket.Pos) -> Unit)? = null) {
        activeHold?.burst?.returnPacket?.let { returnPacket ->
            runCatching { sendReturn?.invoke(returnPacket) }
        }
        releaseActiveHold()
        debug = AutoDodgePacketRuntimeDebug()
    }

    /** Clears an inactive attempt display without discarding the shared success cooldown. */
    fun idle() {
        if (activeHold != null) {
            updateState(AutoDodgePacketRuntimeState.HOLDING)
            return
        }
        debug = debug.copy(
            state = AutoDodgePacketRuntimeState.IDLE,
            selectedThreat = null,
            destination = null,
            predictedImpactTick = null,
            dodgeAtTick = null,
            holdUntilTick = null,
        )
    }

    private fun cooldownReady(tick: Long, cooldownTicks: Int): Boolean {
        val lastSuccess = debug.lastSuccessfulBurstTick ?: return true
        return tick >= lastSuccess && tick - lastSuccess >= cooldownTicks
    }

    private fun updateState(state: AutoDodgePacketRuntimeState) {
        debug = debug.copy(state = state)
    }

    private fun releaseActiveHold() {
        val hold = activeHold ?: return
        activeHold = null
        hold.lease.close()
        debug = debug.copy(holdUntilTick = null)
    }

    private data class ActiveHold(
        val burst: AutoDodgePacketBurst,
        val threatKey: AutoDodgePacketThreatKey,
        val predictedImpactTick: Long,
        val holdUntilTick: Long,
        val lease: AutoCloseable,
    )
}

private val AutoDodgePacketPreflightResult.runtimeState: AutoDodgePacketRuntimeState
    get() = when (this) {
        AutoDodgePacketPreflightResult.READY -> AutoDodgePacketRuntimeState.IDLE
        AutoDodgePacketPreflightResult.CONNECTION_UNAVAILABLE -> AutoDodgePacketRuntimeState.CONNECTION_UNAVAILABLE
        AutoDodgePacketPreflightResult.SAFETY_REJECTED -> AutoDodgePacketRuntimeState.SAFETY_REJECTED
        AutoDodgePacketPreflightResult.BURST_REJECTED -> AutoDodgePacketRuntimeState.BURST_REJECTED
    }

private fun AutoDodgePacketEndpoint.toPositionPacket() = ServerboundMovePlayerPacket.Pos(
    position.x,
    position.y,
    position.z,
    onGround,
    horizontalCollision,
)

internal const val AUTO_DODGE_PACKET_MIN_COOLDOWN_TICKS = 1
internal const val AUTO_DODGE_PACKET_MAX_COOLDOWN_TICKS = 20
internal const val AUTO_DODGE_PACKET_MIN_HOLD_TICKS = 1
internal const val AUTO_DODGE_PACKET_MAX_HOLD_TICKS = 20
internal const val AUTO_DODGE_PACKET_DEFAULT_HOLD_TICKS = 2
internal const val AUTO_DODGE_PACKET_MOVEMENT_OWNER = "AutoDodgePacket"
