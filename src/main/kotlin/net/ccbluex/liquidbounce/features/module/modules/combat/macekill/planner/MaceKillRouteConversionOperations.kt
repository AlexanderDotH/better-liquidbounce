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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*

import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
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

internal fun SpearKillAStarPacketRoute.toMaceKillPlan(
    origin: Vec3,
    stepWaitTicks: Int,
    motion: Boolean = false,
    prefixMovements: List<Vec3> = emptyList(),
    suffixMovements: List<Vec3> = emptyList(),
    vanillaVClipSegments: Set<MaceKillVanillaVClipSegment> = emptySet(),
): MaceKillPlannedRoute {
    val allOutboundMovements = prefixMovements + outboundMovements + suffixMovements
    val request = RemoteKillRouteRequest(
        origin = origin,
        outboundMovements = allOutboundMovements,
        strikeHoldTicks = MACE_KILL_CHAIN_EVIDENCE_HOLD_TICKS,
        stepWaitTicks = stepWaitTicks,
        physicalReturn = motion,
    )
    return MaceKillPlannedRoute(
        request = request,
        renderPath = buildMaceKillRoutePositions(origin, allOutboundMovements),
        motion = motion,
        vanillaVClipSegments = vanillaVClipSegments,
    )
}

internal fun MaceKillModuleState.createMaceKillSegmentValidator(
    origin: Vec3,
    originBoundingBox: AABB = player.boundingBox,
    allowedVanillaVClipSegments: Set<MaceKillVanillaVClipSegment> = emptySet(),
): SpearKillAStarSegmentValidator {
    val collisionValidator = createMaceKillServerPacketSegmentValidator(
        origin = origin,
        playerBoundingBox = originBoundingBox,
        hasDestinationCollision = { box ->
            withVanillaSpearKillBlockShapes { !world.noCollision(player, box) }
        },
        resolveMovement = { box, movement ->
            withVanillaSpearKillBlockShapes { resolveMaceKillServerPacketMovement(player, box, movement) }
        },
    )
    return SpearKillAStarSegmentValidator { from, to ->
        allowedVanillaVClipSegments.any { segment ->
            segment.matches(from, to) &&
                isMaceKillAnchorValid(origin, from, originBoundingBox) &&
                isMaceKillAnchorValid(origin, to, originBoundingBox)
        } || collisionValidator.isClear(from, to)
    }
}

internal fun MaceKillModuleState.predictedMaceKillTarget(
    target: LivingEntity,
    origin: Vec3,
    timing: MaceKillRouteTiming,
): MaceKillRouteTargetPrediction {
    val travelTicks = timing.predictedTravelTicks(origin.distanceTo(target.position()))
    return predictMaceKillRouteTarget(target, travelTicks)
}

internal fun MaceKillModuleState.findMaceKillAttackEndpoint(
    target: LivingEntity,
    origin: Vec3,
    targetPosition: Vec3 = target.position(),
    targetEyePosition: Vec3 = target.eyePosition,
    requireAttackCooldown: Boolean = true,
): Vec3? {
    val clearance = (player.bbWidth + target.bbWidth).toDouble() / 2.0 + 0.2
    return MaceKillEndpointPlanner.find(
        MaceKillEndpointSearchRequest(
            origin = origin,
            targetPosition = targetPosition,
            minimumClearance = clearance,
            maximumRadius = MACE_KILL_ENDPOINT_MAX_SEARCH_RADIUS,
        ),
    ) { endpoint ->
        isRemoteEndpointReady(
            player,
            target,
            endpoint,
            targetEyePosition,
            requireAttackCooldown,
        )
    }
}
