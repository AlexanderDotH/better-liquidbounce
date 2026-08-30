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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction

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
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

internal fun MaceKillModuleState.resolveStandardMaceKillCorrectionMovements(
    request: MaceKillStandardCorrectionRequest,
): List<Vec3>? = with(request) {
    val clipInverse = if (instant) {
        instantRecoveryPlan?.let { maceKillFullInverseRecovery(it, authoritativePosition) }
    } else {
        null
    }
    val inverse = clipInverse ?: routeSession.exactRecoveryMovementsFrom(authoritativeOffset)
    val configuration = activeRouteConfiguration
        ?: currentMaceKillRouteExecutionConfiguration(MaceKillRouteOwner.KILL_AURA)
    val recoveryConfiguration = if (instant) {
        maceKillInstantCorrectionRecoveryConfiguration(configuration)
    } else {
        configuration
    }
    val collisionRecovery = planMaceKillCollisionCorrectionRecovery(
        correctionPlayer,
        state,
        authoritativePosition,
        inverse,
        instant,
        action,
        recoveryConfiguration,
    )
    val forcedRecovery = if (instant) {
        maceKillForcedOriginRecovery(authoritativePosition, state.routeOrigin)
    } else {
        null
    }
    return selectMaceKillCorrectionRecoveryMovements(
        action = action,
        inverseRecovery = inverse,
        collisionRecovery = collisionRecovery,
        forcedRecovery = forcedRecovery,
    )
}

internal data class MaceKillStandardCorrectionRequest(
    val correctionPlayer: Player,
    val state: MaceKillLocalCorrectionState,
    val authoritativePosition: Vec3,
    val authoritativeOffset: Vec3,
    val instant: Boolean,
    val action: MaceKillCorrectionRecoveryAction,
)

internal fun MaceKillModuleState.planMaceKillResearchCorrectionRecovery(
    origin: Vec3,
    authoritativePosition: Vec3,
    session: MaceClipReachSession,
): List<Vec3>? {
    val preferredApexY = session.plan.steps.asSequence()
        .filter { it.evidencePhase == MaceClipReachEvidencePhase.ASCEND ||
            it.evidencePhase == MaceClipReachEvidencePhase.TRANSFER }
        .maxOfOrNull { it.position.y }
        ?: session.plan.steps.maxOf { it.position.y }
    val result = MaceClipReachPlanner.planCorrectionRecovery(
        MaceClipReachRecoveryRequest(
            authoritativePosition = authoritativePosition,
            origin = origin,
            preferredApexY = preferredApexY,
            dimensionBounds = session.plan.dimensionBounds,
            maxMovementPackets = session.plan.profile.parameters.maxMovementPackets,
            anchorValidator = MaceClipReachAnchorValidator { _, position ->
                isMaceKillAnchorValid(origin, position)
            },
        ),
    )
    return (result as? MaceClipReachRecoveryResult.Ready)?.movements
}

internal fun MaceKillModuleState.planMaceKillCollisionCorrectionRecovery(
    correctionPlayer: Player,
    state: MaceKillLocalCorrectionState,
    authoritativePosition: Vec3,
    inverseRecovery: List<Vec3>?,
    instant: Boolean,
    action: MaceKillCorrectionRecoveryAction,
    configuration: MaceKillRouteExecutionConfiguration,
): List<Vec3>? {
    if (inverseRecovery != null) return null
    if (instant && action != MaceKillCorrectionRecoveryAction.RECOVER_COLLISION_DERIVED) return null
    val recoveryBox = maceKillBoundingBoxAtRouteOrigin(
        correctionPlayer.boundingBox,
        correctionPlayer.position(),
        authoritativePosition,
    )
    return buildCollisionAwareRoute(
        authoritativePosition,
        state.routeOrigin,
        configuration,
        recoveryBox,
    )?.outboundMovements ?: buildAStarRoute(
        authoritativePosition,
        state.routeOrigin,
        configuration,
        recoveryBox,
    )?.outboundMovements
}
