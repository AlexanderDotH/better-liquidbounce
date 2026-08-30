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

package net.ccbluex.liquidbounce.features.module.modules.render.animations

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.util.Mth
import net.minecraft.world.entity.HumanoidArm

internal class SwingTransformContext(
    val poseStack: PoseStack,
    val swing: Float,
    arm: HumanoidArm,
) {
    val side = if (arm == HumanoidArm.RIGHT) 1 else -1
    val squareRoot = Mth.sqrt(swing)
    val curve = sin(squareRoot * PI)
    val squaredCurve = sin(swing * swing * PI)
    val smoothCurve = sin(swing * PI) * 0.5f

    fun sin(value: Float): Float = Mth.sin(value.toDouble())

    companion object {
        const val PI = Math.PI.toFloat()
    }
}

internal object BasicSwingTransforms {

    fun swipe(context: SwingTransformContext) = with(context) {
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0f + swing * -20.0f)))
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * curve * -70.0f))
        poseStack.mulPose(Axis.XP.rotationDegrees(-70f))
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0f))
    }

    fun spin(context: SwingTransformContext) = with(context) {
        poseStack.mulPose(Axis.XP.rotationDegrees(swing * -360f))
    }

    fun hook(context: SwingTransformContext) = with(context) {
        poseStack.mulPose(Axis.XP.rotationDegrees(50f))
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (-30f * (1f - curve) - 30f)))
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 110f))
    }

    fun dash(context: SwingTransformContext) = with(context) {
        poseStack.mulPose(Axis.XP.rotationDegrees(50f))
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (-60f * curve - 50f)))
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 110f))
    }

    fun tap(context: SwingTransformContext) = with(context) {
        poseStack.mulPose(Axis.XP.rotationDegrees(50f))
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -60f))
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * (110f + 20f * curve)))
    }

    fun inject(context: SwingTransformContext) = with(context) {
        poseStack.translate(0.0, 0.0, (-curve / 4.0))
        poseStack.mulPose(Axis.XP.rotationDegrees(-120f))
    }

    fun slap(context: SwingTransformContext) = with(context) {
        poseStack.mulPose(Axis.XP.rotationDegrees(-sin(swing * 3f) * 60f))
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -60f * curve))
    }
}

internal object StyledSwingTransforms {

    fun akrien(context: SwingTransformContext) = with(context) {
        if (swing > 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 45f))
            poseStack.mulPose(Axis.XP.rotationDegrees(curve * -85.0f))
            poseStack.translate(side * -0.1, 0.28, 0.2)
            poseStack.mulPose(Axis.XP.rotationDegrees(-85.0f))
            return@with
        }

        val verticalOffset = 0.2f * sin(squareRoot * SwingTransformContext.PI * 2f)
        val depthOffset = -0.2f * sin(swing * SwingTransformContext.PI)
        poseStack.translate(side * (-0.4f * curve).toDouble(), verticalOffset.toDouble(), depthOffset.toDouble())
        applySwingOffset(context)
    }

    fun smooth(context: SwingTransformContext) = with(context) {
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0f + squaredCurve * -20.0f)))
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * curve * -20.0f))
        poseStack.mulPose(Axis.XP.rotationDegrees(curve * -80.0f))
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0f))
        poseStack.translate(0.0, -0.1, 0.0)
    }

    fun power(context: SwingTransformContext) = with(context) {
        poseStack.translate((-smoothCurve * smoothCurve * squaredCurve * side).toDouble(), 0.0, 0.0)
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 61f))
        poseStack.mulPose(Axis.ZP.rotationDegrees(curve))
        poseStack.mulPose(Axis.YP.rotationDegrees(curve * squaredCurve * -5.0f))
        poseStack.mulPose(Axis.XP.rotationDegrees(curve * squaredCurve * -30.0f))
        poseStack.mulPose(Axis.XP.rotationDegrees(-60.0f))
        poseStack.mulPose(Axis.XP.rotationDegrees(smoothCurve * -60.0f))
    }

    fun feast(context: SwingTransformContext) = with(context) {
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 30f))
        poseStack.mulPose(Axis.YP.rotationDegrees(curve * 75.0f * side))
        poseStack.mulPose(Axis.XP.rotationDegrees(curve * -45.0f))
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 30f))
        poseStack.mulPose(Axis.XP.rotationDegrees(-80.0f))
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 35f))
    }

    private fun applySwingOffset(context: SwingTransformContext) = with(context) {
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0f + squaredCurve * -20.0f)))
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * curve * -20.0f))
        poseStack.mulPose(Axis.XP.rotationDegrees(curve * -80.0f))
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0f))
    }
}
