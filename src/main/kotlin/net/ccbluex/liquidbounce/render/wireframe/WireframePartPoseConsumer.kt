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

package net.ccbluex.liquidbounce.render.wireframe

import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal fun interface WireframePartPoseConsumer {
    fun accept(box: AABB, pivot: Vec3, xRot: Float, yRot: Float, zRot: Float)
}

internal fun swimmingWireframeHeadRotation(xRot: Float, swimAmount: Float): Float {
    val swimProgress = if (swimAmount > 0f) swimAmount else 1f
    return Mth.lerp(swimProgress, xRot, SWIM_HEAD_TARGET_ROTATION)
}

internal fun forEachSwimmingWireframePart(headRotation: Float, consumer: WireframePartPoseConsumer) {
    consumer.accept(RENDER_BODY, RENDER_BODY.center, 0f, 0f, 0f)
    consumer.accept(RENDER_LEFT_ARM, RENDER_LEFT_ARM.center, 0f, 0f, SWIM_LEFT_ARM_ROLL)
    consumer.accept(RENDER_RIGHT_ARM, RENDER_RIGHT_ARM.center, 0f, 0f, SWIM_RIGHT_ARM_ROLL)
    consumer.accept(RENDER_LEFT_LEG, RENDER_LEFT_LEG.center, 0f, 0f, SWIM_LEFT_LEG_ROLL)
    consumer.accept(RENDER_RIGHT_LEG, RENDER_RIGHT_LEG.center, 0f, 0f, SWIM_RIGHT_LEG_ROLL)
    consumer.accept(RENDER_HEAD, RENDER_HEAD.bottomCenter, headRotation, 0f, 0f)
}
