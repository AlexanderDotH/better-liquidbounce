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

package net.ccbluex.liquidbounce.render.engine.esp;

import net.ccbluex.liquidbounce.common.EspMaskLayer;
import net.ccbluex.liquidbounce.common.EspMaskRequest;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleItemESP;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleOrbESP;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleStorageESP;
import net.ccbluex.liquidbounce.features.module.modules.render.esp.ModuleESP;
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspGlowMode;
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspOutlineMode;
import net.ccbluex.liquidbounce.utils.combat.CombatExtensionsKt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Converts the active ESP modes into independent shader-mask colors.
 * The first module requesting a particular effect owns that effect's color,
 * while Glow and Outline can coexist on the same rendered object.
 */
public final class EspMaskTargetSelector {

    private static final int PROTECTED_SURFACE_COLOR = 0xFFFFFFFF;

    private EspMaskTargetSelector() {
    }

    public static EspMaskRequest forEntity(@Nullable Entity entity) {
        if (entity == null) {
            return EspMaskRequest.NONE;
        }

        var request = EspMaskRequest.NONE;
        if (entity instanceof Player) {
            request = request.with(EspMaskLayer.PROTECTED_SURFACE, PROTECTED_SURFACE_COLOR);
        }

        var targetGlow = TargetGlowSourceRegistry.selectionFor(entity);
        if (targetGlow != null) {
            request = request.with(EspMaskLayer.TARGET_GLOW, targetGlow.color().argb());
        }

        if (entity instanceof LivingEntity livingEntity && CombatExtensionsKt.shouldBeShown(livingEntity)) {
            int color = ModuleESP.INSTANCE.getColor(livingEntity).argb();
            if (EspGlowMode.INSTANCE.getRunning() && EspGlowMode.INSTANCE.shouldRender(livingEntity)) {
                request = request.with(EspMaskLayer.PLAYER_GLOW, color);
            } else if (EspOutlineMode.INSTANCE.getRunning() && EspOutlineMode.INSTANCE.shouldRender(livingEntity)) {
                request = request.with(EspMaskLayer.PLAYER_OUTLINE, color);
            }
        }

        if (ModuleItemESP.ShaderEspMode.INSTANCE.getRunning() && ModuleItemESP.INSTANCE.shouldRender(entity)) {
            request = request
                .with(EspMaskLayer.PROTECTED_SURFACE, PROTECTED_SURFACE_COLOR)
                .with(EspMaskLayer.ITEM_GLOW, ModuleItemESP.INSTANCE.getColor().argb());
        }

        if (entity instanceof ExperienceOrb
            && ModuleOrbESP.GlowMode.INSTANCE.getRunning()
            && ModuleOrbESP.INSTANCE.shouldRender(entity)) {
            request = request
                .with(EspMaskLayer.PROTECTED_SURFACE, PROTECTED_SURFACE_COLOR)
                .with(EspMaskLayer.ORB_GLOW, ModuleOrbESP.INSTANCE.getColor().argb());
        }

        var category = ModuleStorageESP.categorize(entity);
        if (category == null || !category.shouldRender(entity) || category.getColor().isTransparent()) {
            return request;
        }

        request = request.with(EspMaskLayer.PROTECTED_SURFACE, PROTECTED_SURFACE_COLOR);

        if (ModuleStorageESP.GlowMode.INSTANCE.getRunning()) {
            request = request.with(EspMaskLayer.STORAGE_GLOW, category.getColor().argb());
        } else if (ModuleStorageESP.OutlineMode.INSTANCE.getRunning()) {
            request = request.with(EspMaskLayer.STORAGE_OUTLINE, category.getColor().argb());
        }

        return request;
    }

    public static EspMaskRequest forBlockEntity(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return EspMaskRequest.NONE;
        }

        var request = EspMaskRequest.NONE;
        var category = ModuleStorageESP.categorize(blockEntity);
        if (category == null
            || !category.shouldRender(blockEntity.getBlockPos())
            || category.getColor().isTransparent()) {
            return request;
        }

        request = request.with(EspMaskLayer.PROTECTED_SURFACE, PROTECTED_SURFACE_COLOR);

        if (ModuleStorageESP.GlowMode.INSTANCE.getRunning()) {
            return request.with(EspMaskLayer.STORAGE_GLOW, category.getColor().argb());
        }

        if (ModuleStorageESP.OutlineMode.INSTANCE.getRunning()) {
            return request.with(EspMaskLayer.STORAGE_OUTLINE, category.getColor().argb());
        }

        return request;
    }
}
