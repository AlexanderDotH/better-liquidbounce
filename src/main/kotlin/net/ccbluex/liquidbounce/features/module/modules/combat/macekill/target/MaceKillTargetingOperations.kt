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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*

import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
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
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.*
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.*
import net.minecraft.world.entity.player.*
import net.minecraft.world.item.*
import net.minecraft.world.phys.*

internal fun MaceKillModuleState.isOrdinaryMeleeAvailable(target: LivingEntity): Boolean =
    player.boundingBox.distanceToSqr(target.eyePosition) <= MACE_KILL_ATTACK_RANGE_SQUARED &&
        hasLineOfSight(player.eyePosition, target.eyePosition, player)

internal fun MaceKillModuleState.isMaceKillAnchorValid(
    origin: Vec3,
    position: Vec3,
    originBoundingBox: AABB = player.boundingBox,
): Boolean {
    val box = originBoundingBox.move(position.subtract(origin))
    return world.worldBorder.isWithinBounds(box) && withVanillaSpearKillBlockShapes {
        world.noCollision(player, box)
    }
}

internal fun MaceKillModuleState.routePacketPosition(packet: ServerboundMovePlayerPacket): Vec3 = Vec3(
    packet.getX(player.x),
    packet.getY(player.y),
    packet.getZ(player.z),
)

internal fun MaceKillModuleState.isMaceKillPositionNearGround(position: Vec3): Boolean {
    val box = player.boundingBox.move(position.subtract(player.position()))
    return withVanillaSpearKillBlockShapes {
        !world.noCollision(player, box.move(0.0, -MACE_KILL_GROUND_PROBE_DEPTH, 0.0))
    }
}

internal fun MaceKillModuleState.stopKillAuraBlockingBeforeRoute() {
    if (player.isUsingItem) integration.stopKillAuraBlockingIfActive()
}

internal fun MaceKillModuleState.isRemoteEndpointReady(
    localPlayer: LocalPlayer,
    target: Entity,
    endpoint: Vec3,
    targetEyePosition: Vec3 = target.eyePosition,
    requireAttackCooldown: Boolean = true,
): Boolean {
    val livingTarget = target as? LivingEntity ?: return false
    val endpointBox = localPlayer.boundingBox.move(endpoint.subtract(localPlayer.position()))
    val endpointEyes = endpoint.add(0.0, localPlayer.eyeHeight.toDouble(), 0.0)
    return isMaceKillEndpointReady(
        holdingMace = hasServerHeldMace(),
        bodySpaceClear = world.getBlockCollisions(localPlayer, endpointBox).allEmpty(),
        attackRayClear = endpointBox.distanceToSqr(targetEyePosition) <= MACE_KILL_ATTACK_RANGE_SQUARED &&
            hasLineOfSight(endpointEyes, targetEyePosition, localPlayer),
        cooldownReady = !requireAttackCooldown || isAttackCooldownReady(),
        usableFallHeight = determineUsableFallHeight(endpointBox),
    )
}

internal fun MaceKillModuleState.determineUsableFallHeight(endpointBox: AABB): Int = (fallHeight downTo 1).firstOrNull { height ->
    world.getBlockCollisions(player, endpointBox.move(0.0, height.toDouble(), 0.0)).allEmpty()
} ?: 0

internal fun MaceKillModuleState.currentPreviewGlow(): TargetGlowSelection? {
    if (!enabled || !preview.enabled || preview.mode.activeMode !== preview.glow ||
        !hasServerHeldMace()
    ) {
        return null
    }
    val target = previewTarget ?: return null
    return TargetGlowSelection(
        target,
        preview.glow.glowColor,
        preview.glow.glowStyle.style,
    )
}

@Suppress("ReturnCount") // Target ownership sources are checked in strict priority order.
internal fun MaceKillModuleState.findSelectedTarget(): LivingEntity? {
    activeRouteTarget?.takeIf(::isMaceKillTargetEligible)?.let { return it }
    fightBotMaceTarget?.takeIf(::isMaceKillTargetEligible)?.let { return it }
    val killAuraTarget = integration.killAuraTarget()?.takeIf(::isMaceKillTargetEligible)

    return selectMaceKillDelegatedTarget(acceptsKillAuraDelegation, killAuraTarget) {
        if (!hasServerHeldMace()) return@selectMaceKillDelegatedTarget null
        selectMaceKillTargetForSource(
            targetSource = targetSource,
            lookRayTarget = ::findLookRayTarget,
            combatTarget = ::findCombatTarget,
        )
    }
}

internal fun MaceKillModuleState.findLookRayTarget(clipReachResearch: Boolean = false): LivingEntity? {
    val eye = player.eyePosition
    val lookEnd = eye.add(player.lookAngle.normalize().scale(maximumTargetRange.toDouble()))
    val routing = movementConfiguration.packet.routing.activeMode
    val throughTerrain = shouldMaceKillLookRayIgnoreTerrain(
        packetMovement = movementConfiguration.choice.activeMode === movementConfiguration.packet,
        aStarRouting = routing === movementConfiguration.packet.aStar,
        instantRouting = routing === movementConfiguration.packet.instant,
        clipReachResearch = clipReachResearch,
    )
    var best: Pair<LivingEntity, MaceKillLookRayPriority>? = null

    for (entity in world.getEntitiesOfClass(
        LivingEntity::class.java,
        player.boundingBox.inflate(maximumTargetRange.toDouble() + maceKillTargetSelectionMargin()),
        ::isMaceKillTargetEligible,
    )) {
        val priority = maceKillLookRayPriority(entity.box, eye, lookEnd) ?: continue
        if (!throughTerrain && !hasLineOfSight(eye, entity.eyePosition, player)) continue
        val previous = best?.second
        if (previous == null || compareMaceKillLookRayPriority(priority, previous, throughTerrain) < 0) {
            best = entity to priority
        }
    }
    return best?.first
}

internal fun MaceKillModuleState.findCombatTarget(): LivingEntity? {
    val origin = player.position()
    val timing = currentMaceKillRouteExecutionConfiguration(MaceKillRouteOwner.MANUAL).timing
    val candidates = world.getEntitiesOfClass(
        LivingEntity::class.java,
        player.boundingBox.inflate(maximumTargetRange.toDouble() + maceKillTargetSelectionMargin()),
        ::isMaceKillTargetEligible,
    ).map { target ->
        MaceKillCombatTargetCandidate(
            target = target,
            distance = player.distanceTo(target).toDouble(),
            crosshairAngle = RotationUtil.crosshairAngleToEntity(target),
        )
    }

    return selectMaceKillCombatTarget(
        candidates = candidates,
        retainedTarget = previewTarget,
        hasAttackEndpoint = { target ->
            val predicted = predictedMaceKillTarget(target, origin, timing)
            findMaceKillAttackEndpoint(
                target = target,
                origin = origin,
                targetPosition = predicted.position,
                targetEyePosition = predicted.eyePosition,
                requireAttackCooldown = false,
            ) != null
        },
    )
}

internal fun MaceKillModuleState.isMaceKillTargetEligible(target: LivingEntity): Boolean = isMaceKillTargetCandidateEligible(
    isCombatSafe = integration.shouldAttack(target),
    isAlive = target.isAlive && !target.isRemoved,
    isInCurrentWorld = target.level() === world,
    isWithinRange = player.distanceTo(target) in MACE_KILL_MIN_TARGET_DISTANCE..maximumTargetRange,
    isRejected = rejectedTargets.isRejected(target, player.tickCount),
    isInWater = target.isInWater || target.isSwimming || target.isUnderWater,
)
