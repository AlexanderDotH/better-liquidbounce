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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ccbluex.liquidbounce.event.EventManager;
import net.ccbluex.liquidbounce.event.events.GameRenderEvent;
import net.ccbluex.liquidbounce.event.events.PerspectiveEvent;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleAntiBlind;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleItemChams;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleNoHurtCam;
import net.ccbluex.liquidbounce.features.module.modules.render.customambience.ModuleCustomAmbience;
import net.ccbluex.liquidbounce.injection.hooks.GameRendererBobbingHook;
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment;
import net.ccbluex.liquidbounce.render.engine.CustomFogBlurRenderer;
import net.ccbluex.liquidbounce.render.engine.CustomFogVolumeRenderer;
import net.ccbluex.liquidbounce.render.engine.UnifiedFogRenderer;
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderRenderer;
import net.ccbluex.liquidbounce.render.engine.gui.GuiGlowRenderer;
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent;
import net.ccbluex.liquidbounce.utils.aiming.RotationManager;
import net.ccbluex.liquidbounce.utils.collection.Pools;
import net.ccbluex.liquidbounce.render.WorldToScreen;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Camera mainCamera;

    @Shadow
    public abstract void tick();

    @Shadow
    @Final
    private Lightmap lightmap;

    @Shadow
    @Final
    private RenderTarget mainRenderTarget;

    /**
     * Hook game render event
     */
    @Inject(method = "render", at = @At("HEAD"))
    public void hookGameRender(CallbackInfo callbackInfo) {
        EspShaderRenderer.beginFrame();
        UnifiedFogRenderer.beginFrame();
        EventManager.INSTANCE.callEvent(GameRenderEvent.INSTANCE);
    }

    /**
     * GUI elements are collected during extraction, before {@link #hookGameRender(CallbackInfo)} runs.
     * Reset the GUI glow queue here so extracted masks survive until GuiRenderer draws them.
     */
    @Inject(method = "extract", at = @At("HEAD"))
    private void beginGuiGlowFrame(CallbackInfo callbackInfo) {
        GuiGlowRenderer.beginFrame();
    }

    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            shift = At.Shift.AFTER
        )
    )
    private void renderCustomFogPostProcessing(
        CallbackInfo ci,
        @Local(name = "cameraState") CameraRenderState cameraState,
        @Local(name = "projectionMatrix") Matrix4f projectionMatrix
    ) {
        if (ModuleCustomAmbience.FogValueGroup.INSTANCE.isUnified()) {
            UnifiedFogRenderer.render(cameraState, projectionMatrix);
            return;
        }

        CustomFogBlurRenderer.render(this.mainRenderTarget, cameraState, projectionMatrix);
        CustomFogVolumeRenderer.render(this.mainRenderTarget, cameraState, projectionMatrix);
    }

    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V"
        )
    )
    private void compositeEspShaders(CallbackInfo ci) {
        EspShaderRenderer.composite(mainRenderTarget);
    }

    /**
     * Apply change-look rotations before vanilla updates and extracts the camera state.
     */
    @Inject(method = "update", at = @At("HEAD"))
    private void applyChangeLookRotation(DeltaTracker deltaTracker, CallbackInfo ci) {
        RotationManager.INSTANCE.applyChangeLookRotation(deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "extractCamera", at = @At("TAIL"))
    private void hookWorldToScreenMatricesInExtract(
        DeltaTracker deltaTracker,
        float worldPartialTicks,
        float cameraEntityPartialTicks,
        CallbackInfo ci,
        @Local(name = "cameraState") CameraRenderState cameraState
    ) {
        WorldToScreen.setMatrices(cameraState.projectionMatrix, cameraState.viewRotationMatrix, cameraState.pos);
    }

    /**
     * Hook world render event
     */
    @Inject(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;isSleeping:Z", opcode = Opcodes.GETFIELD))
    public void hookWorldRender(
        DeltaTracker deltaTracker,
        CallbackInfo ci,
        @Local(name = "projectionMatrix") Matrix4f projectionMatrix,
        @Local(name = "modelViewMatrix") Matrix4fc modelViewMatrix
    ) {
        // Pose stack stays identity for camera-relative vertex building. View rotation is applied
        // once via WorldRenderEvent.modelViewMatrix → shader ModelViewMat (same contract as BlockESP).
        var newMatStack = Pools.MatStack.borrow();
        try {
            try (var event = new WorldRenderEvent(
                newMatStack,
                modelViewMatrix,
                this.mainCamera,
                deltaTracker.getGameTimeDeltaPartialTick(false),
                this.mainRenderTarget
            )) {
                EventManager.INSTANCE.callEvent(event);
            }
        } finally {
            Pools.MatStack.recycle(newMatStack);
        }
    }

    @ModifyArg(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;getBuffer(Lnet/minecraft/client/renderer/fog/FogRenderer$FogMode;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;",
            ordinal = 0
        )
    )
    private FogRenderer.FogMode customFogMode(FogRenderer.FogMode fogMode) {
        if (!ModuleCustomAmbience.FogValueGroup.INSTANCE.getRunning()) {
            return fogMode;
        }
        if (ModuleCustomAmbience.FogValueGroup.INSTANCE.isUnified()) {
            if (UnifiedFogRenderer.shouldReplaceNativeFog()) {
                return FogRenderer.FogMode.NONE;
            }
            return FogRenderer.FogMode.WORLD;
        }
        if (ModuleCustomAmbience.FogValueGroup.VolumetricFog.INSTANCE.getRunning()) {
            return FogRenderer.FogMode.NONE;
        }
        return FogRenderer.FogMode.WORLD;
    }

    @WrapOperation(
        method = "renderItemInHand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"
        )
    )
    private void drawItemCharmsOnHandFeatureExecution(
        net.minecraft.client.renderer.feature.FeatureRenderDispatcher instance,
        net.minecraft.client.renderer.SubmitNodeStorage submitNodeStorage,
        Operation<Void> original
    ) {
        ModuleItemChams.Lightmap.INSTANCE.applyToTexture(this.lightmap.getTextureView());
        try {
            original.call(instance, submitNodeStorage);
        } finally {
            ModuleItemChams.Lightmap.INSTANCE.resetTexture(this.lightmap.getTextureView());
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void injectHurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (ModuleNoHurtCam.INSTANCE.getRunning()) {
            ci.cancel();
        }
    }

    /**
     * Keeps the vanilla 26.1 walk interpolation inputs while applying the custom bobbing strength.
     *
     * @see net.minecraft.client.renderer.GameRenderer#bobView(net.minecraft.client.renderer.state.level.CameraRenderState, com.mojang.blaze3d.vertex.PoseStack)
     * @see net.minecraft.client.Camera#extractRenderState(net.minecraft.client.renderer.state.level.CameraRenderState, float)
     * @see net.minecraft.client.renderer.state.level.CameraEntityRenderState#backwardsInterpolatedWalkDistance
     * @see net.minecraft.client.renderer.state.level.CameraEntityRenderState#bob
     */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void injectBobView(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (GameRendererBobbingHook.apply(cameraState, poseStack)) {
            ci.cancel();
        }
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void hookShowFloatingItem(ItemStack floatingItem, CallbackInfo ci) {
        if (!ModuleAntiBlind.canRenderFloatingItems()) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 0, remap = false))
    private float hookAntiNausea(float original) {
        if (!ModuleAntiBlind.canRenderNausea()) {
            return 0f;
        }

        return original;
    }

    @ModifyExpressionValue(method = "extractOptions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Options;getCameraType()Lnet/minecraft/client/CameraType;"
            )
    )
    private CameraType hookPerspectiveEventOnCamera(CameraType original) {
        return PerspectiveEvent.INSTANCE.getPerspective();
    }

}
