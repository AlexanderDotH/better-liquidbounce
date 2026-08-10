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
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class MixinLevelBaseFinderHeadless {

    @Inject(method = "getLevelData", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessLevelData(CallbackInfoReturnable<LevelData> cir) {
        LevelData data = BaseFinderHeadlessServerLevelHook.levelData((Level) (Object) this);
        if (data != null) {
            cir.setReturnValue(data);
        }
    }

    @Inject(method = "dimensionType", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessDimensionType(CallbackInfoReturnable<DimensionType> cir) {
        DimensionType type = BaseFinderHeadlessServerLevelHook.dimensionType((Level) (Object) this);
        if (type != null) {
            cir.setReturnValue(type);
        }
    }

    @Inject(method = "dimensionTypeRegistration", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessDimensionTypeHolder(CallbackInfoReturnable<Holder<DimensionType>> cir) {
        Holder<DimensionType> holder = BaseFinderHeadlessServerLevelHook.dimensionTypeHolder((Level) (Object) this);
        if (holder != null) {
            cir.setReturnValue(holder);
        }
    }

    @Inject(method = "dimension", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessDimension(CallbackInfoReturnable<ResourceKey<Level>> cir) {
        ResourceKey<Level> dimension = BaseFinderHeadlessServerLevelHook.dimension((Level) (Object) this);
        if (dimension != null) {
            cir.setReturnValue(dimension);
        }
    }

    @Inject(method = "registryAccess", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessRegistryAccess(CallbackInfoReturnable<RegistryAccess> cir) {
        RegistryAccess access = BaseFinderHeadlessServerLevelHook.registryAccess((Level) (Object) this);
        if (access != null) {
            cir.setReturnValue(access);
        }
    }

    @Inject(method = "isClientSide", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessIsClientSide(CallbackInfoReturnable<Boolean> cir) {
        if (BaseFinderHeadlessServerLevelHook.isHeadlessLevel(this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getLightEngine", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessLightEngine(CallbackInfoReturnable<LevelLightEngine> cir) {
        if (BaseFinderHeadlessServerLevelHook.isHeadlessLevel(this)) {
            cir.setReturnValue(LevelLightEngine.EMPTY);
        }
    }

    @Inject(method = "getOverworldClockTime", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessClock(CallbackInfoReturnable<Long> cir) {
        if (BaseFinderHeadlessServerLevelHook.isHeadlessLevel(this)) {
            cir.setReturnValue(0L);
        }
    }
}
