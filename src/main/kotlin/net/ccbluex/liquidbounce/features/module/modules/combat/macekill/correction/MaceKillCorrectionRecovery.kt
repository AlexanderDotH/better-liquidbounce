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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.MaceClipReachSession
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

internal data class MaceKillCorrectionRecovery(
    val state: MaceKillLocalCorrectionState,
    val authoritativePosition: Vec3,
    val authoritativeOffset: Vec3,
    val action: MaceKillCorrectionRecoveryAction,
    val instant: Boolean,
    val researchClip: Boolean,
    val movements: List<Vec3>,
)

internal fun MaceKillModuleState.prepareMaceKillCorrectionRecovery(
    correctionPlayer: Player,
    state: MaceKillLocalCorrectionState,
    authoritativePosition: Vec3,
    authoritativeOffset: Vec3,
): MaceKillCorrectionRecovery? {
    val instant = activeRouteConfiguration?.routingMode == MaceKillRoutingMode.INSTANT
    val action = maceKillCorrectionRecoveryAction(correctionRecoveryAttempts)
    if (instant) correctionRecoveryAttempts++
    correctionPlayer.setPos(state.routeOrigin)
    correctionPlayer.deltaMovement = Vec3.ZERO
    val clipSession = activeClipReachSession
    val researchClip = clipSession != null && activeRouteOwner == MaceKillRouteOwner.RESEARCH
    val movements = resolveMaceKillCorrectionMovements(
        correctionPlayer,
        state,
        authoritativePosition,
        authoritativeOffset,
        instant,
        action,
        clipSession.takeIf { researchClip },
    ) ?: return null
    if (instant && !researchClip) prepareMaceKillInstantCorrectionRecovery()
    return MaceKillCorrectionRecovery(
        state,
        authoritativePosition,
        authoritativeOffset,
        action,
        instant,
        researchClip,
        movements,
    )
}

private fun MaceKillModuleState.resolveMaceKillCorrectionMovements(
    correctionPlayer: Player,
    state: MaceKillLocalCorrectionState,
    authoritativePosition: Vec3,
    authoritativeOffset: Vec3,
    instant: Boolean,
    action: MaceKillCorrectionRecoveryAction,
    researchSession: MaceClipReachSession?,
): List<Vec3>? {
    if (researchSession != null) {
        return planMaceKillResearchCorrectionRecovery(
            state.routeOrigin,
            authoritativePosition,
            researchSession,
        )
    }
    return resolveStandardMaceKillCorrectionMovements(
        MaceKillStandardCorrectionRequest(
            correctionPlayer,
            state,
            authoritativePosition,
            authoritativeOffset,
            instant,
            action,
        ),
    )
}

private fun MaceKillModuleState.prepareMaceKillInstantCorrectionRecovery() {
    activeClipReachSession = null
    instantCorrectionRecoveryActive = true
    routeStepWaitTicks = 0
    activeRouteConfiguration = activeRouteConfiguration?.let(::maceKillInstantCorrectionRecoveryConfiguration)
}

internal fun MaceKillModuleState.installMaceKillCorrectionRecovery(
    recovery: MaceKillCorrectionRecovery,
): Boolean {
    val policy = if (recovery.researchClip || instantCorrectionRecoveryActive) {
        MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF
    } else {
        MaceKillGroundPolicy.COLLISION_DERIVED
    }
    if (!replanMaceKillFallSafety(
            recovery.authoritativePosition,
            recovery.movements,
            outboundStepCount = 0,
            groundPolicy = policy,
        )
    ) {
        rejectMaceKillCorrectionRecovery("fall-safety", "steps" to recovery.movements.size)
        return false
    }
    val failure = runCatching {
        routeEngine.beginPacketExactRecoveryFrom(
            recovery.authoritativeOffset,
            recovery.movements,
            routeStepWaitTicks,
        )
    }.exceptionOrNull()
    if (failure == null) return true
    rejectMaceKillCorrectionRecovery("engine", "exception" to failure::class.simpleName)
    return false
}
