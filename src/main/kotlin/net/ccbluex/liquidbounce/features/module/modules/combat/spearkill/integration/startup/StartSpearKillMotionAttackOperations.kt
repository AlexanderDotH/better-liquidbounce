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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAttackStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.isSpearKillElytraActive
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.resolveSpearKillMovementTransport
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.beginSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.currentSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.requestSpearKillPacketFallFlight
import net.minecraft.world.entity.LivingEntity

internal fun SpearKillModuleState.startSpearKillMotionAttack(target: LivingEntity, distance: Double): SpearKillAttackStartResult {
    packetSessionSettings = null
    clearAStarRenderPath()
    requestSpearKillPacketFallFlight()
    val transport = resolveSpearKillMovementTransport(
        configuredSpeed = movementConfiguration.targetSpeed.toDouble(),
        configuredStepLimit = movementConfiguration.motion.stepDistance.toDouble(),
        elytraActive = isSpearKillElytraActive,
    )
    activeMovementTransport = transport
    val movements = createDirectAttackMovements(
        target = target,
        distance = distance,
        profile = currentSpeedProfile(transport.stepLimit),
    )
    val outboundSteps = (movements.size - 1) / 2
    attackMovements.addAll(movements)
    beginSpearKillAttempt(
        target = target,
        routeMode = "Direct",
        outboundSteps = outboundSteps,
        hitTicks = outboundSteps,
        terminalAuthorizationRequired = false,
    )
    return SpearKillAttackStartResult.STARTED
}
