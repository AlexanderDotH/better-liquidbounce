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
package net.ccbluex.liquidbounce.injection.mixins.minecraft.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ccbluex.liquidbounce.common.StorageEspOutlineContext;
import net.ccbluex.liquidbounce.utils.render.NametagSubmitContext;
import net.ccbluex.liquidbounce.utils.render.PlayerModelNametagHook;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public abstract class MixinSubmitNodeCollection {

    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, name = "outlineColor")
    private int injectStorageEspGlowOutlineColor(int outlineColor) {
        int storageEspOutlineColor = StorageEspOutlineContext.getOutlineColor();
        return outlineColor == 0 && storageEspOutlineColor != 0 ? storageEspOutlineColor : outlineColor;
    }

    @Inject(
        method = "submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cancelAmnesiaVanillaNameTag(
        PoseStack poseStack,
        Vec3 attachment,
        int yOffset,
        Component text,
        boolean seeThrough,
        int lightCoords,
        CameraRenderState cameraRenderState,
        CallbackInfo ci
    ) {
        var state = NametagSubmitContext.get();
        if (state != null && PlayerModelNametagHook.shouldSuppressVanillaNameDisplay(state)) {
            ci.cancel();
        }
    }

}
