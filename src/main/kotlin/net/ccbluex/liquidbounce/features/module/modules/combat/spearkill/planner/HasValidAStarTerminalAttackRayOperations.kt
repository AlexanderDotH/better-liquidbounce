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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_ATTACK_RAY_RANGE
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_MIN_ATTACK_RAY_RANGE
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.findSpearKillTerminalAttackHitPoint
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarAttackApproach
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.hasValidAStarTerminalAttackRay(
    targetBox: AABB,
    eyeOffset: Vec3,
    approach: SpearKillAStarAttackApproach,
    lineOfSight: (Vec3, Vec3) -> Boolean = { from, to -> hasLineOfSight(from, to, player) },
): Boolean {
    val virtualEyePosition = approach.terminalWaypoint.add(eyeOffset)
    val attackHitPoint = findSpearKillTerminalAttackHitPoint(
        eye = virtualEyePosition,
        terminalMovement = approach.terminalWaypoint.subtract(approach.plannerGoal),
        targetBox = targetBox,
        range = SPEAR_KILL_ATTACK_RAY_RANGE,
    ) ?: return false
    val hitDistance = virtualEyePosition.distanceTo(attackHitPoint)
    return hitDistance in SPEAR_KILL_MIN_ATTACK_RAY_RANGE..SPEAR_KILL_ATTACK_RAY_RANGE &&
        lineOfSight(virtualEyePosition, attackHitPoint)
}
