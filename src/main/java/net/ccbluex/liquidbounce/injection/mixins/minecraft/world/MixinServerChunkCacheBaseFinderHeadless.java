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
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hollow headless [ServerChunkCache] instances have a null {@code chunkMap}. Serve active shadow
 * [ProtoChunk]s (and no-op holder lookups) so Features regeneration does not NPE.
 */
@Mixin(ServerChunkCache.class)
public abstract class MixinServerChunkCacheBaseFinderHeadless {

    @Inject(method = "randomState", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessRandomState(CallbackInfoReturnable<RandomState> cir) {
        RandomState state = BaseFinderHeadlessServerLevelHook.randomState((ServerChunkCache) (Object) this);
        if (state != null) {
            cir.setReturnValue(state);
        }
    }

    @Inject(method = "getGenerator", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessGenerator(CallbackInfoReturnable<ChunkGenerator> cir) {
        ChunkGenerator generator = BaseFinderHeadlessServerLevelHook.generator((ServerChunkCache) (Object) this);
        if (generator != null) {
            cir.setReturnValue(generator);
        }
    }

    @Inject(method = "getVisibleChunkIfPresent", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessVisibleChunk(long pos, CallbackInfoReturnable<ChunkHolder> cir) {
        if (BaseFinderHeadlessServerLevelHook.isHeadlessChunkCache(this)) {
            // No ChunkMap / ChunkHolder graph — callers treat null as absent.
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "hasChunk", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessHasChunk(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
        ServerChunkCache self = (ServerChunkCache) (Object) this;
        if (BaseFinderHeadlessServerLevelHook.isHeadlessChunkCache(self)) {
            cir.setReturnValue(BaseFinderHeadlessServerLevelHook.hasShadowChunk(self, chunkX, chunkZ));
        }
    }

    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)" +
                    "Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void baseFinderHeadlessGetChunk(
            int chunkX,
            int chunkZ,
            ChunkStatus status,
            boolean load,
            CallbackInfoReturnable<ChunkAccess> cir
    ) {
        ServerChunkCache self = (ServerChunkCache) (Object) this;
        if (!BaseFinderHeadlessServerLevelHook.isHeadlessChunkCache(self)) {
            return;
        }
        ChunkAccess shadow = BaseFinderHeadlessServerLevelHook.shadowChunk(self, chunkX, chunkZ);
        if (shadow != null) {
            cir.setReturnValue(shadow);
            return;
        }
        if (!load) {
            cir.setReturnValue(null);
        } else {
            throw new IllegalStateException(
                    "Headless Features cache has no shadow chunk at " + chunkX + "," + chunkZ
            );
        }
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessGetChunkNow(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (BaseFinderHeadlessServerLevelHook.isHeadlessChunkCache(this)) {
            // Shadow neighborhood is ProtoChunk-only during generation.
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getChunkForLighting", at = @At("HEAD"), cancellable = true)
    private void baseFinderHeadlessGetChunkForLighting(
            int chunkX,
            int chunkZ,
            CallbackInfoReturnable<LightChunk> cir
    ) {
        ServerChunkCache self = (ServerChunkCache) (Object) this;
        if (!BaseFinderHeadlessServerLevelHook.isHeadlessChunkCache(self)) {
            return;
        }
        ChunkAccess shadow = BaseFinderHeadlessServerLevelHook.shadowChunk(self, chunkX, chunkZ);
        cir.setReturnValue(shadow instanceof LightChunk light ? light : null);
    }
}
