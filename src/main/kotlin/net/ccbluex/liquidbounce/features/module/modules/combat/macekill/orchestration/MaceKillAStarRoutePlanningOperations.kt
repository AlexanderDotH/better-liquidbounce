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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.MaceKillRouteExecutionConfiguration
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.MaceKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.MaceKillVanillaVClipSegment
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.maceKillAStarIterationBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.maceKillAStarNodePosition
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.maceKillVanillaVClipCandidates
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.*
import net.ccbluex.liquidbounce.utils.entity.*
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.core.*
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.*
import net.minecraft.world.entity.player.*
import net.minecraft.world.item.*
import net.minecraft.world.phys.*

internal fun MaceKillModuleState.buildCollisionAwareRoute(
    origin: Vec3,
    endpoint: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
    originBoundingBox: AABB = player.boundingBox,
): SpearKillAStarPacketRoute? = integration.buildProfiledAStarPacketRoute(
    origin = origin,
    outboundWaypoints = listOf(endpoint),
    profile = currentMaceKillSpeedProfile(configuration),
    segmentValidator = createMaceKillSegmentValidator(origin, originBoundingBox),
)

internal fun MaceKillModuleState.buildAStarRoute(
    origin: Vec3,
    endpoint: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
    originBoundingBox: AABB = player.boundingBox,
): SpearKillAStarPacketRoute? {
    val validator = createMaceKillSegmentValidator(origin, originBoundingBox)
    val startNode = BlockPos.containing(origin)
    val endNode = BlockPos.containing(endpoint)
    val planner = createMaceKillAStarPlanner(
        origin, endpoint, configuration, originBoundingBox, startNode, endNode, validator,
    )
    val waypoints = resolveSpearKillAStarApproachRoute(
        origin = origin,
        plannerGoal = endpoint,
        segmentValidator = validator,
    ) { planner.plan(origin, endpoint) } ?: return null
    val compacted = compactSpearKillAStarWaypoints(
        origin, waypoints, configuration.timing.stepDistance, validator, configuration.lineOfSightShortcuts,
    )
    return integration.buildProfiledAStarPacketRoute(
        origin = origin,
        outboundWaypoints = compacted.ifEmpty { listOf(endpoint) },
        profile = currentMaceKillSpeedProfile(configuration),
        segmentValidator = validator,
    )
}

private fun MaceKillModuleState.createMaceKillAStarPlanner(
    origin: Vec3,
    endpoint: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
    playerBox: AABB,
    startNode: BlockPos,
    endNode: BlockPos,
    validator: SpearKillAStarSegmentValidator,
) = SpearKillAStarRoutePlanner(
        allowDiagonal = configuration.diagonal,
        maxCost = configuration.maxCost,
        maxIterations = maceKillAStarIterationBudget(configuration.maxCost),
        isPassable = { node ->
            val position = maceKillAStarNodePosition(node, startNode, endNode, origin, endpoint)
            withVanillaSpearKillBlockShapes {
                world.noCollision(player, playerBox.move(position.subtract(origin)))
            }
        },
        canTraverse = validator::isClear,
    )

/**
 * Keeps one short, explicit vanilla VClip separate from the collision-validated route around it.
 * The route engine derives the inverse return, so the same edge can only be crossed back exactly.
 */
internal fun MaceKillModuleState.buildMaceKillVanillaVClipRoute(
    origin: Vec3,
    endpoint: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
    originBoundingBox: AABB,
    motion: Boolean = false,
): MaceKillPlannedRoute? {
    for (movement in maceKillVanillaVClipCandidates(origin, endpoint)) {
        val originSegment = MaceKillVanillaVClipSegment(origin, origin.add(movement))
        buildMaceKillVanillaVClipRoute(
            origin = origin,
            endpoint = endpoint,
            configuration = configuration,
            originBoundingBox = originBoundingBox,
            segment = originSegment,
            vClipBeforeCollisionRoute = true,
            motion = motion,
        )?.let { return it }

        val endpointSegment = MaceKillVanillaVClipSegment(endpoint.subtract(movement), endpoint)
        buildMaceKillVanillaVClipRoute(
            origin = origin,
            endpoint = endpoint,
            configuration = configuration,
            originBoundingBox = originBoundingBox,
            segment = endpointSegment,
            vClipBeforeCollisionRoute = false,
            motion = motion,
        )?.let { return it }
    }
    return null
}

@Suppress("LongParameterList")
internal fun MaceKillModuleState.buildMaceKillVanillaVClipRoute(
    origin: Vec3,
    endpoint: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
    originBoundingBox: AABB,
    segment: MaceKillVanillaVClipSegment,
    vClipBeforeCollisionRoute: Boolean,
    motion: Boolean,
): MaceKillPlannedRoute? {
    if (!isMaceKillAnchorValid(origin, segment.from, originBoundingBox) ||
        !isMaceKillAnchorValid(origin, segment.to, originBoundingBox)
    ) {
        return null
    }
    val collisionOrigin = if (vClipBeforeCollisionRoute) segment.to else origin
    val collisionEndpoint = if (vClipBeforeCollisionRoute) endpoint else segment.from
    val collisionBoundingBox = originBoundingBox.move(collisionOrigin.subtract(origin))
    val collisionRoute = when (configuration.routingMode) {
        MaceKillRoutingMode.A_STAR ->
            buildAStarRoute(collisionOrigin, collisionEndpoint, configuration, collisionBoundingBox)
        MaceKillRoutingMode.DIRECT,
        MaceKillRoutingMode.INSTANT,
        -> buildCollisionAwareRoute(collisionOrigin, collisionEndpoint, configuration, collisionBoundingBox)
    } ?: return null
    return collisionRoute.toMaceKillPlan(
        origin = origin,
        stepWaitTicks = configuration.timing.stepWaitTicks,
        motion = motion,
        prefixMovements = if (vClipBeforeCollisionRoute) listOf(segment.movement) else emptyList(),
        suffixMovements = if (vClipBeforeCollisionRoute) emptyList() else listOf(segment.movement),
        vanillaVClipSegments = setOf(segment),
    )
}
