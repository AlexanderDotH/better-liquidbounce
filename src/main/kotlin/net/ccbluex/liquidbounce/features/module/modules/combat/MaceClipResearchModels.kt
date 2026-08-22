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
package net.ccbluex.liquidbounce.features.module.modules.combat

internal const val MACE_CLIP_RESEARCH_SCHEMA_VERSION = 1
internal const val MACE_CLIP_MAXIMUM_DISTANCE = 500.0
internal const val MACE_CLIP_MAXIMUM_PRIMING_PACKETS = 18
internal const val MACE_CLIP_MINIMUM_CLEARANCE = 4.0
internal const val MACE_CLIP_MAXIMUM_CLEARANCE = 128.0
internal const val MACE_CLIP_MAXIMUM_PHASE_DELAY_TICKS = REMOTE_KILL_ROUTE_MAX_STEP_WAIT_TICKS
internal const val MACE_CLIP_MAXIMUM_TERMINAL_HOLD_TICKS = 20
internal const val MACE_CLIP_MAXIMUM_PACKET_BUDGET = 256

/** Packet shapes shared with the proven SpearKill one-shot research vocabulary. */
internal enum class MaceClipResearchPacketShape(
    val spearEquivalent: SpearKillHighSpeedResearchFinalPacketType,
) {
    POSITION(SpearKillHighSpeedResearchFinalPacketType.POSITION),
    POSITION_ROTATION(SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION),
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

internal data class MaceClipResearchPlugin(
    val name: String,
    val version: String,
    val sha256: String,
)

internal data class MaceClipResearchProfile(
    val id: String,
    val validation: MaceClipResearchValidation,
    val minecraftVersion: String,
    val protocolVersion: Int,
    val paperBuildId: Int,
    val paperDownloadUrl: String,
    val paperSha256: String,
    val javaVersion: Int,
    val plugins: List<MaceClipResearchPlugin>,
)

internal object MaceClipResearchProfiles {
    val PAPER_26_2_BUILD_112 = MaceClipResearchProfile(
        id = "paper-26.2-build-112-unvalidated",
        validation = MaceClipResearchValidation.UNVALIDATED,
        minecraftVersion = "26.2",
        protocolVersion = 776,
        paperBuildId = 112,
        paperDownloadUrl = "https://fill-data.papermc.io/v1/objects/" +
            "bd3a58cf96874e5ea6643f5f6fe9b4f5bf9e34b795fa078c2f0ee8b98b2f907e/paper-26.2-112.jar",
        paperSha256 = "bd3a58cf96874e5ea6643f5f6fe9b4f5bf9e34b795fa078c2f0ee8b98b2f907e",
        javaVersion = 25,
        plugins = listOf(
            MaceClipResearchPlugin(
                name = "MaceKillLabObserver",
                version = "0.1.0",
                sha256 = "b84faf38c6db14618a71bc31409be3e36e52832bb92aed472e8bca517a25076c",
            )
        ),
    )
}

internal typealias MaceClipResearchPosition = SpearKillHighSpeedResearchVector

internal data class MaceClipResearchTargetStart(
    val entityId: Int,
    val name: String,
    val health: Double,
)

@Suppress("LongParameterList")
internal data class MaceClipResearchStart(
    val clientTick: Int,
    val request: MaceClipResearchProbeRequest,
    val profile: MaceClipResearchProfile,
    val packetBudget: Int,
    val origin: net.minecraft.world.phys.Vec3,
    val targetPosition: net.minecraft.world.phys.Vec3?,
    val attackEndpoint: net.minecraft.world.phys.Vec3,
    val apex: net.minecraft.world.phys.Vec3,
    val localPositionBefore: net.minecraft.world.phys.Vec3,
    val target: MaceClipResearchTargetStart?,
)

internal enum class MaceClipResearchBeginRejection {
    ACTIVE_PROBE,
    INVALID_REQUEST,
    INVALID_START,
    LOGGING_UNAVAILABLE,
}

internal sealed interface MaceClipResearchBeginResult {
    data class Started(val sessionId: String) : MaceClipResearchBeginResult
    data class Rejected(val reason: MaceClipResearchBeginRejection) : MaceClipResearchBeginResult
}

internal data class MaceClipResearchTiming(
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val startedAtMonotonicNanos: Long,
    val completedAtMonotonicNanos: Long,
    val clientTick: Int,
    val completionTick: Int,
)

internal data class MaceClipResearchPhaseEvidence(
    val phase: MaceClipResearchPhase,
    val startedTick: Int,
    val completedTick: Int?,
    val startPosition: MaceClipResearchPosition,
    val endPosition: MaceClipResearchPosition?,
)

@Suppress("LongParameterList")
internal data class MaceClipResearchPacketEvidence(
    val sequence: Int,
    val phase: MaceClipResearchPhase,
    val tick: Int,
    val shape: MaceClipResearchPacketShape,
    val position: MaceClipResearchPosition,
    val onGround: Boolean,
    val delivery: MaceClipResearchPacketDelivery,
)

internal data class MaceClipResearchCorrectionEvidence(
    val phase: MaceClipResearchPhase,
    val tick: Int,
    val receivedAtEpochMs: Long,
    val expected: MaceClipResearchPosition,
    val actual: MaceClipResearchPosition,
    val distance: Double,
)

@Suppress("LongParameterList")
internal data class MaceClipResearchPositions(
    val origin: MaceClipResearchPosition,
    val target: MaceClipResearchPosition?,
    val attackEndpoint: MaceClipResearchPosition,
    val apex: MaceClipResearchPosition,
    val localBefore: MaceClipResearchPosition,
    val localAfter: MaceClipResearchPosition,
    val lastAuthoritativeCorrection: MaceClipResearchPosition?,
    val observedLocalDisplacement: Double,
)

internal data class MaceClipResearchDeliveryEvidence(
    val packetBudget: Int,
    val packetsSent: Int,
    val packetsDelivered: Int,
    val packetsQueued: Int,
    val packetsCancelled: Int,
    val exactReturnDelivered: Boolean,
)

internal data class MaceClipResearchTargetEvidence(
    val entityId: Int,
    val name: String,
    val healthBefore: Double,
    val healthAfter: Double,
    val observedHealthDelta: Double,
    val damageEventObserved: Boolean,
    val damageEventAmount: Double?,
    val deathObserved: Boolean,
)

internal data class MaceClipResearchStrikeEvidence(
    val attempts: Int,
    val committedAttacks: Int,
)

@Suppress("LongParameterList")
internal data class MaceClipResearchEntry(
    val schemaVersion: Int = MACE_CLIP_RESEARCH_SCHEMA_VERSION,
    val sessionId: String,
    val profile: MaceClipResearchProfile,
    val request: MaceClipResearchProbeRequest,
    val timing: MaceClipResearchTiming,
    val phases: List<MaceClipResearchPhaseEvidence>,
    val packets: List<MaceClipResearchPacketEvidence>,
    val corrections: List<MaceClipResearchCorrectionEvidence>,
    val positions: MaceClipResearchPositions,
    val delivery: MaceClipResearchDeliveryEvidence,
    val strike: MaceClipResearchStrikeEvidence,
    val target: MaceClipResearchTargetEvidence?,
    val abortRequested: Boolean,
    val outcome: MaceClipResearchOutcome,
)
