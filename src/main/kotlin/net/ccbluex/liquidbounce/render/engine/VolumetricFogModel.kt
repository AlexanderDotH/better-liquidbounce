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

package net.ccbluex.liquidbounce.render.engine

import kotlin.math.max

internal fun volumetricFogStrength(strength: Float): Float =
    strength.coerceIn(MIN_VOLUME_STRENGTH, MAX_VOLUME_STRENGTH) / NEUTRAL_VOLUME_STRENGTH

internal fun volumetricFogMaxDistance(environmentalEnd: Float, renderDistanceEnd: Float): Float =
    max(environmentalEnd, renderDistanceEnd).coerceIn(MIN_VOLUME_DISTANCE, MAX_VOLUME_DISTANCE)

internal fun volumetricCameraClearRadius(radius: Float): Float = radius.coerceIn(0f, MAX_CAMERA_CLEAR_RADIUS)

internal fun volumetricInteractionStrength(nukerRunning: Boolean): Float =
    if (nukerRunning) NUKER_VOLUME_FACTOR else 1f

internal fun wrapVolumetricFogCoordinate(coordinate: Double): Float {
    return (coordinate % VOLUME_WORLD_PERIOD.toDouble()).toFloat()
}

internal data class VolumetricFogLayerSettings(
    val spacing: Float,
    val groundDensity: Float,
    val middleDensity: Float,
    val upperDensity: Float,
) {
    companion object {
        fun from(
            enabled: Boolean,
            spacing: Float,
            groundDensity: Int,
            middleDensity: Int,
            upperDensity: Int,
        ) = VolumetricFogLayerSettings(
            spacing = if (enabled) spacing.coerceIn(MIN_LAYER_SPACING, MAX_LAYER_SPACING) else 0f,
            groundDensity = groundDensity.toDensity(),
            middleDensity = middleDensity.toDensity(),
            upperDensity = upperDensity.toDensity(),
        )
    }
}

private fun Int.toDensity(): Float = coerceIn(0, 100) / 100f

private const val MIN_VOLUME_STRENGTH = 4f
private const val NEUTRAL_VOLUME_STRENGTH = 14f
private const val MAX_VOLUME_STRENGTH = 24f
private const val MIN_VOLUME_DISTANCE = 16f
private const val MAX_VOLUME_DISTANCE = 1024f
private const val MAX_CAMERA_CLEAR_RADIUS = 32f
private const val NUKER_VOLUME_FACTOR = 0.2f
private const val MIN_LAYER_SPACING = 16f
private const val MAX_LAYER_SPACING = 128f
internal const val VOLUME_WORLD_PERIOD = 4096f
