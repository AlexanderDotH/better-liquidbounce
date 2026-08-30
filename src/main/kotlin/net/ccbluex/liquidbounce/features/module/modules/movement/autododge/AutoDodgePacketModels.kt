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
        fun create(origin: AutoDodgePacketEndpoint, destination: AutoDodgePacketEndpoint) = AutoDodgePacketBurst(
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

internal data class AutoDodgeActiveHold(
    val burst: AutoDodgePacketBurst,
    val threatKey: AutoDodgePacketThreatKey,
    val predictedImpactTick: Long,
    val holdUntilTick: Long,
    val lease: AutoCloseable,
)

internal val AutoDodgePacketPreflightResult.runtimeState: AutoDodgePacketRuntimeState
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
