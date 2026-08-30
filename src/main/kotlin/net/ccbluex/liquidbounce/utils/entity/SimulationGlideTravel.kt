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

import net.ccbluex.liquidbounce.utils.math.fastCos
import net.ccbluex.liquidbounce.utils.math.fastSin
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

private data class GlideContext(
    val view: Vec3,
    val pitchRadians: Float,
    val horizontalViewMagnitude: Double,
    val horizontalVelocity: Double,
    val liftFactor: Double,
)

internal fun SimulatedPlayer.travelWhileGliding(gravity: Double) {
    if (deltaMovement.y > -0.5) {
        fallDistance = 1.0
    }

    val context = glideContext(deltaMovement)
    var velocity = deltaMovement.add(0.0, gravity * (-1.0 + context.liftFactor * 0.75), 0.0)
    velocity = applyDownwardLift(velocity, context)
    velocity = applyPitchLift(velocity, context)
    velocity = alignWithView(velocity, context)
    deltaMovement = velocity.multiply(0.99, 0.98, 0.99)
    moveSimulated(deltaMovement)
}

private fun SimulatedPlayer.glideContext(velocity: Vec3): GlideContext {
    val view = getViewVector()
    val pitchRadians = xRot * (Math.PI.toFloat() / 180)
    val horizontalViewMagnitude = sqrt(view.x * view.x + view.z * view.z)
    val viewLength = view.length()
    val cosine = pitchRadians.fastCos().toDouble()
    val liftFactor = cosine * (cosine * 1.0.coerceAtMost(viewLength / 0.4))
    return GlideContext(view, pitchRadians, horizontalViewMagnitude, velocity.horizontalDistance(), liftFactor)
}

private fun applyDownwardLift(velocity: Vec3, context: GlideContext): Vec3 {
    if (velocity.y >= 0.0 || context.horizontalViewMagnitude <= 0.0) {
        return velocity
    }
    val lift = velocity.y * -0.1 * context.liftFactor
    return velocity.add(
        context.view.x * lift / context.horizontalViewMagnitude,
        lift,
        context.view.z * lift / context.horizontalViewMagnitude,
    )
}

private fun applyPitchLift(velocity: Vec3, context: GlideContext): Vec3 {
    if (context.pitchRadians >= 0.0f || context.horizontalViewMagnitude <= 0.0) {
        return velocity
    }
    val lift = context.horizontalVelocity * (-context.pitchRadians.fastSin()).toDouble() * 0.04
    return velocity.add(
        -context.view.x * lift / context.horizontalViewMagnitude,
        lift * 3.2,
        -context.view.z * lift / context.horizontalViewMagnitude,
    )
}

private fun alignWithView(velocity: Vec3, context: GlideContext): Vec3 {
    if (context.horizontalViewMagnitude <= 0.0) {
        return velocity
    }
    return velocity.add(
        (context.view.x / context.horizontalViewMagnitude * context.horizontalVelocity - velocity.x) * 0.1,
        0.0,
        (context.view.z / context.horizontalViewMagnitude * context.horizontalVelocity - velocity.z) * 0.1,
    )
}
