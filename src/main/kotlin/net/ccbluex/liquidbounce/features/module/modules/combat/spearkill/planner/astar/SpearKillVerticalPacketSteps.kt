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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar


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
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil

internal fun hasValidSpearKillPacketStepBounds(maxSpeed: Double, maxVerticalStep: Double): Boolean =
    maxSpeed.isFinite() && maxSpeed > 0.0 && maxVerticalStep.isFinite() && maxVerticalStep >= 0.0

internal fun appendSpearKillVerticalStepParts(
    from: Vec3,
    to: Vec3,
    maxSpeed: Double,
    maxVerticalStep: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
    destination: MutableList<Vec3>,
): Boolean {
    val baseMovement = to.subtract(from)
    val partCount = spearKillVerticalStepPartCount(baseMovement, maxVerticalStep) ?: return false
    var partStart = from
    for (partIndex in 1..partCount) {
        val partEnd = if (partIndex == partCount) {
            to
        } else {
            from.add(baseMovement.scale(partIndex.toDouble() / partCount))
        }
        if (!appendValidatedSpearKillPacketStep(
                from = partStart,
                to = partEnd,
                maxSpeed = maxSpeed,
                maxVerticalStep = maxVerticalStep,
                segmentValidator = segmentValidator,
                destination = destination,
            )
        ) {
            return false
        }
        partStart = partEnd
    }
    return true
}

private fun spearKillVerticalStepPartCount(movement: Vec3, maxVerticalStep: Double): Int? {
    val verticalDistance = abs(movement.y)
    if (verticalDistance <= maxVerticalStep + SPEAR_KILL_PACKET_STEP_EPSILON) return 1
    if (maxVerticalStep <= SPEAR_KILL_PACKET_STEP_EPSILON) return null
    return ceil(verticalDistance / maxVerticalStep).toInt()
}

private fun appendValidatedSpearKillPacketStep(
    from: Vec3,
    to: Vec3,
    maxSpeed: Double,
    maxVerticalStep: Double,
    segmentValidator: SpearKillAStarSegmentValidator,
    destination: MutableList<Vec3>,
): Boolean {
    val movement = to.subtract(from)
    if (!movement.isFinite() ||
        movement.length() > maxSpeed + SPEAR_KILL_PACKET_STEP_EPSILON ||
        abs(movement.y) > maxVerticalStep + SPEAR_KILL_PACKET_STEP_EPSILON ||
        !segmentValidator.isClear(from, to)
    ) {
        return false
    }
    destination += movement
    return true
}

private const val SPEAR_KILL_PACKET_STEP_EPSILON = 1.0E-9
