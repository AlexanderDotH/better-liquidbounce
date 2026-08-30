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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle
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

internal fun MaceKillModuleState.activeRouteStepDistance(): Double = activeRouteConfiguration?.timing?.stepDistance
    ?: minOf(movementConfiguration.targetSpeed, movementConfiguration.packet.stepDistance).toDouble()

internal fun MaceKillModuleState.currentMaceKillSpeedLimits(
    configuration: MaceKillRouteExecutionConfiguration,
): MaceKillSpeedLimits = MaceKillSpeedLimits(
    targetSpeed = minOf(configuration.targetSpeed, configuration.timing.stepDistance),
    acceleration = configuration.acceleration,
    deceleration = configuration.deceleration,
    stepDistance = configuration.timing.stepDistance,
    vanillaBudget = calculateMaceKillVanillaMovementBudget(player.deltaMovement, player.isFallFlying),
)

internal fun MaceKillModuleState.currentMaceKillSpeedProfile(
    configuration: MaceKillRouteExecutionConfiguration,
): MaceKillSpeedProfile {
    val limits = currentMaceKillSpeedLimits(configuration)
    val initialSpeed = if (speedController.active) {
        speedController.currentSpeed
    } else {
        player.deltaMovement.length().takeIf(Double::isFinite)?.coerceIn(0.0, limits.targetSpeed) ?: 0.0
    }
    return MaceKillSpeedProfile(initialSpeed, limits)
}
