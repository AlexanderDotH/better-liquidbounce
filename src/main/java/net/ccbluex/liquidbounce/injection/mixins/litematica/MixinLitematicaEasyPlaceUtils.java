/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.injection.mixins.litematica;

import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.util.EasyPlaceUtils", remap = false)
public abstract class MixinLitematicaEasyPlaceUtils {

    @Inject(
        method = { "easyPlaceOnUseTick", "onRightClickTail" },
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void liquidbounce$suppressNativeEasyPlace(CallbackInfo callback) {
        if (LitematicaEasyPlaceHook.shouldSuppressNativeEasyPlace()) {
            callback.cancel();
        }
    }

    @Inject(
        method = "handleEasyPlaceWithMessage",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void liquidbounce$suppressDirectNativeEasyPlace(CallbackInfoReturnable<Boolean> callback) {
        if (LitematicaEasyPlaceHook.shouldSuppressNativeEasyPlace()) {
            callback.setReturnValue(true);
        }
    }
}
