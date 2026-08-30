/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarAttackApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchFinalPacketType

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal object FightBotSpearUseRequester

@Suppress("LongParameterList")
internal data class AStarAttackPlan(
    val approach: SpearKillAStarAttackApproach,
    val packetRoute: SpearKillAStarPacketRoute,
    val renderPath: List<Vec3>,
    val targetPosition: Vec3,
    val targetVelocity: Vec3,
    val schedule: SpearKillPathSchedule,
    val preStrikeHoldTicks: Int,
    val terminalSuffixCount: Int,
)

internal data class AStarSpatialPlan(
    val approach: SpearKillAStarAttackApproach,
    val packetRoute: SpearKillAStarPacketRoute,
    val renderPath: List<Vec3>,
    val terminalSuffixCount: Int,
)

internal data class DirectPacketRoutePlan(
    val route: SpearKillAStarPacketRoute,
    val targetSnapshot: SpearKillRouteTargetSnapshot,
)

internal data class InstantDirectRouteCandidate(
    val route: SpearKillAStarPacketRoute,
    val targetBox: AABB,
    val approach: SpearKillAStarAttackApproach,
)

internal data class SpearKillPlayerRouteSnapshot(
    val eyeOffset: Vec3,
    val lookAngle: Vec3,
    val sessionBoundingBox: AABB,
    val speedProfile: SpearKillSpeedProfile,
    val safeVerticalStep: Double,
    val maximumTargetDistance: Double,
)

internal data class SpearKillPacketSessionSettings(
    val transport: SpearKillMovementTransport,
    val stepWaitTicks: Int,
    val routingMode: SpearKillRoutingMode,
    val aStar: SpearKillAStarSessionSettings,
    val damageEvidenceWindowTicks: Int,
    val setbackBackoffTicks: Int,
    val allowTerminalBurst: Boolean,
    val instantMaxPackets: Int,
    val primedInstant: Boolean,
    val priming: SpearKillPrimedInstantPriming,
    val primingPacketType: SpearKillPrimedInstantPacketType,
    val researchLog: Boolean,
    val finalPacketType: SpearKillHighSpeedResearchFinalPacketType,
) {
    val networkOptimized: Boolean
        get() = routingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED

    val strikeHoldTicks: Int
        get() = spearKillStrikeHoldTicks(routingMode)
}

internal data class SpearKillAStarSessionSettings(
    val maxCost: Int,
    val diagonal: Boolean,
    val lineOfSightShortcuts: Boolean,
)

internal data class PacketChainPlan(
    val outboundMovements: List<Vec3>,
    val routeMode: String,
    val hitTicks: Int,
    val strikeHoldTicks: Int,
    val terminalBurstSteps: Int = 0,
    val preStrikeHoldTicks: Int = 0,
    val terminalAuthorizationRequired: Boolean = false,
    val aStarPlan: AStarAttackPlan? = null,
)

internal data class InstantStepDelivery(
    val packetsSent: Int,
    val continueBurst: Boolean,
)

internal data class SpearKillOwnedPacketDelivery(
    val delivered: Boolean,
    val blinkQueued: Boolean,
)

internal data class SpearKillPrimingSequenceDelivery(
    val packetsSent: Int,
    val delivered: Boolean,
)

internal data class SpearKillPrimedPendingStep(
    val plan: SpearKillPrimedInstantPlan,
    val noFallPacketRequired: Boolean,
    val origin: Vec3,
    val destination: Vec3,
    var noFallPacketDelivered: Boolean = false,
    var burstId: String? = null,
)

internal sealed interface SpearKillPrimedPendingStepPreparation {
    data class Ready(val step: SpearKillPrimedPendingStep) : SpearKillPrimedPendingStepPreparation
    data object Defer : SpearKillPrimedPendingStepPreparation
    data object Block : SpearKillPrimedPendingStepPreparation
}

internal enum class PacketFollowTermination(
    val rejectTarget: Boolean,
    val notificationKey: String?,
) {
    DEFEATED(rejectTarget = false, notificationKey = null),
    UNREACHABLE(rejectTarget = true, notificationKey = "targetUnreachable"),
    BLOCKED(rejectTarget = true, notificationKey = "pathBlocked"),
}

internal enum class PacketChainStartResult {
    STARTED,
    FAILED,
}
