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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedBurstStepResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPacketAccounting
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPrimedPendingStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPrimedPendingStepPreparation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.planSpearKillPrimedBurstStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.shouldProtectFallDamage
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.packetPositionOrigin
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.phys.Vec3

@Suppress("LongParameterList")
internal fun SpearKillModuleState.readyPrimedPendingStep(
    plan: SpearKillPrimedInstantPlan,
    noFallRequired: Boolean,
    origin: Vec3,
    destination: Vec3,
    movement: Vec3,
    windowOrigin: Vec3,
    settings: SpearKillPacketSessionSettings,
): SpearKillPrimedPendingStepPreparation.Ready {
    val step = SpearKillPrimedPendingStep(
        plan = plan,
        noFallPacketRequired = noFallRequired,
        origin = origin,
        destination = destination,
    )
    logSpearKillPrimedBurstPlan(step, movement, windowOrigin)
    if (settings.researchLog) step.burstId = beginHighSpeedResearchBurst(step, settings)
    activePrimedStep = step
    return SpearKillPrimedPendingStepPreparation.Ready(step)
}

internal fun SpearKillModuleState.planPrimedPendingBurst(
    settings: SpearKillPacketSessionSettings,
    movement: Vec3,
    noFallRequired: Boolean,
    origin: Vec3,
): SpearKillPrimedBurstStepResult = planSpearKillPrimedBurstStep(
    windowOrigin = origin,
    currentPosition = origin,
    movement = movement,
    expectedVelocitySquared = player.deltaMovement.lengthSqr(),
    movementProfile = primedMovementProfile(settings),
    priming = settings.priming,
    packetAccounting = SpearKillPrimedInstantPacketAccounting(
        ownedPreFinalPackets = ownedMovementPacketsThisTick,
        noFallPreFinalPackets = if (noFallRequired) 1 else 0,
        reservedPacketsAfterFinal = 0,
        maxPackets = settings.instantMaxPackets - primedSessionPacketsDelivered,
    ),
    primingPacketType = settings.primingPacketType,
    instantDirectTeleport = true,
)

/** Grounded Instant attacks use the normal threshold even if the local Elytra flag lingers. */
private fun SpearKillModuleState.primedMovementProfile(
    settings: SpearKillPacketSessionSettings,
): SpearKillPrimedInstantMovementProfile = if (
    settings.priming === SpearKillPrimedInstantPriming.Auto
) {
    SpearKillPrimedInstantMovementProfile.NORMAL
} else if (player.isFallFlying) {
    SpearKillPrimedInstantMovementProfile.ELYTRA
} else {
    SpearKillPrimedInstantMovementProfile.NORMAL
}

internal fun SpearKillModuleState.ensurePrimedPendingStep(
    movement: Vec3,
): SpearKillPrimedPendingStepPreparation {
    activePrimedStep?.let { return SpearKillPrimedPendingStepPreparation.Ready(it) }
    val settings = packetSessionSettings?.takeIf { it.primedInstant }
        ?: return SpearKillPrimedPendingStepPreparation.Block
    val noFallRequired = fallSafetyLifecycle.shouldStabilizePendingMovement(
        movement,
        shouldProtectFallDamage,
    )
    val origin = packetPositionOrigin().add(packetBootSession.committedOffset)
    val context = PrimedPendingStepContext(
        settings = settings,
        movement = movement,
        noFallRequired = noFallRequired,
        origin = origin,
        destination = origin.add(movement),
    )
    val burstPlan = planPrimedPendingBurst(settings, movement, noFallRequired, origin)
    return resolvePrimedPendingStep(burstPlan, context)
}

private data class PrimedPendingStepContext(
    val settings: SpearKillPacketSessionSettings,
    val movement: Vec3,
    val noFallRequired: Boolean,
    val origin: Vec3,
    val destination: Vec3,
)

private fun SpearKillModuleState.resolvePrimedPendingStep(
    burstPlan: SpearKillPrimedBurstStepResult,
    context: PrimedPendingStepContext,
): SpearKillPrimedPendingStepPreparation = when (burstPlan) {
    SpearKillPrimedBurstStepResult.Defer -> {
        logSpearKillPrimedBurstDecision(
            event = "PRIMED_BURST_DEFER",
            reason = "fresh-server-packet-window-required",
            origin = context.origin,
            destination = context.destination,
            movement = context.movement,
            windowOrigin = context.origin,
            noFallRequired = context.noFallRequired,
        )
        SpearKillPrimedPendingStepPreparation.Defer
    }
    SpearKillPrimedBurstStepResult.Block -> {
        logSpearKillPrimedBurstDecision(
            event = "PRIMED_BURST_BLOCK",
            reason = "packet-budget",
            origin = context.origin,
            destination = context.destination,
            movement = context.movement,
            windowOrigin = context.origin,
            noFallRequired = context.noFallRequired,
            maxPacketsRemaining = context.settings.instantMaxPackets - primedSessionPacketsDelivered,
        )
        SpearKillPrimedPendingStepPreparation.Block
    }
    is SpearKillPrimedBurstStepResult.Send -> readyPrimedPendingStep(
        plan = burstPlan.plan,
        noFallRequired = context.noFallRequired,
        origin = context.origin,
        destination = context.destination,
        movement = context.movement,
        windowOrigin = context.origin,
        settings = context.settings,
    )
}
