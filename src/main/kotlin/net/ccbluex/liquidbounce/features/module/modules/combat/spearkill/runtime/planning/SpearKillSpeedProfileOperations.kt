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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.SpearKillSpeedLimits
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.currentVanillaMovementBudget
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.kotlin.toDouble

internal fun SpearKillModuleState.currentSpeedLimits(stepDistance: Double): SpearKillSpeedLimits = SpearKillSpeedLimits(
    targetSpeed = activeMovementTransport?.maxSpeed ?: movementConfiguration.targetSpeed.toDouble(),
    acceleration = movementConfiguration.acceleration.toDouble(),
    deceleration = movementConfiguration.deceleration.toDouble(),
    stepDistance = stepDistance,
    vanillaBudget = currentVanillaMovementBudget,
)

internal fun SpearKillModuleState.currentSpeedProfile(stepDistance: Double): SpearKillSpeedProfile {
    val limits = currentSpeedLimits(stepDistance)
    val initialSpeed = if (speedController.active) {
        speedController.currentSpeed
    } else {
        player.deltaMovement.length().takeIf(Double::isFinite)?.coerceIn(0.0, limits.targetSpeed) ?: 0.0
    }
    return SpearKillSpeedProfile(initialSpeed, limits)
}

internal fun SpearKillModuleState.beginSpearKillSpeedSession() {
    if (speedController.active) return
    speedController.begin(
        observedSpeed = player.deltaMovement.length(),
        targetSpeed = activeMovementTransport?.maxSpeed ?: movementConfiguration.targetSpeed.toDouble(),
    )
}
