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

import net.ccbluex.liquidbounce.common.EspMaskLayer;
import net.ccbluex.liquidbounce.interfaces.PreparedFrameAddition;
import net.ccbluex.liquidbounce.interfaces.SubmitNodeCollectionAddition;
import net.ccbluex.liquidbounce.render.engine.esp.EspPreparedPhaseLookup;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class MixinFeatureRenderDispatcherPreparedFrame implements PreparedFrameAddition {

    @Shadow
    private FeatureFrameContext context;

    @Shadow
    private SubmitNodeStorage submitNodeStorage;

    @Shadow
    @Final
    private Map<FeatureRenderPhase<?>, List<?>> groupsByPhase;

    @Shadow
    private void executePhase(FeatureRenderPhase<?> phase, FeatureFrameContext context) {
        throw new AssertionError();
    }

    @Unique
    @Override
    public boolean liquid_bounce$hasEspMask(EspMaskLayer layer) {
        var storage = Objects.requireNonNull(submitNodeStorage, "Prepared frame has no submit storage");
        for (var collection : storage.getSubmitsPerOrder().values()) {
            var addition = (SubmitNodeCollectionAddition) collection;
            var phase = addition.liquid_bounce$getEspPhase(layer);
            if (EspPreparedPhaseLookup.hasPreparedGroups(groupsByPhase, phase)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    @Override
    public void liquid_bounce$executeEspMask(EspMaskLayer layer) {
        var frameContext = Objects.requireNonNull(context, "Prepared frame has no context");
        var storage = Objects.requireNonNull(submitNodeStorage, "Prepared frame has no submit storage");
        for (var collection : storage.getSubmitsPerOrder().values()) {
            var addition = (SubmitNodeCollectionAddition) collection;
            executePhase(addition.liquid_bounce$getEspPhase(layer), frameContext);
        }
    }
}
