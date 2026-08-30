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
internal data class MaceClipResearchPosition(
    val x: Double,
    val y: Double,
    val z: Double,
)

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
