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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.utils.entity

import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

internal object ExplosionExposureSampler {

    fun calculate(box: AABB, isRayClear: (Vec3) -> Boolean): Float {
        val grid = ExplosionExposureGrid(box)
        if (grid.hasInvalidSteps) return 0.0F

        val tally = ExposureTally()
        var xStep = 0.0
        while (xStep <= 1.0) {
            sampleY(grid, xStep, tally, isRayClear)
            xStep += grid.stepX
        }
        return tally.ratio()
    }

    private fun sampleY(
        grid: ExplosionExposureGrid,
        xStep: Double,
        tally: ExposureTally,
        isRayClear: (Vec3) -> Boolean,
    ) {
        var yStep = 0.0
        while (yStep <= 1.0) {
            sampleZ(grid, xStep, yStep, tally, isRayClear)
            yStep += grid.stepY
        }
    }

    private fun sampleZ(
        grid: ExplosionExposureGrid,
        xStep: Double,
        yStep: Double,
        tally: ExposureTally,
        isRayClear: (Vec3) -> Boolean,
    ) {
        var zStep = 0.0
        while (zStep <= 1.0) {
            tally.record(isRayClear(grid.samplePoint(xStep, yStep, zStep)))
            zStep += grid.stepZ
        }
    }
}

private class ExplosionExposureGrid(private val box: AABB) {
    val stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0)
    val stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0)
    val stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0)
    private val offsetX = (1.0 - floor(1.0 / stepX) * stepX) / 2.0
    private val offsetZ = (1.0 - floor(1.0 / stepZ) * stepZ) / 2.0

    val hasInvalidSteps = stepX < 0.0 || stepY < 0.0 || stepZ < 0.0

    fun samplePoint(xStep: Double, yStep: Double, zStep: Double) = Vec3(
        Mth.lerp(xStep, box.minX, box.maxX) + offsetX,
        Mth.lerp(yStep, box.minY, box.maxY),
        Mth.lerp(zStep, box.minZ, box.maxZ) + offsetZ,
    )
}

private class ExposureTally {
    private var clearRays = 0
    private var totalRays = 0

    fun record(clear: Boolean) {
        if (clear) clearRays++
        totalRays++
    }

    fun ratio() = clearRays.toFloat() / totalRays.toFloat()
}
