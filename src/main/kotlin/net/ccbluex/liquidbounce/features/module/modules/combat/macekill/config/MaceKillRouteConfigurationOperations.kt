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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
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

internal fun MaceKillModuleState.currentMaceKillRouteExecutionConfiguration(
    owner: MaceKillRouteOwner,
): MaceKillRouteExecutionConfiguration {
    val targetSpeed = movementConfiguration.targetSpeed.toDouble()
    val acceleration = movementConfiguration.acceleration.toDouble()
    val deceleration = movementConfiguration.deceleration.toDouble()
    val transport = selectMaceKillRouteTransport(
        configuredMotion = movementConfiguration.choice.activeMode === movementConfiguration.motion,
        owner = owner,
    )
    if (transport == MaceKillRouteTransport.MOTION) {
        return MaceKillRouteExecutionConfiguration(
            timing = MaceKillRouteTiming(
                transport = transport,
                stepDistance = minOf(targetSpeed, movementConfiguration.motion.stepDistance.toDouble()),
            ),
            routingMode = MaceKillRoutingMode.DIRECT,
            targetSpeed = targetSpeed,
            acceleration = acceleration,
            deceleration = deceleration,
        )
    }

    return currentPacketMaceKillRouteExecutionConfiguration(
        targetSpeed = targetSpeed,
        acceleration = acceleration,
        deceleration = deceleration,
        transport = transport,
    )
}

internal fun MaceKillModuleState.currentPacketMaceKillRouteExecutionConfiguration(
    targetSpeed: Double,
    acceleration: Double,
    deceleration: Double,
    transport: MaceKillRouteTransport,
): MaceKillRouteExecutionConfiguration {
    val packet = movementConfiguration.packet
    val packetStepDistance = minOf(targetSpeed, packet.stepDistance.toDouble())
    return when (packet.routing.activeMode) {
        packet.aStar -> aStarMaceKillRouteConfiguration(
            transport, packetStepDistance, targetSpeed, acceleration, deceleration,
        )
        packet.instant -> instantMaceKillRouteConfiguration(
            transport, packetStepDistance, targetSpeed, acceleration, deceleration,
        )
        else -> directMaceKillRouteConfiguration(
            transport, packetStepDistance, targetSpeed, acceleration, deceleration,
        )
    }
}

private fun MaceKillModuleState.aStarMaceKillRouteConfiguration(
    transport: MaceKillRouteTransport,
    stepDistance: Double,
    targetSpeed: Double,
    acceleration: Double,
    deceleration: Double,
) = MaceKillRouteExecutionConfiguration(
    timing = MaceKillRouteTiming(transport, stepDistance, movementConfiguration.packet.stepDelay),
    routingMode = MaceKillRoutingMode.A_STAR,
    targetSpeed = targetSpeed,
    acceleration = acceleration,
    deceleration = deceleration,
    maxCost = movementConfiguration.packet.aStar.maxCost,
    diagonal = movementConfiguration.packet.aStar.diagonal,
    lineOfSightShortcuts = movementConfiguration.packet.aStar.lineOfSightShortcuts,
)

private fun MaceKillModuleState.instantMaceKillRouteConfiguration(
    transport: MaceKillRouteTransport,
    stepDistance: Double,
    targetSpeed: Double,
    acceleration: Double,
    deceleration: Double,
) = MaceKillRouteExecutionConfiguration(
    timing = MaceKillRouteTiming(
        transport = transport,
        stepDistance = stepDistance,
        stepWaitTicks = movementConfiguration.packet.stepDelay,
        maxPacketsPerTick = maceKillInstantPacketsPerTick(
            movementConfiguration.packet.stepDelay,
            movementConfiguration.packet.instant.maxPackets,
        ),
    ),
    routingMode = MaceKillRoutingMode.INSTANT,
    targetSpeed = targetSpeed,
    acceleration = acceleration,
    deceleration = deceleration,
)

private fun MaceKillModuleState.directMaceKillRouteConfiguration(
    transport: MaceKillRouteTransport,
    stepDistance: Double,
    targetSpeed: Double,
    acceleration: Double,
    deceleration: Double,
) = MaceKillRouteExecutionConfiguration(
    timing = MaceKillRouteTiming(transport, stepDistance, movementConfiguration.packet.stepDelay),
    routingMode = MaceKillRoutingMode.DIRECT,
    targetSpeed = targetSpeed,
    acceleration = acceleration,
    deceleration = deceleration,
)

internal fun MaceKillModuleState.isInstantPacketRoutingConfigured(): Boolean =
    movementConfiguration.choice.activeMode === movementConfiguration.packet &&
        movementConfiguration.packet.routing.activeMode === movementConfiguration.packet.instant
