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

package net.ccbluex.liquidbounce.injection.mixins.minecraft.client;

import net.ccbluex.liquidbounce.render.playermodel.PlayerModelParticleHook;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("HEAD"), cancellable = true)
    private void hookAmnesiaTrackingEmitter(Entity entity, ParticleOptions particle, CallbackInfo ci) {
        if (!PlayerModelParticleHook.shouldRedirectEntityParticles(entity)) {
            return;
        }

        Vec3 offset = PlayerModelParticleHook.getEntityParticleOffset(entity);
        if (offset == null) {
            ci.cancel();
            return;
        }

        ci.cancel();
        ((ParticleEngine) (Object) this).createParticle(
            particle,
            entity.getX() + offset.x,
            entity.getY() + offset.y,
            entity.getZ() + offset.z,
            0.0,
            0.0,
            0.0
        );
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"), cancellable = true)
    private void hookAmnesiaTrackingEmitterCount(Entity entity, ParticleOptions particle, int count, CallbackInfo ci) {
        if (!PlayerModelParticleHook.shouldRedirectEntityParticles(entity)) {
            return;
        }

        Vec3 offset = PlayerModelParticleHook.getEntityParticleOffset(entity);
        if (offset == null) {
            ci.cancel();
            return;
        }

        ci.cancel();
        ParticleEngine engine = (ParticleEngine) (Object) this;
        for (int i = 0; i < count; i++) {
            engine.createParticle(
                particle,
                entity.getX() + offset.x,
                entity.getY() + offset.y,
                entity.getZ() + offset.z,
                0.0,
                0.0,
                0.0
            );
        }
    }
}
