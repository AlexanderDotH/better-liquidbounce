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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
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

internal fun MaceKillModuleState.beginMaceKillFallSafety(planned: MaceKillPlannedRoute): Boolean = beginMaceKillFallSafety(
    request = planned.request,
    groundPolicy = if (planned.clipReachPlan == null) {
        MaceKillGroundPolicy.COLLISION_DERIVED
    } else {
        MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF
    },
    vanillaVClipSegments = planned.vanillaVClipSegments,
)

internal fun MaceKillModuleState.beginMaceKillFallSafety(
    request: RemoteKillRouteRequest,
    groundPolicy: MaceKillGroundPolicy = MaceKillGroundPolicy.COLLISION_DERIVED,
    vanillaVClipSegments: Set<MaceKillVanillaVClipSegment> = emptySet(),
): Boolean {
    val originGrounded = isMaceKillPositionNearGround(request.origin)
    val finalPosition = request.returnMovements.fold(request.endpoint, Vec3::add)
    val routeReturnsExactly = finalPosition.distanceToSqr(request.origin) < MACE_KILL_EXACT_RETURN_EPSILON_SQUARED
    if (!canBeginMaceKillFallSafetyAtOrigin(originGrounded, routeReturnsExactly)) {
        reportMaceKillFallSafetyRejection(
            "airborne-origin-without-exact-return",
            "origin" to request.origin,
            "player" to player.position(),
        )
        return false
    }
    val inputs = maceKillFallSafetyInputs(request, groundPolicy, vanillaVClipSegments, originGrounded)
    val preflight = preflightMaceKillFallSafety(
            inputs.initialFallDistance,
            inputs.safeFallDistance,
            inputs.steps,
        )
    if (preflight != MaceKillFallSafetyPreflight.Safe) {
        reportMaceKillFallSafetyPreflightRejection(preflight, inputs)
        return false
    }
    val result = createMaceKillFallSafetyPlan(inputs, request.outboundMovements.size)
    val ready = result as? MaceKillServerFallSafetyPlanResult.Ready
    if (ready == null) {
        reportMaceKillFallSafetyRejection(
            result,
            "initial-fall-distance" to inputs.initialFallDistance,
            "safe-fall-distance" to inputs.safeFallDistance,
        )
        return false
    }
    fallSafetyLifecycle.begin(ready.plan)
    return true
}

private fun createMaceKillFallSafetyPlan(
    inputs: MaceKillFallSafetyInputs,
    outboundStepCount: Int,
) = MaceKillServerFallSafetyPlan.createForMovements(
    movements = inputs.movements,
    outboundStepCount = outboundStepCount,
    initialFallDistance = inputs.initialFallDistance,
    safeFallDistance = inputs.safeFallDistance,
    groundedSteps = inputs.steps.map(MaceKillFallSafetyStep::grounded),
    expectedNetMovement = Vec3.ZERO,
)

private fun MaceKillModuleState.maceKillFallSafetyInputs(
    request: RemoteKillRouteRequest,
    groundPolicy: MaceKillGroundPolicy,
    vanillaVClipSegments: Set<MaceKillVanillaVClipSegment>,
    originGrounded: Boolean,
): MaceKillFallSafetyInputs {
    val movements = request.outboundMovements + request.returnMovements
    return MaceKillFallSafetyInputs(
        movements = movements,
        steps = maceKillFallSafetySteps(request.origin, movements, groundPolicy, vanillaVClipSegments),
        initialFallDistance = player.fallDistance.toDouble(),
        safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        originGrounded = originGrounded,
    )
}

private fun MaceKillModuleState.reportMaceKillFallSafetyPreflightRejection(
    preflight: MaceKillFallSafetyPreflight,
    inputs: MaceKillFallSafetyInputs,
) = reportMaceKillFallSafetyRejection(
    preflight,
    "origin-grounded" to inputs.originGrounded,
    "initial-fall-distance" to inputs.initialFallDistance,
    "safe-fall-distance" to inputs.safeFallDistance,
    "grounded-steps" to inputs.steps.map(MaceKillFallSafetyStep::grounded),
)

private fun MaceKillModuleState.reportMaceKillFallSafetyRejection(
    reason: Any?,
    vararg details: Pair<String, Any?>,
) {
    debugMaceKill("fall-safety-reject") { listOf("reason" to reason) + details }
}

private data class MaceKillFallSafetyInputs(
    val movements: List<Vec3>,
    val steps: List<MaceKillFallSafetyStep>,
    val initialFallDistance: Double,
    val safeFallDistance: Double,
    val originGrounded: Boolean,
)

internal fun MaceKillModuleState.replanMaceKillFallSafety(
    start: Vec3,
    movements: List<Vec3>,
    outboundStepCount: Int,
    groundPolicy: MaceKillGroundPolicy = MaceKillGroundPolicy.COLLISION_DERIVED,
    vanillaVClipSegments: Set<MaceKillVanillaVClipSegment> = activeVanillaVClipSegments,
): Boolean {
    if (movements.isEmpty()) {
        fallSafetyLifecycle.invalidate()
        return true
    }
    val initialFallDistance = fallSafetyLifecycle.confirmedFallDistance.takeIf { fallSafetyLifecycle.active }
        ?: player.fallDistance
    val safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
    val steps = maceKillFallSafetySteps(start, movements, groundPolicy, vanillaVClipSegments)
    if (preflightMaceKillFallSafety(
            initialFallDistance,
            safeFallDistance,
            steps,
        ) != MaceKillFallSafetyPreflight.Safe
    ) {
        return false
    }
    val result = MaceKillServerFallSafetyPlan.createForMovements(
        movements = movements,
        outboundStepCount = outboundStepCount,
        initialFallDistance = initialFallDistance,
        safeFallDistance = safeFallDistance,
        groundedSteps = steps.map(MaceKillFallSafetyStep::grounded),
        expectedNetMovement = movements.fold(Vec3.ZERO, Vec3::add),
    ) as? MaceKillServerFallSafetyPlanResult.Ready ?: return false
    fallSafetyLifecycle.replan(result.plan)
    return true
}

internal fun MaceKillModuleState.maceKillFallSafetySteps(
    start: Vec3,
    movements: List<Vec3>,
    groundPolicy: MaceKillGroundPolicy,
    vanillaVClipSegments: Set<MaceKillVanillaVClipSegment>,
): List<MaceKillFallSafetyStep> {
    var position = start
    val clipReachSpoofed = groundPolicy.shouldSpoofOnGround(
        MaceKillGroundPacketContext(
            identityOwnedByRoute = true,
            kind = MaceKillMovementPacketKind.CLIP_REACH_ANCHOR,
        ),
    )
    return movements.map { movement ->
        val previousPosition = position
        position = position.add(movement)
        val vanillaVClipSpoofed = vanillaVClipSegments.any { it.matches(previousPosition, position) }
        MaceKillFallSafetyStep(
            movement = movement,
            grounded = clipReachSpoofed || vanillaVClipSpoofed || isMaceKillPositionNearGround(position),
            groundSpoofed = clipReachSpoofed || vanillaVClipSpoofed,
        )
    }
}
