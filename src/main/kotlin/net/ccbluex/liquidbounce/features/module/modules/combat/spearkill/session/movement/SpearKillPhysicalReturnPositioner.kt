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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement



import net.minecraft.world.phys.Vec3

private const val SPEAR_KILL_PHYSICAL_RETURN_MATCH_EPSILON_SQUARED = 1.0E-6

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

/** Applies confirmed return positions once the client is observed away from the session origin. */
internal class SpearKillPhysicalReturnPositioner(
    private val matchEpsilonSquared: Double = SPEAR_KILL_PHYSICAL_RETURN_MATCH_EPSILON_SQUARED,
) {

    private var applyPositionUpdates: Boolean? = null

    val followingReturn: Boolean
        get() = applyPositionUpdates == true

    init {
        require(matchEpsilonSquared.isFinite() && matchEpsilonSquared >= 0.0) {
            "Physical return match epsilon must be finite and non-negative"
        }
    }

    fun resolve(origin: Vec3, currentPosition: Vec3, confirmedOffset: Vec3): Vec3? {
        val confirmedPosition = origin.add(confirmedOffset)
        val matchesConfirmedRoutePosition = confirmedOffset.lengthSqr() > matchEpsilonSquared &&
            currentPosition.distanceToSqr(confirmedPosition) <= matchEpsilonSquared
        val shouldApply = applyPositionUpdates == true || matchesConfirmedRoutePosition
        applyPositionUpdates = shouldApply
        return confirmedPosition.takeIf { shouldApply }
    }

    fun clear() {
        applyPositionUpdates = null
    }
}
