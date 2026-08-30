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
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.minecraft.util.Mth
import net.minecraft.world.entity.HumanoidArm
import org.joml.Quaternionf

open class AvatarAnimationMode : BlockAnimationMode("Avatar") {
    private val translateY by float("Y", 0.1f, 0.05f..0.3f)

    override fun transform(matrices: PoseStack, arm: HumanoidArm, equipProgress: Float, swingProgress: Float) {
        val sine = Mth.sin(Mth.sqrt(swingProgress) * Math.PI)
        val squaredSine = Mth.sin(swingProgress * swingProgress * Math.PI)
        val side = if (arm == HumanoidArm.RIGHT) 1f else -1f
        matrices.translate(0.2f * side, translateY, 0f)
        val rotation = Quaternionf().rotationAxis(0f, 0f, 1f, 0f)
            .rotateY((squaredSine * -20f * side).toRadians())
            .rotateZ((sine * -20f * side).toRadians())
            .rotateAxis((sine * -40f * side).toRadians(), side, 0f, 0f)
        matrices.mulPose(rotation)
        applySwingOffset(matrices, arm, 0f)
    }
}

open class DortwareAnimationMode : BlockAnimationMode("Dortware") {
    private val translateY by float("Y", 0.1f, 0.05f..0.3f)

    override fun transform(matrices: PoseStack, arm: HumanoidArm, equipProgress: Float, swingProgress: Float) {
        val sine = Mth.sin(Mth.sqrt(swingProgress) * Math.PI)
        val alternateSine = Mth.sin(Mth.sqrt(swingProgress) * Math.PI - 3)
        val rotation = Quaternionf().rotationAxis(
            (-sine * 10).toRadians(), 0f, 15f, 200f,
        ).rotateAxis(
            (-sine * 10f).toRadians(), 300f, sine / 2f, 1f,
        )
        matrices.mulPose(rotation)
        matrices.translate(3.4, 0.3, -0.4)
        matrices.translate(-2.10f, -0.2f, 0.1f)
        matrices.mulPose(
            Quaternionf().rotationAxis(
                (alternateSine * 13f).toRadians(), -10f, -1.4f, -10f,
            ),
        )
        matrices.translate(if (arm == HumanoidArm.RIGHT) -1f else -2f, translateY, 0f)
    }
}
