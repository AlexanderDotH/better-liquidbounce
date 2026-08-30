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

import net.ccbluex.liquidbounce.event.events.PerspectiveEvent;
import net.ccbluex.liquidbounce.features.module.modules.combat.aimbot.ModuleDroneControl;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFreeLook;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleQuickPerspectiveSwap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public final class CameraOrientationHook {

    private CameraOrientationHook() {
    }

    public static boolean apply(
        @Nullable Entity entity,
        Minecraft minecraft,
        Runnable detached,
        FloatSupplier yRot,
        FloatSupplier xRot,
        RotationSetter setRotation,
        ZoomLimiter getMaxZoom,
        CameraMover move,
        Consumer<Vec3> setPosition
    ) {
        var freeLook = ModuleFreeLook.INSTANCE.getRunning();
        var freeLockInvertedView = ModuleFreeLook.INSTANCE.isInvertedView();
        var qps = ModuleQuickPerspectiveSwap.INSTANCE.getRunning();
        var rearView = isRearView(qps, freeLook, minecraft);

        if (freeLook || qps) {
            detached.run();
            applyFreeLook(freeLook, freeLockInvertedView, setRotation);
            applyQuickPerspectiveSwap(qps, rearView, freeLook, freeLockInvertedView, yRot, xRot, setRotation);
            moveCamera(entity, getMaxZoom, move);
            return true;
        }

        applyDroneCamera(setPosition, setRotation);
        return false;
    }

    private static boolean isRearView(boolean qps, boolean freeLook, Minecraft minecraft) {
        return qps && ModuleQuickPerspectiveSwap.INSTANCE.getRearView() && !freeLook
            && minecraft.options.getCameraType().isFirstPerson();
    }

    private static void applyFreeLook(boolean freeLook, boolean invertedView, RotationSetter setRotation) {
        if (!freeLook) {
            return;
        }

        var cameraYaw = ModuleFreeLook.INSTANCE.getCameraYaw();
        var cameraPitch = ModuleFreeLook.INSTANCE.getCameraPitch();
        if (invertedView) {
            setRotation.set(cameraYaw + 180, -cameraPitch);
        } else {
            setRotation.set(cameraYaw, cameraPitch);
        }
    }

    private static void applyQuickPerspectiveSwap(
        boolean qps,
        boolean rearView,
        boolean freeLook,
        boolean invertedView,
        FloatSupplier yRot,
        FloatSupplier xRot,
        RotationSetter setRotation
    ) {
        if (qps && !rearView) {
            setRotation.set(yRot.get() + 180.0f, freeLook && !invertedView ? xRot.get() : -xRot.get());
        }
    }

    private static void moveCamera(@Nullable Entity entity, ZoomLimiter getMaxZoom, CameraMover move) {
        float scale = entity instanceof LivingEntity livingEntity ? livingEntity.getScale() : 1.0F;
        float desiredCameraDistance = PerspectiveEvent.INSTANCE.getDistance();
        move.move(-getMaxZoom.limit(desiredCameraDistance * scale), 0.0f, 0.0f);
    }

    private static void applyDroneCamera(Consumer<Vec3> setPosition, RotationSetter setRotation) {
        var screen = ModuleDroneControl.INSTANCE.getScreen();
        if (screen != null) {
            setPosition.accept(screen.getCameraPos());
            setRotation.set(screen.getCameraRotation().yRot(), screen.getCameraRotation().xRot());
        }
    }

    @FunctionalInterface
    public interface FloatSupplier {
        float get();
    }

    @FunctionalInterface
    public interface RotationSetter {
        void set(float yRot, float xRot);
    }

    @FunctionalInterface
    public interface ZoomLimiter {
        float limit(float maxZoom);
    }

    @FunctionalInterface
    public interface CameraMover {
        void move(float zoom, float dy, float dx);
    }
}
