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
import net.ccbluex.liquidbounce.features.module.modules.fun.ModuleDankBobbing;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleBlockESP;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleItemESP;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleNoBob;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleOrbESP;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleStorageESP;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleTracers;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;

public final class GameRendererBobbingHook {

    private GameRendererBobbingHook() {
    }

    public static boolean apply(CameraRenderState cameraState, PoseStack poseStack) {
        if (shouldSuppressVanillaBobbing()) {
            return true;
        }

        if (!ModuleDankBobbing.INSTANCE.getRunning()) {
            return false;
        }

        var entityRenderState = cameraState.entityRenderState;
        if (!entityRenderState.isPlayer) {
            return false;
        }

        float additionalBobbing = ModuleDankBobbing.INSTANCE.getMotion();
        float g = entityRenderState.backwardsInterpolatedWalkDistance;
        float h = entityRenderState.bob;
        poseStack.translate(Mth.sin(g * Mth.PI) * h * 0.5f, -Math.abs(Mth.cos(g * Mth.PI) * h), 0.0f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(h * Mth.PI) * h * (3.0F + additionalBobbing)));
        poseStack.mulPose(
            Axis.XP.rotationDegrees(Math.abs(Mth.cos(h * Mth.PI - (0.2F + additionalBobbing)) * h) * 5.0F)
        );
        return true;
    }

    private static boolean shouldSuppressVanillaBobbing() {
        return ModuleNoBob.INSTANCE.getRunning() ||
            ModuleTracers.INSTANCE.getRunning() ||
            ModuleBlockESP.INSTANCE.showTracers() ||
            (ModuleItemESP.INSTANCE.getRunning() && ModuleItemESP.INSTANCE.getShowTracers()) ||
            (ModuleOrbESP.INSTANCE.getRunning() && ModuleOrbESP.INSTANCE.getShowTracers()) ||
            ModuleStorageESP.INSTANCE.showTracers();
    }
}
