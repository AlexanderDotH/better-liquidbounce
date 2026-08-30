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
package net.ccbluex.liquidbounce.injection.mixins.minecraft.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinderServerHook;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Keep Fabric lifecycle / tick callbacks off BaseFinder's unpublished background server.
 *
 * <p>{@code Map.put} in {@link MinecraftServer#createLevels()} fires {@code ServerLevelEvents.LOAD}
 * (Distant Horizons must not attach — it tears down the shared DH IO pool on stop).
 *
 * <p>{@link MinecraftServer#tickServer} fires Fabric {@code ServerTickEvents}; Spark's shared TPS
 * window then {@code ArrayIndexOutOfBoundsException}s on this second server.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServerBaseFinderSilent {

    @WrapOperation(
        method = "createLevels",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        )
    )
    private <K, V> V liquidbounce$silentCreateLevelsPut(
        Map<K, V> levels,
        K key,
        V value,
        Operation<V> original
    ) {
        if (BaseFinderServerHook.isSilentServer(this)) {
            return levels.put(key, value);
        }
        return original.call(levels, key, value);
    }

    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void liquidbounce$skipSilentServerTick(BooleanSupplier haveTime, CallbackInfo ci) {
        if (BaseFinderServerHook.isSilentServer(this)) {
            ci.cancel();
        }
    }
}
