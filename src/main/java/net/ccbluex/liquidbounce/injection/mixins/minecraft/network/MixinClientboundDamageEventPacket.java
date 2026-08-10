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
package net.ccbluex.liquidbounce.injection.mixins.minecraft.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ccbluex.liquidbounce.utils.network.DamageTypeNetworkRebind;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.damagesource.DamageType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Rebind damage-type holders to the connection registry before encode.
 *
 * <p>Cross-registry Holders (background WorldLoader / second MinecraftServer) otherwise throw
 * {@code Can't find id for … minecraft:spear} on {@code clientbound/minecraft:damage_event}.
 */
@Mixin(ClientboundDamageEventPacket.class)
public abstract class MixinClientboundDamageEventPacket {

    @WrapOperation(
        method = "write",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V",
            ordinal = 0
        )
    )
    private void liquidbounce$rebindDamageTypeHolder(
        StreamCodec<RegistryFriendlyByteBuf, Holder<DamageType>> codec,
        Object buf,
        Object holder,
        Operation<Void> original
    ) {
        RegistryFriendlyByteBuf registryBuf = (RegistryFriendlyByteBuf) buf;
        @SuppressWarnings("unchecked")
        Holder<DamageType> damageType = (Holder<DamageType>) holder;
        original.call(codec, buf, DamageTypeNetworkRebind.rebind(registryBuf.registryAccess(), damageType));
    }
}
