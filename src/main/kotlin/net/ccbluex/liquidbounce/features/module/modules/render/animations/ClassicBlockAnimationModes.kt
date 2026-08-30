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
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.minecraft.util.Mth
import net.minecraft.world.entity.HumanoidArm
import org.joml.Quaternionf

abstract class BlockAnimationMode(name: String) : Mode(name) {
    override val parent: ModeValueGroup<*>
        get() = ModuleAnimations.blockAnimationChoice

    protected fun applySwingOffset(matrices: PoseStack, arm: HumanoidArm, swingProgress: Float) {
        val armSide = if (arm == HumanoidArm.RIGHT) 1 else -1
        val firstSwing = Mth.sin(swingProgress * swingProgress * Math.PI)
        matrices.mulPose(Axis.YP.rotationDegrees(armSide * (45f + firstSwing * -20f)))
        val secondSwing = Mth.sin(Mth.sqrt(swingProgress) * Math.PI)
        matrices.mulPose(Axis.ZP.rotationDegrees(armSide * secondSwing * -20f))
        matrices.mulPose(Axis.XP.rotationDegrees(secondSwing * -80f))
        matrices.mulPose(Axis.YP.rotationDegrees(armSide * -45f))
    }

    abstract fun transform(matrices: PoseStack, arm: HumanoidArm, equipProgress: Float, swingProgress: Float)
}

open class OneSevenAnimationMode : BlockAnimationMode("1.7") {
    private val translateY by float("Y", 0.1f, 0.05f..0.3f)
    private val swingProgressScale by float("SwingScale", 0.9f, 0.1f..1.0f)

    override fun transform(matrices: PoseStack, arm: HumanoidArm, equipProgress: Float, swingProgress: Float) {
        matrices.translate(if (arm == HumanoidArm.RIGHT) -0.1f else 0.1f, translateY, 0f)
        applySwingOffset(matrices, arm, swingProgress * swingProgressScale)
    }
}

open class PushdownAnimationMode : BlockAnimationMode("Pushdown") {
    override fun transform(matrices: PoseStack, arm: HumanoidArm, equipProgress: Float, swingProgress: Float) {
        matrices.translate(if (arm == HumanoidArm.RIGHT) -0.1f else 0.1f, 0.1f, 0f)
        val swing = Mth.sin(Mth.sqrt(swingProgress) * Math.PI)
        val armSide = if (arm == HumanoidArm.RIGHT) 1 else -1
        matrices.mulPose(Axis.ZP.rotationDegrees(armSide * swing * 10f))
        matrices.mulPose(Axis.XP.rotationDegrees(swing * -35f))
    }
}

open class SigmaAnimationMode : BlockAnimationMode("Sigma") {
    private val translateY by float("Y", 0.1f, 0.05f..0.3f)

    override fun transform(matrices: PoseStack, arm: HumanoidArm, equipProgress: Float, swingProgress: Float) {
        val sine = Mth.sin(Mth.sqrt(swingProgress) * Math.PI)
        val side = if (arm == HumanoidArm.RIGHT) 1f else -1f
        val rotation = Quaternionf().rotationAxis(
            (-sine * 27.5f * side).toRadians(), -8f * side, 0f, 9f,
        ).rotateAxis(
            (-sine * 45f * side).toRadians(), side, sine / 2f, 0f,
        )
        matrices.mulPose(rotation)
        matrices.translate(0f, translateY, 0f)
        applySwingOffset(matrices, arm, 0f)
    }
}

open class ExhibitionAnimationMode : BlockAnimationMode("Exhibition") {
    private val translateY by float("Y", 0.1f, 0.05f..0.3f)

    override fun transform(matrices: PoseStack, arm: HumanoidArm, equipProgress: Float, swingProgress: Float) {
        val sine = Mth.sin(Mth.sqrt(swingProgress) * Math.PI)
        val side = if (arm == HumanoidArm.RIGHT) 1f else -1f
        matrices.translate(0.0, -0.1, 0.0)
        applySwingOffset(matrices, arm, 0f)
        matrices.translate(0.1f, 0.4f, -0.1f)
        val rotation = Quaternionf().rotationAxis(
            (-sine * 30f * side).toRadians(), sine / 2f, 0f, 9f,
        ).rotateAxis(
            (-sine * 50f * side).toRadians(), 0.8f * side, sine / 2f, 0f,
        )
        matrices.mulPose(rotation)
        matrices.translate(0f, translateY - 0.2f, 0f)
    }
}
