/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** One immutable, collinear attack line from the current server origin to spear stand-off. */
internal data class SpearKillDirectAttackLine(
    val direction: Vec3,
    val targetHitPoint: Vec3,
    val terminalWaypoint: Vec3,
)

/**
 * Resolves the first target-box surface hit on the ray towards the predicted eye position.
 * The player's terminal feet position remains on that same ray and stops exactly at spear reach.
 */
@Suppress("LongParameterList", "ReturnCount")
internal fun solveSpearKillDirectAttackLine(
    origin: Vec3,
    targetBox: AABB,
    targetEyePosition: Vec3,
    playerEyeOffset: Vec3,
    fallbackDirection: Vec3,
    standOff: Double = SPEAR_KILL_DIRECT_TARGET_STAND_OFF,
): SpearKillDirectAttackLine? {
    val hasFiniteGeometry = origin.hasFiniteDirectCoordinates() &&
        targetEyePosition.hasFiniteDirectCoordinates() &&
        playerEyeOffset.hasFiniteDirectCoordinates() &&
        fallbackDirection.hasFiniteDirectCoordinates()
    if (!hasFiniteGeometry || !standOff.isFinite() || standOff <= 0.0
    ) {
        return null
    }

    val playerEyePosition = origin.add(playerEyeOffset)
    val targetDirection = targetEyePosition.subtract(playerEyePosition)
    val direction = targetDirection.takeIf {
        it.lengthSqr() > SPEAR_KILL_DIRECT_EPSILON_SQUARED
    }?.normalize() ?: fallbackDirection.takeIf {
        it.lengthSqr() > SPEAR_KILL_DIRECT_EPSILON_SQUARED
    }?.normalize() ?: return null
    val rayLength = playerEyePosition.distanceTo(targetEyePosition) + SPEAR_KILL_DIRECT_RAY_PADDING
    val targetHitPoint = targetBox.clip(
        playerEyePosition,
        playerEyePosition.add(direction.scale(rayLength)),
    ).orElse(null) ?: return null
    val hitDistance = targetHitPoint.subtract(playerEyePosition).dot(direction)
    if (!hitDistance.isFinite() || hitDistance <= standOff + SPEAR_KILL_DIRECT_EPSILON) return null

    return SpearKillDirectAttackLine(
        direction = direction,
        targetHitPoint = targetHitPoint,
        terminalWaypoint = origin.add(direction.scale(hitDistance - standOff)),
    )
}

private fun Vec3.hasFiniteDirectCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

internal const val SPEAR_KILL_DIRECT_TARGET_STAND_OFF = 2.25
private const val SPEAR_KILL_DIRECT_RAY_PADDING = 4.5
private const val SPEAR_KILL_DIRECT_EPSILON = 1.0E-9
private const val SPEAR_KILL_DIRECT_EPSILON_SQUARED = 1.0E-18
