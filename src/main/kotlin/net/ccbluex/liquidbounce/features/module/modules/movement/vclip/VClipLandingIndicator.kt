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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

internal data class VClipLandingIndicatorState(
    val direction: VClipDirection,
    val renderPosition: Vec3,
    val verticalDistance: Double,
) {
    val color: Color4b
        get() = VClipLandingIndicator.colorForDistance(verticalDistance)
}

internal object VClipLandingIndicator {

    private const val SAFE_DISTANCE = 50.0
    private const val CAUTION_DISTANCE = 80.0
    private const val DANGER_DISTANCE = 100.0

    private val SAFE_COLOR = Color4b(0x20, 0xC2, 0x06)
    private val CAUTION_COLOR = Color4b.ORANGE
    private val DANGER_COLOR = Color4b(0xD7, 0x09, 0x09)

    fun resolve(
        origin: VClipPosition,
        target: VClipPosition?,
        direction: VClipDirection,
    ): VClipLandingIndicatorState? {
        target ?: return null
        val verticalDistance = ((target.y - origin.y) * direction.verticalSign)
            .takeIf { it.isFinite() && it > 0.0 }
            ?: return null

        return VClipLandingIndicatorState(
            direction = direction,
            renderPosition = Vec3(floor(target.x), target.y, floor(target.z)),
            verticalDistance = verticalDistance,
        )
    }

    fun colorForDistance(verticalDistance: Double): Color4b {
        require(verticalDistance.isFinite() && verticalDistance >= 0.0) {
            "VClip landing distance must be finite and non-negative"
        }

        return when {
            verticalDistance <= SAFE_DISTANCE -> SAFE_COLOR
            verticalDistance < CAUTION_DISTANCE -> interpolate(
                SAFE_COLOR,
                CAUTION_COLOR,
                verticalDistance,
                SAFE_DISTANCE,
                CAUTION_DISTANCE,
            )
            verticalDistance < DANGER_DISTANCE -> interpolate(
                CAUTION_COLOR,
                DANGER_COLOR,
                verticalDistance,
                CAUTION_DISTANCE,
                DANGER_DISTANCE,
            )
            else -> DANGER_COLOR
        }
    }

    private fun interpolate(
        fromColor: Color4b,
        toColor: Color4b,
        distance: Double,
        fromDistance: Double,
        toDistance: Double,
    ): Color4b {
        val progress = (distance - fromDistance) / (toDistance - fromDistance)
        return fromColor.interpolateTo(toColor, progress)
    }
}
