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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_NEAR_GROUND_HORIZONTAL_INSET
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_NEAR_GROUND_PROBE_DEPTH
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.withVanillaSpearKillBlockShapes


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.resetVirtualFallSafetyState() {
    virtualFallGroundingPackets.clear()
    virtualFallStabilizationPackets.clear()
    virtualFallStabilizationDelivered = false
}

internal fun SpearKillModuleState.calculateSpearKillServerCollisionBoxAt(position: Vec3): AABB =
    if (shouldMaintainSpearKillServerSneak) {
        player.getDimensions(Pose.CROUCHING).makeBoundingBox(position)
    } else {
        player.boundingBox.move(position.subtract(player.position()))
    }

internal fun SpearKillModuleState.isSpearKillPositionNearGroundState(position: Vec3): Boolean {
    if (!position.isFinite()) return false
    val probe = calculateSpearKillServerCollisionBoxAt(position)
        .inflate(-SPEAR_KILL_NEAR_GROUND_HORIZONTAL_INSET, 0.0, -SPEAR_KILL_NEAR_GROUND_HORIZONTAL_INSET)
        .move(0.0, -SPEAR_KILL_NEAR_GROUND_PROBE_DEPTH, 0.0)
    return withVanillaSpearKillBlockShapes { !world.noCollision(player, probe) }
}

internal fun SpearKillModuleState.spearKillGroundProfileState(
    origin: Vec3,
    movements: List<Vec3>,
): List<Boolean> {
    var position = origin
    return movements.map { movement ->
        position = position.add(movement)
        isSpearKillPositionNearGroundState(position)
    }
}

internal fun SpearKillModuleState.beginVirtualFallSafetyForMovements(
    outboundMovements: List<Vec3>,
    routeOrigin: Vec3 = player.position(),
): Boolean {
    val movements = outboundMovements + outboundMovements.asReversed().map { it.scale(-1.0) }
    val result = SpearKillServerFallSafetyPlan.createForMovements(
        movements = movements,
        outboundStepCount = outboundMovements.size,
        initialFallDistance = player.fallDistance.toDouble(),
        safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        groundedSteps = spearKillGroundProfileState(routeOrigin, movements),
        expectedNetMovement = Vec3.ZERO,
    )
    val plan = (result as? SpearKillServerFallSafetyPlanResult.Ready)?.plan ?: run {
        fallSafetyLifecycle.invalidate()
        resetVirtualFallSafetyState()
        return false
    }
    beginVirtualFallSafetyPlan(plan)
    return true
}

internal fun SpearKillModuleState.beginVirtualFallSafetyPlan(plan: SpearKillServerFallSafetyPlan) {
    virtualFallGroundingPackets.clear()
    virtualFallStabilizationPackets.clear()
    virtualFallStabilizationDelivered = false
    fallSafetyLifecycle.begin(plan)
}
