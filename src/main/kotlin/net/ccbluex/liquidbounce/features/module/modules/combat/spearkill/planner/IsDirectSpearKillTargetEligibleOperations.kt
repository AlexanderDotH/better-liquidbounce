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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner


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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.calculateSpearKillAttackDirection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.packetRoutingAllowsOccludedTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.usesPacketMovementMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.isSpearKillAStarTargetEligible
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.hasVisibleSpearKillAttackRay
import net.minecraft.world.entity.LivingEntity

internal fun SpearKillModuleState.isDirectSpearKillTargetEligible(entity: LivingEntity, travel: Double): Boolean {
    val eye = player.eyePosition
    val direction = calculateSpearKillAttackDirection(
        playerEyePosition = eye,
        predictedTargetPosition = entity.position(),
        targetEyeOffset = entity.eyePosition.subtract(entity.position()),
        fallbackDirection = player.lookAngle,
    )
    val hasVisibleAttackRay = hasVisibleSpearKillAttackRay(
        eye = eye,
        direction = entity.eyePosition.subtract(eye),
        targetBox = entity.boundingBox,
        range = maxTargetDistance.toDouble(),
    )
    val hasClearDirectTravel = hasClearSpearKillDirectTravel(direction, travel)

    return isSpearKillAStarTargetEligible(
        hasLineOfSight = hasVisibleAttackRay,
        hasClearDirectTravel = hasClearDirectTravel,
        packetAStarEnabled = packetRoutingAllowsOccludedTarget,
        packetMovementMode = usesPacketMovementMode,
    )
}
