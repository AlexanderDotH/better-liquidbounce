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

import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinderBackgroundServer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * BaseFinder's background {@link net.minecraft.server.WorldLoader} calls
 * {@link TagLoader#loadTagsForExistingRegistries} on the shared STATIC / BuiltInRegistries layer.
 * Doing that while a live world is running races gameplay and can leave Holders that the play
 * connection cannot encode (seen with {@code minecraft:spear} damage_event).
 *
 * <p>Skip STATIC tag reload for that load path; WORLDGEN/DIMENSION registries are still created
 * fresh by {@link net.minecraft.resources.RegistryDataLoader}.
 */
@Mixin(TagLoader.class)
public abstract class MixinTagLoaderBaseFinderSilent {

    @Inject(method = "loadTagsForExistingRegistries", at = @At("HEAD"), cancellable = true)
    private static void liquidbounce$skipSharedTagReloadDuringBaseFinderStem(
        ResourceManager resources,
        RegistryAccess registryAccess,
        CallbackInfoReturnable<List<?>> cir
    ) {
        if (BaseFinderBackgroundServer.isSuppressingSharedRegistryTagReload()) {
            cir.setReturnValue(List.of());
        }
    }
}
