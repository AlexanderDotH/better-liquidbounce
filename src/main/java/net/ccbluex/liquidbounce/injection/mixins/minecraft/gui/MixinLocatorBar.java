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

package net.ccbluex.liquidbounce.injection.mixins.minecraft.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ccbluex.liquidbounce.integration.theme.component.ClientPlayerLocatorBar;
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(LocatorBar.class)
public abstract class MixinLocatorBar {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void hookDisableLocatorBarTweak(final CallbackInfo ci) {
        if (HudComponentManager.shouldSuppressLocatorBar()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void hookDisableLocatorBarTweakOnBackground(final CallbackInfo ci) {
        if (HudComponentManager.shouldSuppressLocatorBar()) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/waypoints/ClientWaypointManager;forEachWaypoint(" +
                            "Lnet/minecraft/world/entity/Entity;Ljava/util/function/Consumer;)V"
            )
    )
    private void hookClientPlayerWaypoints(
            ClientWaypointManager waypointManager,
            Entity cameraEntity,
            Consumer<TrackedWaypoint> renderer,
            Operation<Void> original,
            GuiGraphicsExtractor context,
            DeltaTracker tickCounter
    ) {
        Consumer<TrackedWaypoint> rendererWithHeads = waypoint -> {
            renderer.accept(waypoint);
            ClientPlayerLocatorBar.extractPlayerHead(context, tickCounter, cameraEntity, waypoint);
        };

        original.call(waypointManager, cameraEntity, rendererWithHeads);
        ClientPlayerLocatorBar.appendFallbackWaypoints(
                waypointManager.hasWaypoints(),
                cameraEntity,
                rendererWithHeads
        );
    }

}
