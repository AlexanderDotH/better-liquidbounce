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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillTargetChainSelection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.buildSpearKillChainedAttackMovements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.isDirectSpearKillTargetEligible
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.beginSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.calculateSpearKillTravel
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.currentSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.selectNearestReachableSpearKillChainTarget
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.tryStartMotionChain(defeatedTarget: LivingEntity): Boolean {
    val transport = activeMovementTransport ?: return false
    val routeOrigin = player.position()
    val chainAnchor = defeatedTarget.position()
    val inheritedTargetSource = attemptTracker.current?.targetSource
    val selection = createMotionChainSelection(defeatedTarget, routeOrigin, chainAnchor, transport.stepLimit)
        ?: return false
    installMotionChain(defeatedTarget, selection, inheritedTargetSource)
    return true
}

@Suppress("LongParameterList")
private fun SpearKillModuleState.createMotionChainSelection(
    defeatedTarget: LivingEntity,
    routeOrigin: Vec3,
    chainAnchor: Vec3,
    stepLimit: Double,
): SpearKillTargetChainSelection<LivingEntity, List<Vec3>>? = selectNearestReachableSpearKillChainTarget(
        candidates = findSpearKillChainCandidates(defeatedTarget, chainAnchor),
        distanceSquared = { candidate -> chainAnchor.distanceToSqr(candidate.position()) },
        createRoute = { candidate ->
            val rawDistance = routeOrigin.distanceTo(candidate.position())
            if (rawDistance !in 3.0..maxTargetDistance.toDouble()) {
                null
            } else {
                val travel = calculateSpearKillTravel(rawDistance)
                if (!isDirectSpearKillTargetEligible(candidate, travel)) {
                    null
                } else {
                    val roundTrip = createDirectAttackMovements(
                        target = candidate,
                        distance = travel,
                        profile = currentSpeedProfile(stepLimit),
                    )
                    roundTrip.take((roundTrip.size - 1) / 2).takeIf { it.isNotEmpty() }
                }
            }
        },
    )

private fun SpearKillModuleState.installMotionChain(
    defeatedTarget: LivingEntity,
    selection: SpearKillTargetChainSelection<LivingEntity, List<Vec3>>,
    inheritedTargetSource: String?,
) {
    val chainedMovements = buildSpearKillChainedAttackMovements(
        outboundMovements = selection.route,
        existingReturnMovements = attackMovements.toList(),
    )
    attackMovements.clear()
    attackMovements.addAll(chainedMovements)
    handoffSpearKillRouteTarget(defeatedTarget, selection.target)
    beginSpearKillAttempt(
        target = selection.target,
        routeMode = "Direct Chain",
        outboundSteps = selection.route.size,
        hitTicks = selection.route.size,
        terminalAuthorizationRequired = false,
        targetSourceOverride = inheritedTargetSource,
    )
}
