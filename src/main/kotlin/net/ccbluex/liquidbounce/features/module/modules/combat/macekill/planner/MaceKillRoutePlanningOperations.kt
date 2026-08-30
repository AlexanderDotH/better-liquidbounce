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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*
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

internal fun MaceKillModuleState.buildMaceKillRoute(
    origin: Vec3,
    endpoint: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
    originBoundingBox: AABB = player.boundingBox,
    allowVanillaVClip: Boolean = true,
): MaceKillPlannedRoute? {
    lastInstantPlanBlockReason = null
    if (configuration.timing.transport == MaceKillRouteTransport.MOTION) {
        return buildMaceKillMotionRoute(origin, endpoint, configuration, originBoundingBox, allowVanillaVClip)
    }
    return buildMaceKillPacketRoute(origin, endpoint, configuration, originBoundingBox, allowVanillaVClip)
}

private fun MaceKillModuleState.buildMaceKillMotionRoute(
    origin: Vec3,
    endpoint: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
    originBoundingBox: AABB,
    allowVanillaVClip: Boolean,
): MaceKillPlannedRoute? = selectMaceKillMotionRoutePlan(
    collisionPlan = {
        buildCollisionAwareRoute(origin, endpoint, configuration, originBoundingBox)
            ?.toMaceKillPlan(origin, stepWaitTicks = 0, motion = true)
    },
    vanillaVClipPlan = {
        if (allowVanillaVClip) {
            buildMaceKillVanillaVClipRoute(
                origin, endpoint, configuration, originBoundingBox, motion = true,
            )
        } else {
            null
        }
    },
)

private fun MaceKillModuleState.buildMaceKillPacketRoute(
    origin: Vec3,
    endpoint: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
    originBoundingBox: AABB,
    allowVanillaVClip: Boolean,
): MaceKillPlannedRoute? = selectMaceKillRoutePlan(
        routingMode = configuration.routingMode,
        directPlan = {
            buildCollisionAwareRoute(origin, endpoint, configuration, originBoundingBox)
                ?.toMaceKillPlan(origin, configuration.timing.stepWaitTicks)
        },
        aStarPlan = {
            buildAStarRoute(origin, endpoint, configuration, originBoundingBox)
                ?.toMaceKillPlan(origin, configuration.timing.stepWaitTicks)
        },
        vanillaVClipPlan = {
            if (allowVanillaVClip) {
                buildMaceKillVanillaVClipRoute(
                    origin,
                    endpoint,
                    configuration,
                    originBoundingBox,
                )
            } else {
                null
            }
        },
        wallClipPlan = { buildInstantRoute(origin, endpoint, configuration) },
)

internal fun MaceKillModuleState.buildInstantRoute(
    origin: Vec3,
    endpoint: Vec3,
    configuration: MaceKillRouteExecutionConfiguration,
): MaceKillPlannedRoute? {
    val instant = movementConfiguration.packet.instant
    val request = MaceClipReachPlanRequest(
        origin = origin,
        endpoint = endpoint,
        dimensionBounds = MaceClipReachDimensionBounds(world.minY.toDouble(), world.maxY.toDouble()),
        profile = MaceClipReachProfile.experimental(
        MaceClipReachResearchParameters(
            primingPacketCount = instant.primingPackets,
            clearanceHeight = instant.clearanceHeight.toDouble(),
            maxTargetDistance = maximumTargetRange.toDouble(),
            maxMovementPackets = instant.maxPackets,
            timeoutTicks = MACE_KILL_INSTANT_TIMEOUT_TICKS,
        ),
        ),
        use = MaceClipReachUse.EXPERIMENTAL,
        anchorValidator = MaceClipReachAnchorValidator { _, position -> isMaceKillAnchorValid(origin, position) },
    )
    return resolveMaceKillInstantRoute(MaceClipReachPlanner.plan(request), configuration, instant.maxPackets)
}

private fun MaceKillModuleState.resolveMaceKillInstantRoute(
    result: MaceClipReachPlanResult,
    configuration: MaceKillRouteExecutionConfiguration,
    packetBudget: Int,
): MaceKillPlannedRoute? = when (result) {
        is MaceClipReachPlanResult.Ready -> {
            if (maceKillInstantRoundTripPacketCount(result.plan) > packetBudget) {
                lastInstantPlanBlockReason = MaceClipReachBlockReason.PACKET_BUDGET_EXCEEDED
                null
            } else {
                maceKillInstantPlannedRoute(result.plan, configuration.timing.stepWaitTicks)
            }
        }
        is MaceClipReachPlanResult.Blocked -> {
            lastInstantPlanBlockReason = result.reason
            null
        }
    }
