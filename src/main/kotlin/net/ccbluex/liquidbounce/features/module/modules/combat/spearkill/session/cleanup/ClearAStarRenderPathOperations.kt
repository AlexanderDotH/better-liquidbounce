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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillServerFallSafetyPlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillServerFallSafetyPlanResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.exactRecoveryMovementsFrom
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.replanVirtualFallSafety(plan: SpearKillServerFallSafetyPlan) {
    virtualFallGroundingPackets.clear()
    virtualFallStabilizationPackets.clear()
    virtualFallStabilizationDelivered = false
    fallSafetyLifecycle.replan(plan)
}

internal fun SpearKillModuleState.createFutureFallSafetyPlan(
    routeOrigin: Vec3,
    movements: List<Vec3>,
    outboundStepCount: Int,
    expectedNetMovement: Vec3,
    initialFallDistance: Double = fallSafetyLifecycle.confirmedFallDistance,
): SpearKillServerFallSafetyPlan? {
    val result = SpearKillServerFallSafetyPlan.createForMovements(
        movements = movements,
        outboundStepCount = outboundStepCount,
        initialFallDistance = initialFallDistance,
        safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        groundedSteps = spearKillGroundProfile(routeOrigin, movements),
        expectedNetMovement = expectedNetMovement,
    )
    return (result as? SpearKillServerFallSafetyPlanResult.Ready)?.plan
}

internal fun SpearKillModuleState.createReplacementFallSafetyPlan(
    outboundMovements: List<Vec3>,
): SpearKillServerFallSafetyPlan? {
    if (!fallSafetyLifecycle.active || outboundMovements.isEmpty()) return null
    val committedOffset = packetBootSession.committedOffset
    val committedRecovery = if (committedOffset.lengthSqr() < SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED) {
        emptyList()
    } else {
        packetBootSession.exactRecoveryMovementsFrom(committedOffset) ?: return null
    }
    val futureMovements = buildList(outboundMovements.size * 2 + committedRecovery.size) {
        addAll(outboundMovements)
        outboundMovements.asReversed().forEach { add(it.scale(-1.0)) }
        addAll(committedRecovery)
    }
    return createFutureFallSafetyPlan(
        routeOrigin = packetSessionOrigin?.add(committedOffset) ?: player.position(),
        movements = futureMovements,
        outboundStepCount = outboundMovements.size,
        expectedNetMovement = committedOffset.scale(-1.0),
    )
}
