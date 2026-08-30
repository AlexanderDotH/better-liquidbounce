/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.module.modules.render.animations

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.InteractionHand

object AnimationRenderHook {
    @JvmStatic fun isSwingAnimationEnabled() = ModuleAnimations.running && SwingAnimations.enabled

    @JvmStatic
    fun renderMainHandSwing(player: AbstractClientPlayer, attack: Float, poseStack: PoseStack) {
        SwingAnimations.onRenderItem(player, InteractionHand.MAIN_HAND, attack, poseStack)
    }
}
