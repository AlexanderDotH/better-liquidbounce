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
package net.ccbluex.liquidbounce.render.playermodel

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.lang.Math.fma

internal fun Entity.interpolatePlayerModelPosition(partialTicks: Float): Vec3 {
    val current = position()
    val previous = if (tickCount == 0) null else Vec3(xOld, yOld, zOld)
    return interpolatePlayerModelPosition(previous, current, partialTicks)
}

internal fun interpolatePlayerModelPosition(previous: Vec3?, current: Vec3, partialTicks: Float): Vec3 {
    previous ?: return current
    val delta = partialTicks.toDouble()
    return Vec3(
        fma(delta, current.x - previous.x, previous.x),
        fma(delta, current.y - previous.y, previous.y),
        fma(delta, current.z - previous.z, previous.z),
    )
}
