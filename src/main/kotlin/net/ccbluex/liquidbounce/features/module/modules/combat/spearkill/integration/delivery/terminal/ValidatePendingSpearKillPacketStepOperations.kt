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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SPEAR_KILL_EXPERIMENTAL_MAX_SPEED
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_RECOVERY_STEP_EPSILON
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPendingPacketStepValidation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.activeInstantEndpointOnly
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.activePacketRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.isSpearKillPrimedInstantStepAdmissible
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.hasValidAStarTerminalAttackRay
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarAttackApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.isSpearKillPacketStepClear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.isSpearKillPrimedEndpointFree
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.previewSpearKillOutboundStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.pendingFinalOutboundStep
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.world.phys.Vec3

/**
 * Revalidates the one pending virtual movement against the live world immediately before it
 * reaches the packet pipeline. Planning validates the same edge up front, but a chunk or
 * collision can change while the session is in progress.
 */
internal fun SpearKillModuleState.validatePendingSpearKillPacketStep(): SpearKillPendingPacketStepValidation {
    if (!packetBootSession.requiresDelivery) return SpearKillPendingPacketStepValidation.BLOCKED

    val sessionOrigin = packetSessionOrigin ?: return SpearKillPendingPacketStepValidation.BLOCKED
    val movement = packetBootSession.pendingMovement ?: return SpearKillPendingPacketStepValidation.BLOCKED
    if (activePacketRoutingMode == SpearKillRoutingMode.INSTANT) {
        return validatePendingSpearKillInstantStep(sessionOrigin)
    }
    return validatePendingSpearKillRoutedStep(sessionOrigin, movement)
}

private fun SpearKillModuleState.validatePendingSpearKillRoutedStep(
    sessionOrigin: Vec3,
    movement: Vec3,
): SpearKillPendingPacketStepValidation {
    val outboundStepLimit = if (packetBootSession.pendingOutboundStep) {
        previewSpearKillOutboundStep().stepLimit
    } else {
        activeMovementTransport?.stepLimit ?: SPEAR_KILL_EXPERIMENTAL_MAX_SPEED.toDouble()
    }
    if (packetBootSession.pendingOutboundStep &&
        movement.length() > outboundStepLimit + SPEAR_KILL_RECOVERY_STEP_EPSILON
    ) {
        return SpearKillPendingPacketStepValidation.BUDGET_EXCEEDED
    }
    val sessionBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin)
    val segmentValidator = if (packetAStarAttackActive) {
        createServerMovementSpearKillSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
        )
    } else {
        createServerValidatedSpearKillDirectPacketSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
        )
    }
    return if (isSpearKillPacketStepClear(
        sessionOrigin = sessionOrigin,
        committedOffset = packetBootSession.committedOffset,
        candidateOffset = packetBootSession.virtualOffset,
        maxStepLength = outboundStepLimit,
        segmentValidator = segmentValidator,
    )) {
        SpearKillPendingPacketStepValidation.CLEAR
    } else {
        SpearKillPendingPacketStepValidation.BLOCKED
    }
}

internal fun SpearKillModuleState.validatePendingSpearKillInstantStep(
    sessionOrigin: Vec3,
): SpearKillPendingPacketStepValidation {
    val destination = sessionOrigin.add(packetBootSession.virtualOffset)
    val target = lockedAStarTarget
    val routeOrigin = sessionOrigin.add(packetBootSession.committedOffset)
    val terminalOutboundStep = packetBootSession.pendingFinalOutboundStep
    val targetValid = target == null || isLockedTargetEligibleAt(target, routeOrigin)
    val terminalRaytraceClear = !terminalOutboundStep || target == null ||
        targetValid && hasValidAStarTerminalAttackRay(
        targetBox = target.boundingBox,
        eyeOffset = player.eyePosition.subtract(player.position()),
        approach = SpearKillAStarAttackApproach(
            plannerGoal = routeOrigin,
            terminalWaypoint = destination,
        ),
    )
    val serverMovementClear = activeInstantEndpointOnly ||
        createServerValidatedSpearKillDirectPacketSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin),
        ).isClear(routeOrigin, destination)
    return if (isSpearKillPrimedInstantStepAdmissible(
            endpointFree = serverMovementClear &&
                isSpearKillPrimedEndpointFree(sessionOrigin, destination),
            outboundStep = packetBootSession.pendingOutboundStep,
            terminalOutboundStep = terminalOutboundStep,
            attackTargetPresent = target != null,
            targetValid = targetValid,
            terminalRaytraceClear = terminalRaytraceClear,
        )
    ) {
        SpearKillPendingPacketStepValidation.CLEAR
    } else {
        SpearKillPendingPacketStepValidation.BLOCKED
    }
}
