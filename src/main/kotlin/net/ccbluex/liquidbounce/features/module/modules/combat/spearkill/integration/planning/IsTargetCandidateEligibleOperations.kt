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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning

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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.acceptsKillAuraDelegation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.isSafeSpearKillCombatTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.isSpearKillLockedTargetEligible
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.isSpearKillTargetCandidateEligible
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.killAuraDelegatedTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.packetRoutingAllowsOccludedTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.spearKillLookRayPriority
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.spearKillTargetSelectionMargin
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.selectSpearKillTargetForSource
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.calculateSpearKillTravel
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillLookRayCandidate
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.selectBestSpearKillLookRayCandidate
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.entity.box
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

internal fun SpearKillModuleState.isTargetCandidateEligible(entity: LivingEntity): Boolean =
    isTargetCandidateEligibleAt(entity, player.position())

internal fun SpearKillModuleState.isTargetCandidateEligibleAt(entity: LivingEntity, referencePosition: Vec3): Boolean =
    isSpearKillTargetCandidateEligible(
        isCombatSafe = entity.isSafeSpearKillCombatTarget(),
        isAlive = entity.isAlive && !entity.isRemoved,
        isInCurrentWorld = entity.level() === world,
        isWithinRange = referencePosition.distanceTo(entity.position()) in
            3.0..maxTargetDistance.toDouble(),
        isRejected = isSpearKillTargetRejected(entity),
    )

internal fun SpearKillModuleState.isLockedTargetEligibleAt(entity: LivingEntity, referencePosition: Vec3): Boolean =
    isSpearKillLockedTargetEligible(
        isCombatSafe = entity.isSafeSpearKillCombatTarget(),
        isAlive = entity.isAlive && !entity.isRemoved,
        isInCurrentWorld = entity.level() === world,
        distance = referencePosition.distanceTo(entity.position()),
        maximumDistance = maxTargetDistance.toDouble(),
        hysteresis = spearKillTargetSelectionMargin(),
        isRejected = isSpearKillTargetRejected(entity),
    )

internal fun SpearKillModuleState.findLookRayTarget(
    throughTerrain: Boolean = packetRoutingAllowsOccludedTarget,
): Pair<LivingEntity, Double>? {
    val eye = player.eyePosition
    val lookDirection = player.lookAngle.normalize()
    val lookEnd = eye.add(lookDirection.scale(maxTargetDistance.toDouble()))
    val searchDistance = maxTargetDistance.toDouble()
    val selectionMargin = spearKillTargetSelectionMargin()
    val targetSearchBox = player.boundingBox.inflate(searchDistance + selectionMargin)
    val bestCandidate = selectBestSpearKillLookRayCandidate(
        candidates = world.getEntitiesOfClass(
            LivingEntity::class.java,
            targetSearchBox,
            ::isTargetCandidateEligible,
        ).asSequence().mapNotNull { entity ->
            createSpearKillLookRayCandidate(entity, eye, lookEnd, searchDistance, selectionMargin)
        },
        throughTerrain = throughTerrain,
    ) ?: return null

    // Selection is look-ray only. Direct travel / LOS still gate attack start below.
    return bestCandidate.target to calculateSpearKillTravel(sqrt(bestCandidate.distanceSquared))
}

private fun SpearKillModuleState.createSpearKillLookRayCandidate(
    entity: LivingEntity,
    eye: Vec3,
    lookEnd: Vec3,
    searchDistance: Double,
    selectionMargin: Double,
): SpearKillLookRayCandidate<LivingEntity>? {
    val distanceSquared = player.distanceToSqr(entity)
    if (sqrt(distanceSquared) !in 3.0..searchDistance) return null
    val priority = spearKillLookRayPriority(
        entityBox = entity.box,
        eye = eye,
        lookEnd = lookEnd,
        hitboxMargin = selectionMargin,
    ) ?: return null
    return SpearKillLookRayCandidate(entity, distanceSquared, priority)
}

internal fun SpearKillModuleState.findCombatTarget(): Pair<LivingEntity, Double>? {
    val searchDistance = maxTargetDistance.toDouble()
    val target = world.getEntitiesOfClass(
        LivingEntity::class.java,
        player.boundingBox.inflate(searchDistance + spearKillTargetSelectionMargin()),
        ::isTargetCandidateEligible,
    ).minWithOrNull(
        compareBy<LivingEntity>(RotationUtil::crosshairAngleToEntity)
            .thenBy { player.distanceToSqr(it) },
    ) ?: return null

    return target to calculateSpearKillTravel(player.distanceTo(target).toDouble())
}

internal fun SpearKillModuleState.findSelectedTarget(): Pair<LivingEntity, Double>? {
    pendingKillAuraTarget = null
    if (acceptsKillAuraDelegation) {
        return (killAuraSpearTarget ?: killAuraDelegatedTarget())
            ?.takeIf(::isTargetCandidateEligible)
            ?.let { target ->
                pendingKillAuraTarget = target
                target to calculateSpearKillTravel(player.distanceTo(target).toDouble())
            }
    }

    return selectSpearKillTargetForSource(
        targetSource = targetSource,
        lookRayTarget = ::findLookRayTarget,
        combatTarget = ::findCombatTarget,
    )
}
