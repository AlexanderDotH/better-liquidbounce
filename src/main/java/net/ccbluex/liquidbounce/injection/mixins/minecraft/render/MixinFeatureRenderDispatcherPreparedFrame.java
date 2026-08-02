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

package net.ccbluex.liquidbounce.injection.mixins.minecraft.render;

import net.ccbluex.liquidbounce.interfaces.PreparedFrameAddition;
import net.ccbluex.liquidbounce.interfaces.SubmitNodeCollectionAddition;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;

@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class MixinFeatureRenderDispatcherPreparedFrame implements PreparedFrameAddition {

    @Shadow
    private FeatureFrameContext context;

    @Shadow
    private SubmitNodeStorage submitNodeStorage;

    @Shadow
    private void executePhase(FeatureRenderPhase<?> phase, FeatureFrameContext context) {
        throw new AssertionError();
    }

    @Unique
    @Override
    public boolean liquid_bounce$hasEspGlow() {
        var storage = Objects.requireNonNull(submitNodeStorage, "Prepared frame has no submit storage");
        for (var collection : storage.getSubmitsPerOrder().values()) {
            var addition = (SubmitNodeCollectionAddition) collection;
            if (!addition.liquid_bounce$getEspGlowPhase().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Unique
    @Override
    public boolean liquid_bounce$hasEspOutline() {
        var storage = Objects.requireNonNull(submitNodeStorage, "Prepared frame has no submit storage");
        for (var collection : storage.getSubmitsPerOrder().values()) {
            var addition = (SubmitNodeCollectionAddition) collection;
            if (!addition.liquid_bounce$getEspOutlinePhase().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Unique
    @Override
    public void liquid_bounce$executeEspGlow() {
        liquid_bounce$executeEspPhase(true);
    }

    @Unique
    @Override
    public void liquid_bounce$executeEspOutline() {
        liquid_bounce$executeEspPhase(false);
    }

    @Unique
    private void liquid_bounce$executeEspPhase(boolean glow) {
        var frameContext = Objects.requireNonNull(context, "Prepared frame has no context");
        var storage = Objects.requireNonNull(submitNodeStorage, "Prepared frame has no submit storage");
        for (var collection : storage.getSubmitsPerOrder().values()) {
            var addition = (SubmitNodeCollectionAddition) collection;
            executePhase(
                glow ? addition.liquid_bounce$getEspGlowPhase() : addition.liquid_bounce$getEspOutlinePhase(),
                frameContext
            );
        }
    }
}
