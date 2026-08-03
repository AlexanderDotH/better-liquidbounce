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

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ccbluex.liquidbounce.common.EspMaskCaptureContext;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleChams;
import net.ccbluex.liquidbounce.interfaces.SubmitNodeCollectionAddition;
import net.ccbluex.liquidbounce.utils.render.NametagSubmitContext;
import net.ccbluex.liquidbounce.utils.render.PlayerModelNametagHook;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(SubmitNodeCollection.class)
public abstract class MixinSubmitNodeCollection implements SubmitNodeCollectionAddition {

    @Unique
    private SimpleFeatureRenderPhase liquid_bounce$espGlowPhase;

    @Unique
    private SimpleFeatureRenderPhase liquid_bounce$espOutlinePhase;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeEspPhases(CallbackInfo ci) {
        liquid_bounce$espGlowPhase = new SimpleFeatureRenderPhase();
        liquid_bounce$espOutlinePhase = new SimpleFeatureRenderPhase();
    }

    @ModifyReturnValue(method = "allPhases", at = @At("RETURN"))
    private List<FeatureRenderPhase<?>> includeEspPhases(List<FeatureRenderPhase<?>> original) {
        var phases = new ArrayList<>(original);
        phases.add(liquid_bounce$espGlowPhase);
        phases.add(liquid_bounce$espOutlinePhase);
        return List.copyOf(phases);
    }

    @Override
    public SimpleFeatureRenderPhase liquid_bounce$getEspGlowPhase() {
        return liquid_bounce$espGlowPhase;
    }

    @Override
    public SimpleFeatureRenderPhase liquid_bounce$getEspOutlinePhase() {
        return liquid_bounce$espOutlinePhase;
    }

    @Inject(method = "submitModel", at = @At("TAIL"))
    private <S> void captureEspModel(
        Model<? super S> model,
        S state,
        PoseStack poseStack,
        RenderType renderType,
        int lightCoords,
        int overlayCoords,
        int tintedColor,
        TextureAtlasSprite sprite,
        int outlineColor,
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
        CallbackInfo ci
    ) {
        var maskRenderType = liquid_bounce$getOutlineRenderType(renderType);
        if (maskRenderType == null) {
            return;
        }

        var request = EspMaskCaptureContext.current();
        if (request.glowColor() != 0) {
            liquid_bounce$espGlowPhase.submit(new ModelFeatureRenderer.Submit<>(
                maskRenderType, poseStack.last().copy(), model, state,
                15728880, OverlayTexture.NO_OVERLAY, request.glowColor(), sprite, null
            ));
        }
        if (request.outlineColor() != 0) {
            liquid_bounce$espOutlinePhase.submit(new ModelFeatureRenderer.Submit<>(
                maskRenderType, poseStack.last().copy(), model, state,
                15728880, OverlayTexture.NO_OVERLAY, request.outlineColor(), sprite, null
            ));
        }
    }

    @Inject(method = "submitBlockModel", at = @At("TAIL"))
    private void captureEspBlockModel(
        PoseStack poseStack,
        RenderType renderType,
        List<BlockStateModelPart> modelParts,
        int[] tintLayers,
        int lightCoords,
        int overlayCoords,
        int outlineColor,
        CallbackInfo ci
    ) {
        var maskRenderType = liquid_bounce$getOutlineRenderType(renderType);
        if (maskRenderType == null) {
            return;
        }

        var request = EspMaskCaptureContext.current();
        if (request.glowColor() != 0) {
            liquid_bounce$espGlowPhase.submit(new BlockModelFeatureRenderer.Submit(
                poseStack.last().copy(), maskRenderType, modelParts, BlockModelRenderState.EMPTY_TINTS,
                15728880, OverlayTexture.NO_OVERLAY, request.glowColor(), null
            ));
        }
        if (request.outlineColor() != 0) {
            liquid_bounce$espOutlinePhase.submit(new BlockModelFeatureRenderer.Submit(
                poseStack.last().copy(), maskRenderType, modelParts, BlockModelRenderState.EMPTY_TINTS,
                15728880, OverlayTexture.NO_OVERLAY, request.outlineColor(), null
            ));
        }
    }

    @Inject(method = "submitItem", at = @At("TAIL"))
    private void captureEspItem(
        PoseStack poseStack,
        ItemDisplayContext displayContext,
        int lightCoords,
        int overlayCoords,
        int outlineColor,
        int[] tintLayers,
        List<BakedQuad> quads,
        ItemStackRenderState.FoilType foilType,
        CallbackInfo ci
    ) {
        var request = EspMaskCaptureContext.current();
        if (request.glowColor() != 0) {
            liquid_bounce$espGlowPhase.submit(new ItemFeatureRenderer.Submit(
                poseStack.last().copy(), displayContext, 15728880, OverlayTexture.NO_OVERLAY,
                request.glowColor(), ItemStackRenderState.LayerRenderState.EMPTY_TINTS,
                quads, ItemStackRenderState.FoilType.NONE
            ));
        }
        if (request.outlineColor() != 0) {
            liquid_bounce$espOutlinePhase.submit(new ItemFeatureRenderer.Submit(
                poseStack.last().copy(), displayContext, 15728880, OverlayTexture.NO_OVERLAY,
                request.outlineColor(), ItemStackRenderState.LayerRenderState.EMPTY_TINTS,
                quads, ItemStackRenderState.FoilType.NONE
            ));
        }
    }

    @Unique
    private static RenderType liquid_bounce$getOutlineRenderType(RenderType renderType) {
        if (renderType.isOutline()) {
            return renderType;
        }

        return renderType.outline().orElse(null);
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

    @ModifyVariable(
        method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
        at = @At("HEAD"),
        argsOnly = true,
        name = "renderType"
    )
    private RenderType remapHeldItemModelRenderType(RenderType renderType) {
        return ModuleChams.INSTANCE.remapCurrentHeldItemRenderTypeIfNeeded(renderType);
    }

    @Inject(
        method = "submitItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer$Submit;hasTranslucency()Z"
        )
    )
    private void markHeldItemSubmit(
        CallbackInfo callbackInfo,
        @Local(name = "submit") ItemFeatureRenderer.Submit submit
    ) {
        ModuleChams.INSTANCE.markHeldItemSubmitIfActive(submit);
    }

}
