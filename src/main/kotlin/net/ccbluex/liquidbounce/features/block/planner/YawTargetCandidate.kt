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
package net.ccbluex.liquidbounce.features.block.planner

import net.minecraft.world.phys.Vec3

internal data class YawTargetCandidate(
    val point: Vec3?,
    val tolerance: Float,
)

internal fun selectYawTarget(
    high: YawTargetCandidate,
    low: YawTargetCandidate,
    tolerance: Float,
): Vec3? = when {
    high.tolerance <= tolerance && low.tolerance <= tolerance -> {
        if (high.tolerance < low.tolerance) high.point else low.point
    }
    high.tolerance <= tolerance -> high.point
    low.tolerance <= tolerance -> low.point
    else -> null
}
