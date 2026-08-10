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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevelBaseFinderHeadless {

    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessGetSeed(CallbackInfoReturnable<Long> cir) {
        Long seed = BaseFinderHeadlessServerLevelHook.seed((ServerLevel) (Object) this);
        if (seed != null) {
            cir.setReturnValue(seed);
        }
    }

    @Inject(method = "getChunkSource", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessGetChunkSource(CallbackInfoReturnable<ServerChunkCache> cir) {
        ServerChunkCache cache = BaseFinderHeadlessServerLevelHook.chunkSource((ServerLevel) (Object) this);
        if (cache != null) {
            cir.setReturnValue(cache);
        }
    }

    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessNoiseBiome(int x, int y, int z, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> biome = BaseFinderHeadlessServerLevelHook.noiseBiome((ServerLevel) (Object) this, x, y, z);
        if (biome != null) {
            cir.setReturnValue(biome);
        }
    }

    @Inject(method = "updatePOIOnBlockStateChange", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessUpdatePoi(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (BaseFinderHeadlessServerLevelHook.isHeadlessLevel(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "getWorldBorder", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessWorldBorder(CallbackInfoReturnable<WorldBorder> cir) {
        WorldBorder border = BaseFinderHeadlessServerLevelHook.worldBorder((ServerLevel) (Object) this);
        if (border != null) {
            cir.setReturnValue(border);
        }
    }

    @Inject(method = "enabledFeatures", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessFeatures(CallbackInfoReturnable<FeatureFlagSet> cir) {
        FeatureFlagSet flags = BaseFinderHeadlessServerLevelHook.featureFlags((ServerLevel) (Object) this);
        if (flags != null) {
            cir.setReturnValue(flags);
        }
    }

    @Inject(method = "getMoonBrightness", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessMoon(BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (BaseFinderHeadlessServerLevelHook.isHeadlessLevel(this)) {
            cir.setReturnValue(0f);
        }
    }

    @Inject(method = "getSeaLevel", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessSeaLevel(CallbackInfoReturnable<Integer> cir) {
        Integer sea = BaseFinderHeadlessServerLevelHook.seaLevel((ServerLevel) (Object) this);
        if (sea != null) {
            cir.setReturnValue(sea);
        }
    }

    @Inject(method = "getServer", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessServer(CallbackInfoReturnable<MinecraftServer> cir) {
        if (BaseFinderHeadlessServerLevelHook.isHeadlessLevel(this)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "clockManager", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessClockManager(
            CallbackInfoReturnable<net.minecraft.world.clock.ClockManager> cir) {
        net.minecraft.world.clock.ClockManager clock =
                BaseFinderHeadlessServerLevelHook.clockManager((ServerLevel) (Object) this);
        if (clock != null) {
            cir.setReturnValue(clock);
        }
    }
}
