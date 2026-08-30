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
package net.ccbluex.liquidbounce.injection.hooks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ccbluex.liquidbounce.features.module.modules.render.animations.ModuleAnimations;
import net.ccbluex.liquidbounce.features.module.modules.render.animations.ModuleAnimationsKt;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class FirstPersonItemTransformHook {

    private FirstPersonItemTransformHook() {
    }

    public static void apply(InteractionHand hand, ItemStack itemStack, ItemStack offHandItem, PoseStack poseStack) {
        if (!ModuleAnimations.INSTANCE.getRunning()) {
            return;
        }

        var isInBothHands = InteractionHand.MAIN_HAND == hand && itemStack.has(DataComponents.MAP_ID)
            && offHandItem.isEmpty();
        ModuleAnimations.MainHand mainHand = ModuleAnimations.MainHand.INSTANCE;
        ModuleAnimations.OffHand offHand = ModuleAnimations.OffHand.INSTANCE;
        if (isInBothHands && mainHand.getRunning() && offHand.getRunning()) {
            applyBothHandsTransform(poseStack, mainHand, offHand);
        } else if (isInBothHands && mainHand.getRunning()) {
            poseStack.translate(0f, 0f, mainHand.getMainHandItemScale());
        } else if (InteractionHand.MAIN_HAND == hand && mainHand.getRunning()) {
            applyTransformations(
                poseStack,
                mainHand.getMainHandX(),
                mainHand.getMainHandY(),
                mainHand.getMainHandItemScale(),
                mainHand.getMainHandPositiveX(),
                mainHand.getMainHandPositiveY(),
                mainHand.getMainHandPositiveZ()
            );
        } else if (ModuleAnimationsKt.shouldApplyOffHandTransform(hand, isInBothHands, offHand.getRunning())) {
            applyTransformations(
                poseStack,
                offHand.getOffHandX(),
                offHand.getOffHandY(),
                offHand.getOffHandItemScale(),
                offHand.getOffHandPositiveX(),
                offHand.getOffHandPositiveY(),
                offHand.getOffHandPositiveZ()
            );
        }
    }

    private static void applyBothHandsTransform(
        PoseStack poseStack,
        ModuleAnimations.MainHand mainHand,
        ModuleAnimations.OffHand offHand
    ) {
        applyTransformations(
            poseStack,
            (mainHand.getMainHandX() + offHand.getOffHandX()) / 2f,
            (mainHand.getMainHandY() + offHand.getOffHandY()) / 2f,
            (mainHand.getMainHandItemScale() + offHand.getOffHandItemScale()) / 2f,
            (mainHand.getMainHandPositiveX() + offHand.getOffHandPositiveX()) / 2f,
            (mainHand.getMainHandPositiveY() + offHand.getOffHandPositiveY()) / 2f,
            (mainHand.getMainHandPositiveZ() + offHand.getOffHandPositiveZ()) / 2f
        );
    }

    private static void applyTransformations(
        PoseStack matrices,
        float translateX,
        float translateY,
        float translateZ,
        float rotateX,
        float rotateY,
        float rotateZ
    ) {
        matrices.translate(translateX, translateY, translateZ);
        matrices.mulPose(Axis.XP.rotationDegrees(rotateX));
        matrices.mulPose(Axis.YP.rotationDegrees(rotateY));
        matrices.mulPose(Axis.ZP.rotationDegrees(rotateZ));
    }
}
