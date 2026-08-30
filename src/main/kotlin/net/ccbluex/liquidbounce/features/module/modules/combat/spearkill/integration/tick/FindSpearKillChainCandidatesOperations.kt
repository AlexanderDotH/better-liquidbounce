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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick

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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.calculateSpearKillTravel
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.spearKillTargetSelectionMargin
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.findSpearKillChainCandidates(
    defeatedTarget: LivingEntity,
    chainAnchor: Vec3,
): List<LivingEntity> {
    val radius = maxTargetDistance.toDouble() + spearKillTargetSelectionMargin()
    val searchBox = AABB.ofSize(chainAnchor, radius * 2.0, radius * 2.0, radius * 2.0)
    return world.getEntitiesOfClass(LivingEntity::class.java, searchBox) { candidate ->
        candidate !== defeatedTarget && isTargetCandidateEligibleAt(candidate, chainAnchor)
    }
}

internal fun SpearKillModuleState.lockedAStarTargetCandidate(): Pair<LivingEntity, Double>? {
    val target = lockedAStarTarget ?: return null
    val routePosition = packetSessionOrigin
        ?.takeIf { packetBootSession.active }
        ?.add(packetBootSession.committedOffset)
        ?: player.position()
    if (!isLockedTargetEligibleAt(target, routePosition)) return null

    val distance = routePosition.distanceTo(target.position())
    return target to calculateSpearKillTravel(distance)
}
