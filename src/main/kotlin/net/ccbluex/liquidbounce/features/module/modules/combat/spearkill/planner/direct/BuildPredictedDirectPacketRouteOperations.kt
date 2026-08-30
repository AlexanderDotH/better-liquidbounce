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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillSpeedProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.buildSpearKillProfiledDirectAttackRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.calculateSpearKillAttackDirection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPacketRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.hasValidAStarTerminalAttackRay
import net.minecraft.world.phys.Vec3

@Suppress("LongParameterList")
internal fun SpearKillModuleState.buildPredictedDirectPacketRoute(
    target: SpearKillRouteTargetSnapshot,
    routeOrigin: Vec3,
    routeEye: Vec3,
    profile: SpearKillSpeedProfile,
    maxVerticalStep: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
    predictedHitTicks: Int,
    fallbackDirection: Vec3,
    hasAttackRay: (Vec3, Vec3) -> Boolean,
): SpearKillAStarPacketRoute? {
    val prediction = target.predict(predictedHitTicks)
    val direction = calculateSpearKillAttackDirection(
        playerEyePosition = routeEye,
        predictedTargetPosition = prediction.position,
        targetEyeOffset = target.eyeOffset,
        fallbackDirection = fallbackDirection,
    )
    val attackRoute = buildSpearKillProfiledDirectAttackRoute(
        origin = routeOrigin,
        targetBox = prediction.boundingBox,
        targetEyePosition = prediction.eyePosition,
        playerEyeOffset = routeEye.subtract(routeOrigin),
        preferredDirection = direction,
        profile = profile,
        segmentValidator = segmentValidator,
        maxVerticalStep = maxVerticalStep,
    ) ?: return null
    if (!hasValidAStarTerminalAttackRay(
            targetBox = prediction.boundingBox,
            eyeOffset = routeEye.subtract(routeOrigin),
            approach = attackRoute.approach,
            lineOfSight = hasAttackRay,
        )
    ) {
        return null
    }
    return attackRoute.packetRoute
}
