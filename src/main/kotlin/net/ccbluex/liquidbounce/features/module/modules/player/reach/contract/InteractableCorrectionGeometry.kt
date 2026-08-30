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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.contract

import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** Collision sweep used for a normal one-block step instead of intersecting its support block diagonally. */
internal fun interactableSweepWaypoints(from: Vec3, to: Vec3): List<Vec3> {
    val hasHorizontalTravel = abs(from.x - to.x) > GEOMETRY_POSITION_EPSILON ||
        abs(from.z - to.z) > GEOMETRY_POSITION_EPSILON
    val verticalDistance = abs(from.y - to.y)
    if (!hasHorizontalTravel || verticalDistance < STEP_HEIGHT_THRESHOLD ||
        verticalDistance > MAXIMUM_STEP_HEIGHT
    ) {
        return listOf(to)
    }
    return if (to.y > from.y) {
        listOf(Vec3(from.x, to.y, from.z), to)
    } else {
        listOf(Vec3(to.x, from.y, to.z), to)
    }
}

private const val GEOMETRY_POSITION_EPSILON = 1.0E-6
private const val STEP_HEIGHT_THRESHOLD = 0.5
private const val MAXIMUM_STEP_HEIGHT = 1.0 + GEOMETRY_POSITION_EPSILON
