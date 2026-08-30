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
package net.ccbluex.liquidbounce.render.target

import net.minecraft.world.entity.LivingEntity
import kotlin.math.max
import kotlin.math.min

internal class TargetHeartAnimationState {
    val layout = TargetHeartLayout()
    var flashStrength = 0f
        private set
    var squeezeStrength = 0f
        private set
    private var currentTargetId = -1
    private var lastUpdateTime = 0L

    fun update(
        target: LivingEntity,
        heartCount: Int,
        heartSize: Float,
        configuredSqueezeStrength: Float,
        squeezeSpeed: Int,
    ) {
        val now = System.currentTimeMillis()
        var deltaSeconds = if (lastUpdateTime != 0L) {
            ((now - lastUpdateTime) / 1000f).coerceAtMost(0.25f)
        } else {
            0f
        }
        lastUpdateTime = now
        if (target.id != currentTargetId) {
            currentTargetId = target.id
            flashStrength = 0f
            squeezeStrength = 0f
            deltaSeconds = 0f
            layout.markDirty()
        }

        flashStrength = if (target.hurtTime in 8..10) 1f else max(0f, flashStrength - deltaSeconds * 3.5f)
        val inAnimation = (5 + squeezeSpeed)..10
        val outAnimation = (1 + squeezeSpeed)..(4 + squeezeSpeed)
        squeezeStrength += when (target.hurtTime) {
            in inAnimation -> max(0f, deltaSeconds * configuredSqueezeStrength * 5)
            in outAnimation -> -min(squeezeStrength, deltaSeconds * configuredSqueezeStrength * 5)
            else -> -squeezeStrength
        }
        layout.ensure(heartCount, heartSize)
    }
}
