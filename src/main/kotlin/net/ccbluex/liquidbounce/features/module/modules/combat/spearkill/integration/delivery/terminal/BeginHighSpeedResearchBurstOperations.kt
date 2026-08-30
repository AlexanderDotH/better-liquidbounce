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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_ATTACK_RAY_RANGE
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_PRIMED_MAX_SERVER_PACKETS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPacketSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillPrimedPendingStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.estimateSpearKillKineticDamage
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.hasSpearKillHitboxRaycastCollision
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchBurstPolicy
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchBurstStart
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchTargetStart
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.isSpearKillPrimedEndpointFree
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.packetPositionOrigin
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.spearKillKineticDamageRequirements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.toResearchPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.hasVisibleSpearKillAttackRay
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.kotlin.toDouble
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.pendingFinalOutboundStep
import net.ccbluex.liquidbounce.utils.entity.lastPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.beginHighSpeedResearchBurst(
    step: SpearKillPrimedPendingStep,
    settings: SpearKillPacketSessionSettings,
): String? {
    val movement = step.destination.subtract(step.origin)
    val target = currentHighSpeedResearchTarget()
    val researchTarget = createHighSpeedResearchTarget(target, movement)
    val destinationSpaceFree = isSpearKillPrimedEndpointFree(packetPositionOrigin(), step.destination)
    val terminalRaytraceClear = hasClearHighSpeedResearchTerminalRay(step.destination, target)
    return highSpeedResearch.begin(
        createHighSpeedResearchBurstStart(
            step,
            settings,
            movement,
            researchTarget,
            destinationSpaceFree,
            terminalRaytraceClear,
        ),
    )
}

private fun SpearKillModuleState.currentHighSpeedResearchTarget(): LivingEntity? = lockedAStarTarget?.takeIf {
    SpearKillHighSpeedResearchBurstPolicy.admitsTarget(
        pendingOutbound = packetBootSession.pendingOutboundStep,
        sameWorld = it.level() === world,
    )
}

private fun SpearKillModuleState.hasClearHighSpeedResearchTerminalRay(
    destination: Vec3,
    target: LivingEntity?,
): Boolean {
    val virtualEye = destination.add(player.eyePosition.subtract(player.position()))
    val visibleAttackRay = target?.let {
        hasVisibleSpearKillAttackRay(
            eye = virtualEye,
            direction = it.eyePosition.subtract(virtualEye),
            targetBox = it.boundingBox,
            range = SPEAR_KILL_ATTACK_RAY_RANGE,
        )
    } ?: false
    return SpearKillHighSpeedResearchBurstPolicy.terminalRaytraceClear(
        pendingFinalOutbound = packetBootSession.pendingFinalOutboundStep,
        targetAvailable = target != null,
        visibleAttackRay = visibleAttackRay,
    )
}

@Suppress("LongParameterList")
private fun SpearKillModuleState.createHighSpeedResearchBurstStart(
    step: SpearKillPrimedPendingStep,
    settings: SpearKillPacketSessionSettings,
    movement: Vec3,
    target: SpearKillHighSpeedResearchTargetStart?,
    destinationSpaceFree: Boolean,
    terminalRaytraceClear: Boolean,
): SpearKillHighSpeedResearchBurstStart {
    val plan = step.plan
    return SpearKillHighSpeedResearchBurstStart(
        clientTick = player.tickCount,
        primingPacketsRequested = plan.targetPrimingPackets,
        primingPacketType = settings.primingPacketType.toResearchPacketType(),
        finalPacketType = settings.finalPacketType,
        packetBudget = settings.instantMaxPackets,
        origin = step.origin,
        destination = step.destination,
        localPositionBefore = player.position(),
        targetSpeed = settings.transport.maxSpeed,
        currentSpeed = speedController.currentSpeed,
        acceleration = movementConfiguration.acceleration.toDouble(),
        deceleration = movementConfiguration.deceleration.toDouble(),
        routeStepLimit = settings.transport.stepLimit,
        expectedVelocity = player.deltaMovement.length(),
        elytraFlying = player.isFallFlying,
        onGround = player.onGround(),
        horizontalCollision = player.horizontalCollision,
        squaredDistanceThresholdPerPacket = plan.movementProfile.squaredDistanceThreshold,
        effectivePacketCount = plan.serverCountedPackets,
        packetCountReset = SpearKillHighSpeedResearchBurstPolicy.packetCountReset(
            plan.finalPacketOrdinal,
            SPEAR_KILL_PRIMED_MAX_SERVER_PACKETS,
        ),
        predictedAccepted = plan.sourcePredictedAccepted,
        corridorBlocked = hasSpearKillHitboxRaycastCollision(spearKillServerCollisionBoxAt(step.origin), movement),
        destinationSpaceFree = destinationSpaceFree,
        terminalRaytraceClear = terminalRaytraceClear,
        target = target,
    )
}

internal fun SpearKillModuleState.createHighSpeedResearchTarget(
    target: LivingEntity?,
    movement: Vec3,
): SpearKillHighSpeedResearchTargetStart? {
    target ?: return null
    val targetMovement = target.position().subtract(target.lastPos)
    val estimatedDamage = player.useItem.get(DataComponents.KINETIC_WEAPON)
        ?.let(::spearKillKineticDamageRequirements)
        ?.let { requirements ->
            estimateSpearKillKineticDamage(
                deliveredMovement = movement,
                targetMovement = targetMovement,
                lookDirection = movement,
                requirements = requirements,
            ).bonusDamage
        }?.toDouble() ?: 0.0
    return SpearKillHighSpeedResearchTargetStart(
        entityId = target.id,
        name = target.name.string,
        health = target.health.toDouble(),
        estimatedKineticDamage = estimatedDamage,
    )
}
