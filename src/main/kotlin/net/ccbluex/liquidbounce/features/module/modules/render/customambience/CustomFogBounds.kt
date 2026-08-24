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
package net.ccbluex.liquidbounce.features.module.modules.render.customambience

/** Immutable fog bounds shared by vanilla fog data and the custom volume pass. */
internal data class CustomFogBounds(
    val environmentalStart: Float,
    val environmentalEnd: Float,
    val renderDistanceStart: Float,
    val renderDistanceEnd: Float,
    val skyEnd: Float,
    val cloudEnd: Float,
) {

    fun withDensity(densityPercent: Int): CustomFogBounds = copy(
        environmentalStart = effectiveFogStart(environmentalStart, densityPercent),
        renderDistanceStart = effectiveFogStart(renderDistanceStart, densityPercent),
    )
}

/** Moves positive fog starts toward the camera without weakening zero or negative starts. */
internal fun effectiveFogStart(start: Float, densityPercent: Int): Float {
    val density = densityPercent.coerceIn(0, 100) / 100f
    return start + (minOf(start, 0f) - start) * density
}

/** Matches `linear_fog_value` from Minecraft's `assets/minecraft/shaders/include/fog.glsl`. */
internal fun linearFogFactor(distance: Float, start: Float, end: Float): Float {
    if (distance <= start) {
        return 0f
    }
    if (distance >= end) {
        return 1f
    }

    return (distance - start) / (end - start)
}

/** Matches vanilla's maximum of spherical environmental and cylindrical render-distance fog. */
internal fun totalFogFactor(
    sphericalDistance: Float,
    cylindricalDistance: Float,
    bounds: CustomFogBounds,
): Float = maxOf(
    linearFogFactor(sphericalDistance, bounds.environmentalStart, bounds.environmentalEnd),
    linearFogFactor(cylindricalDistance, bounds.renderDistanceStart, bounds.renderDistanceEnd),
)

internal fun shouldApplyFogVolume(
    fogRunning: Boolean,
    volumeRunning: Boolean,
): Boolean = fogRunning && volumeRunning

internal fun shouldApplyFogBlur(
    fogRunning: Boolean,
    blurRunning: Boolean,
): Boolean = fogRunning && blurRunning
