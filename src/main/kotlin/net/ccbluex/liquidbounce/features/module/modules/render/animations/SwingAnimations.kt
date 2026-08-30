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
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm

object SwingAnimations : ToggleableValueGroup(ModuleAnimations, "SwingAnimations", false) {

    val mode by enumChoice("Mode", Mode.Spin)

    enum class Mode(
        override val tag: String,
        private val transform: (SwingTransformContext) -> Unit,
    ) : Tagged {
        Swipe("Swipe", BasicSwingTransforms::swipe),
        Spin("Spin", BasicSwingTransforms::spin),
        Hook("Hook", BasicSwingTransforms::hook),
        Dash("Dash", BasicSwingTransforms::dash),
        Tap("Tap", BasicSwingTransforms::tap),
        Inject("Inject", BasicSwingTransforms::inject),
        Slap("Slap", BasicSwingTransforms::slap),
        Akrien("Akrien", StyledSwingTransforms::akrien),
        Smooth("Smooth", StyledSwingTransforms::smooth),
        Power("Power", StyledSwingTransforms::power),
        Feast("Feast", StyledSwingTransforms::feast),
        ;

        internal fun apply(context: SwingTransformContext) = transform(context)
    }

    fun onRenderItem(player: AbstractClientPlayer,
                     hand: InteractionHand,
                     swingProgress: Float,
                     poseStack: PoseStack
    ) {
        val isMainHand = hand == InteractionHand.MAIN_HAND
        val arm = if (isMainHand) player.mainArm else player.mainArm.opposite

        applySwing(poseStack, swingProgress, arm)
    }

    private fun applySwing(poseStack: PoseStack, swing: Float, arm: HumanoidArm) =
        mode.apply(SwingTransformContext(poseStack, swing, arm))
}
