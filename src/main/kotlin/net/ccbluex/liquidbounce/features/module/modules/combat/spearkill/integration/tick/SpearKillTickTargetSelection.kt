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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick

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
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.retainsRejectedTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillActivationTick
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.SpearKillPreview
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillTargetCandidate
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillTickTargetContext
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.activeSpearKillTargetLock
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.debugSpearKillChanged
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.hasActiveAttackPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.hasAutomaticSpearRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.isSpearKillHoldUseCursorRetargetRequested
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.isSpearKillLaunchActivationSatisfied
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.isUseInputHeld
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.preferLockedSpearKillTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.requestSpearKillPacketFallFlight
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.selectSpearKillHoldUseLaunchTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.shouldAcquireSpearKillPreparationLock
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.usesPacketMovementMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.synchronizeSpearKillServerSneak

private data class SpearKillTickTargetCandidates(
    val attackActive: Boolean,
    val locked: SpearKillTargetCandidate?,
    val configured: SpearKillTargetCandidate?,
    val cursor: SpearKillTargetCandidate?,
    val launch: SpearKillTargetCandidate?,
)

internal fun SpearKillModuleState.selectSpearKillTickTarget(
    activation: SpearKillActivationTick,
): SpearKillTickTargetContext {
    val candidates = resolveSpearKillTickTargetCandidates(activation)
    val attackRequested = isSpearKillLaunchActivationSatisfied(
        activationMode = activationMode,
        activationRequested = activation.requested,
        previousLaunchTarget = holdUseLaunchTarget,
        launchTarget = candidates.launch?.first,
        automaticRequest = hasAutomaticSpearRequest,
    )
    previewTarget = candidates.launch?.first ?: candidates.configured?.first
    debugSpearKillTickTarget(candidates, activation.requested, attackRequested)
    updateSpearKillTickTargetOwnership(candidates, attackRequested)
    return SpearKillTickTargetContext(candidates.attackActive, attackRequested, candidates.launch)
}

private fun SpearKillModuleState.resolveSpearKillTickTargetCandidates(
    activation: SpearKillActivationTick,
): SpearKillTickTargetCandidates {
    val attackActive = hasActiveAttackPath
    if (!attackActive && !packetRoutePreparationActive && lockedAStarTarget != null) clearAStarTargetLock()
    val shouldFind = SpearKillPreview.enabled || (!attackActive && activation.requested)
    val lockedCandidate = lockedAStarTargetCandidate()
    if (packetRoutePreparationActive && lockedCandidate == null) clearAStarTargetLock()
    val locked = activeSpearKillTargetLock(lockedCandidate, hasActiveAttackPath, packetRoutePreparationActive)
    val selected = if (locked == null && shouldFind) findSelectedTarget() else null
    val configured = preferLockedSpearKillTarget(locked, selected)
    val cursor = resolveSpearKillTickCursorTarget(locked, shouldFind)
    val launch = resolveSpearKillTickLaunchTarget(locked, configured, cursor)
    return SpearKillTickTargetCandidates(attackActive, locked, configured, cursor, launch)
}

private fun SpearKillModuleState.resolveSpearKillTickCursorTarget(
    locked: SpearKillTargetCandidate?,
    shouldFind: Boolean,
): SpearKillTargetCandidate? = if (locked == null && shouldFind && isSpearKillHoldUseCursorRetargetRequested(
        activationMode,
        isUseInputHeld,
        hasAutomaticSpearRequest,
        holdUseLaunchTarget,
    )
) {
    findLookRayTarget()
} else {
    null
}

private fun SpearKillModuleState.resolveSpearKillTickLaunchTarget(
    locked: SpearKillTargetCandidate?,
    configured: SpearKillTargetCandidate?,
    cursor: SpearKillTargetCandidate?,
): SpearKillTargetCandidate? {
    if (locked != null) return locked
    return when (selectSpearKillHoldUseLaunchTarget(
        activationMode,
        isUseInputHeld,
        hasAutomaticSpearRequest,
        holdUseLaunchTarget,
        cursor?.first,
        configured?.first,
    )) {
        null -> null
        cursor?.first -> cursor
        configured?.first -> configured
        else -> null
    }
}

private fun SpearKillModuleState.updateSpearKillTickTargetOwnership(
    candidates: SpearKillTickTargetCandidates,
    attackRequested: Boolean,
) {
    if (!attackRequested && !candidates.attackActive) {
        movementAssistPreparationActive = false
        if (!packetRoutePreparationActive && !fightBotSpearState.retainsRejectedTarget) clearAStarTargetLock()
    }
    if (shouldAcquireSpearKillPreparationLock(
            usesPacketMovementMode,
            candidates.attackActive,
            attackRequested,
            candidates.launch != null,
            lockedAStarTarget != null,
        )
    ) {
        lockedAStarTarget = requireNotNull(candidates.launch).first
        packetRoutePreparationActive = true
    }
    movementAssistPreparationActive = !candidates.attackActive && attackRequested && candidates.launch != null
    if (movementAssistPreparationActive) requestSpearKillPacketFallFlight()
    synchronizeSpearKillServerSneak()
}

private fun SpearKillModuleState.debugSpearKillTickTarget(
    candidates: SpearKillTickTargetCandidates,
    activationRequested: Boolean,
    attackRequested: Boolean,
) = debugSpearKillChanged(
    channel = "target-selection",
    event = "TARGET_STATE",
    fingerprint = {
        listOf(
            previewTarget?.id, candidates.launch?.first?.id, candidates.locked?.first?.id,
            candidates.configured?.first?.id, candidates.cursor?.first?.id, attackRequested,
            candidates.attackActive, packetRoutePreparationActive,
        )
    },
) {
    listOf(
        "tick" to player.tickCount, "activation" to activationMode, "configured_source" to targetSource,
        "activation_requested" to activationRequested, "attack_requested" to attackRequested,
        "attack_active" to candidates.attackActive, "route_preparing" to packetRoutePreparationActive,
        "selected_target_id" to candidates.launch?.first?.id, "locked_target_id" to candidates.locked?.first?.id,
        "configured_target_id" to candidates.configured?.first?.id, "cursor_target_id" to candidates.cursor?.first?.id,
        "selected_distance" to candidates.launch?.second,
    ) + spearKillDebugTargetFields(previewTarget, candidates.launch?.second)
}
