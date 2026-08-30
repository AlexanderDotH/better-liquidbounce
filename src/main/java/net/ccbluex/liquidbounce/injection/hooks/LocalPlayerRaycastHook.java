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

import net.ccbluex.liquidbounce.features.module.modules.player.ModuleReach;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFreeCam;
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleLiquidPlace;
import net.ccbluex.liquidbounce.utils.aiming.RotationManager;
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation;
import net.ccbluex.liquidbounce.utils.raytracing.EntityRaytracingKt;
import net.ccbluex.liquidbounce.utils.raytracing.RaytracingKt;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

public final class LocalPlayerRaycastHook {

    private LocalPlayerRaycastHook() {
    }

    public static HitResult modify(
        HitResult original,
        Entity camera,
        double blockInteractionRange,
        double entityInteractionRange,
        float tickDelta
    ) {
        if (camera != Minecraft.getInstance().player) {
            return original;
        }

        var cameraRotation = new Rotation(camera.getViewYRot(tickDelta), camera.getViewXRot(tickDelta), true);
        var rotation = selectRotation(cameraRotation);
        var throughWallsHit = findThroughWallsEntity(rotation);
        if (throughWallsHit != null) {
            return throughWallsHit;
        }

        return RaytracingKt.traceFromPlayer(
            rotation,
            Math.max(blockInteractionRange, entityInteractionRange),
            ClipContext.Block.OUTLINE,
            ModuleLiquidPlace.INSTANCE.getRunning() ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
            tickDelta
        );
    }

    private static Rotation selectRotation(Rotation cameraRotation) {
        if (ModuleFreeCam.INSTANCE.getRunning()) {
            var serverRotation = RotationManager.INSTANCE.getServerRotation();
            return ModuleFreeCam.INSTANCE.shouldDisableCameraInteract() ? serverRotation : cameraRotation;
        }

        if (RotationManager.INSTANCE.getCurrentRotation() != null) {
            return RotationManager.INSTANCE.getCurrentRotation();
        }

        return cameraRotation;
    }

    private static @Nullable EntityHitResult findThroughWallsEntity(Rotation rotation) {
        if (!ModuleReach.INSTANCE.getRunning()) {
            return null;
        }

        var throughWallsRange = ModuleReach.INSTANCE.getEntity().getInteractionThroughWallsRange();
        if (throughWallsRange <= 0.0) {
            return null;
        }

        var hitEntityResult = EntityRaytracingKt.findEntityInCrosshair(throughWallsRange, rotation, null);
        return hitEntityResult != null && hitEntityResult.getType() == HitResult.Type.ENTITY ? hitEntityResult : null;
    }
}
