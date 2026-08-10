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
package net.ccbluex.liquidbounce.injection.mixins.minecraft.world;

import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinderHeadlessServerLevelHook;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class MixinWorldGenRegionBaseFinderHeadless {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "isOldChunkAround", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessIsOldChunkAround(ChunkPos pos, int distance, CallbackInfoReturnable<Boolean> cir) {
        if (BaseFinderHeadlessServerLevelHook.isHeadlessLevel(this.level)) {
            cir.setReturnValue(false);
        }
    }
}
