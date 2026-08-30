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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research



import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.REMOTE_KILL_ROUTE_MAX_STEP_WAIT_TICKS

internal const val MACE_CLIP_RESEARCH_SCHEMA_VERSION = 1
internal const val MACE_CLIP_MAXIMUM_DISTANCE = 500.0
internal const val MACE_CLIP_MAXIMUM_PRIMING_PACKETS = 18
internal const val MACE_CLIP_MINIMUM_CLEARANCE = 4.0
internal const val MACE_CLIP_MAXIMUM_CLEARANCE = 128.0
internal const val MACE_CLIP_MAXIMUM_PHASE_DELAY_TICKS = REMOTE_KILL_ROUTE_MAX_STEP_WAIT_TICKS
internal const val MACE_CLIP_MAXIMUM_TERMINAL_HOLD_TICKS = 20
internal const val MACE_CLIP_MAXIMUM_PACKET_BUDGET = 256

/** Stable packet-shape names persisted in MaceClip research evidence. */
internal enum class MaceClipResearchPacketShape {
    POSITION,
    POSITION_ROTATION,
}

internal sealed interface MaceClipResearchProbeRequest {

    val primingPackets: Int
    val packetShape: MaceClipResearchPacketShape
    val clearance: Double
    val phaseDelayTicks: Int
    val terminalHoldTicks: Int
    val probe: Probe

    enum class Probe {
        MOVE,
        ATTACK,
    }

    data class Move(
        val distance: Double,
        override val primingPackets: Int,
        override val packetShape: MaceClipResearchPacketShape,
        override val clearance: Double,
        override val phaseDelayTicks: Int,
        override val terminalHoldTicks: Int,
    ) : MaceClipResearchProbeRequest {
        override val probe = Probe.MOVE
    }

    data class Attack(
        override val primingPackets: Int,
        override val packetShape: MaceClipResearchPacketShape,
        override val clearance: Double,
        override val phaseDelayTicks: Int,
        override val terminalHoldTicks: Int,
    ) : MaceClipResearchProbeRequest {
        override val probe = Probe.ATTACK
    }
}

internal fun MaceClipResearchProbeRequest.isValid(): Boolean =
    primingPackets in 0..MACE_CLIP_MAXIMUM_PRIMING_PACKETS &&
        clearance.isFinite() && clearance in MACE_CLIP_MINIMUM_CLEARANCE..MACE_CLIP_MAXIMUM_CLEARANCE &&
        phaseDelayTicks in 0..MACE_CLIP_MAXIMUM_PHASE_DELAY_TICKS &&
        terminalHoldTicks in 0..MACE_CLIP_MAXIMUM_TERMINAL_HOLD_TICKS &&
        (this !is MaceClipResearchProbeRequest.Move ||
            distance.isFinite() && distance > 0.0 && distance <= MACE_CLIP_MAXIMUM_DISTANCE)

internal enum class MaceClipResearchProbeStartResult {
    STARTED,
    ACTIVE_PROBE,
    ACTIVE_REMOTE_KILL_SESSION,
    UNSAFE_CONTEXT,
    INVALID_CONTEXT,
    NO_TARGET,
    ROUTE_REJECTED,
    LOGGING_UNAVAILABLE,
}

internal enum class MaceClipResearchAbortResult {
    ABORT_REQUESTED,
    IDLE,
}

internal sealed interface MaceClipResearchStatus {
    data object Idle : MaceClipResearchStatus

    data class Active(
        val sessionId: String,
        val probe: MaceClipResearchProbeRequest.Probe,
        val phase: MaceClipResearchPhase?,
        val profileId: String,
        val abortRequested: Boolean,
    ) : MaceClipResearchStatus
}

internal enum class MaceClipResearchPhase {
    PRIME,
    ASCEND,
    TRANSFER,
    DESCEND,
    STRIKE,
    RETURN_ASCEND,
    RETURN_TRANSFER,
    RETURN_DESCEND,
}

internal enum class MaceClipResearchPacketDelivery {
    DELIVERED,
    QUEUED,
    CANCELLED,
}

internal enum class MaceClipResearchValidation {
    UNVALIDATED,
    VALIDATED,
}

internal enum class MaceClipResearchOutcome {
    CORRECTED,
    DELIVERY_FAILED,
    ABORTED,
    NO_CORRECTION_OBSERVED,
}
