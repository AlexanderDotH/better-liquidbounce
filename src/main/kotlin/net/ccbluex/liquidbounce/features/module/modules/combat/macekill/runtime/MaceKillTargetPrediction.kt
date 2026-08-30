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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.entity.lastPos
import net.ccbluex.liquidbounce.utils.math.geometry.LineSegment
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal data class MaceKillRouteTargetPrediction(
    val observedPosition: Vec3,
    val position: Vec3,
    val eyePosition: Vec3,
    val boundingBox: AABB,
)

internal data class MaceKillLookRayPriority(
    val directlyHovered: Boolean,
    val angularErrorSquared: Double,
    val distanceAlongRaySquared: Double,
) : Comparable<MaceKillLookRayPriority> {
    override fun compareTo(other: MaceKillLookRayPriority): Int {
        if (directlyHovered != other.directlyHovered) return if (directlyHovered) -1 else 1
        val angularComparison = angularErrorSquared.compareTo(other.angularErrorSquared)
        return angularComparison.takeIf { it != 0 }
            ?: distanceAlongRaySquared.compareTo(other.distanceAlongRaySquared)
    }
}

internal fun maceKillTargetSelectionMargin(): Double = MACE_KILL_TARGET_SELECTION_MARGIN

internal fun maceKillLookRayPriority(
    entityBox: AABB,
    eye: Vec3,
    lookEnd: Vec3,
    hitboxMargin: Double = MACE_KILL_TARGET_SELECTION_MARGIN,
): MaceKillLookRayPriority? {
    if (lookEnd.distanceToSqr(eye) <= MACE_KILL_LOOK_RAY_EPSILON_SQUARED ||
        !hitboxMargin.isFinite() || hitboxMargin < 0.0
    ) {
        return null
    }
    val lookRay = LineSegment(eye, lookEnd)
    lookRay.firstIntersectionWith(entityBox)?.let { hitPoint ->
        return MaceKillLookRayPriority(true, 0.0, eye.distanceToSqr(hitPoint))
    }
    val expandedHit = lookRay.firstIntersectionWith(entityBox.inflate(hitboxMargin)) ?: return null
    val nearest = lookRay.getNearestPointTo(entityBox)
    return MaceKillLookRayPriority(
        directlyHovered = false,
        angularErrorSquared = nearest.distanceSquared /
            maxOf(eye.distanceToSqr(nearest.point), MACE_KILL_LOOK_RAY_EPSILON_SQUARED),
        distanceAlongRaySquared = eye.distanceToSqr(expandedHit),
    )
}

internal fun compareMaceKillLookRayPriority(
    left: MaceKillLookRayPriority,
    right: MaceKillLookRayPriority,
    throughTerrain: Boolean,
): Int {
    if (!throughTerrain) return left.compareTo(right)
    val angularComparison = left.angularErrorSquared.compareTo(right.angularErrorSquared)
    return angularComparison.takeIf { it != 0 }
        ?: right.distanceAlongRaySquared.compareTo(left.distanceAlongRaySquared)
}

internal fun predictMaceKillRouteTarget(
    target: LivingEntity,
    predictionTicks: Int,
): MaceKillRouteTargetPrediction {
    val observedPosition = target.position()
    val predictedPosition = PositionExtrapolation.getBestForEntity(target)
        .getPositionInTicks(predictionTicks.coerceIn(0, MACE_KILL_MAX_TARGET_PREDICTION_TICKS).toDouble())
        .takeIf(Vec3::hasFiniteMaceKillPredictionCoordinates)
        ?: observedPosition
    val offset = predictedPosition.subtract(observedPosition)
    return MaceKillRouteTargetPrediction(
        observedPosition = observedPosition,
        position = predictedPosition,
        eyePosition = target.eyePosition.add(offset),
        boundingBox = target.boundingBox.move(offset),
    )
}

private fun Vec3.hasFiniteMaceKillPredictionCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private const val MACE_KILL_TARGET_SELECTION_MARGIN = 0.75
private const val MACE_KILL_LOOK_RAY_EPSILON_SQUARED = 1e-9
private const val MACE_KILL_MAX_TARGET_PREDICTION_TICKS = 512
