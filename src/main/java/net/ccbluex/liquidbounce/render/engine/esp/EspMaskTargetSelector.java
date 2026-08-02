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

import net.ccbluex.liquidbounce.common.EspMaskRequest;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleStorageESP;
import net.ccbluex.liquidbounce.features.module.modules.render.esp.ModuleESP;
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspGlowMode;
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspOutlineMode;
import net.ccbluex.liquidbounce.utils.combat.CombatExtensionsKt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Converts the active ESP modes into independent shader-mask colors.
 * The first module requesting a particular effect owns that effect's color,
 * while Glow and Outline can coexist on the same rendered object.
 */
public final class EspMaskTargetSelector {

    private EspMaskTargetSelector() {
    }

    public static EspMaskRequest forEntity(@Nullable Entity entity) {
        if (entity == null) {
            return EspMaskRequest.NONE;
        }

        var request = EspMaskRequest.NONE;
        if (entity instanceof LivingEntity livingEntity && CombatExtensionsKt.shouldBeShown(livingEntity)) {
            int color = ModuleESP.INSTANCE.getColor(livingEntity).argb();
            if (EspGlowMode.INSTANCE.getRunning() && EspGlowMode.INSTANCE.shouldRender(livingEntity)) {
                request = request.withGlow(color);
            } else if (EspOutlineMode.INSTANCE.getRunning() && EspOutlineMode.INSTANCE.shouldRender(livingEntity)) {
                request = request.withOutline(color);
            }
        }

        var category = ModuleStorageESP.categorize(entity);
        if (category == null || !category.shouldRender(entity) || category.getColor().isTransparent()) {
            return request;
        }

        if (ModuleStorageESP.GlowMode.INSTANCE.getRunning()) {
            request = request.withGlow(category.getColor().argb());
        } else if (ModuleStorageESP.OutlineMode.INSTANCE.getRunning()) {
            request = request.withOutline(category.getColor().argb());
        }

        return request;
    }

    public static EspMaskRequest forBlockEntity(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return EspMaskRequest.NONE;
        }

        var category = ModuleStorageESP.categorize(blockEntity);
        if (category == null
            || !category.shouldRender(blockEntity.getBlockPos())
            || category.getColor().isTransparent()) {
            return EspMaskRequest.NONE;
        }

        if (ModuleStorageESP.GlowMode.INSTANCE.getRunning()) {
            return EspMaskRequest.NONE.withGlow(category.getColor().argb());
        }

        if (ModuleStorageESP.OutlineMode.INSTANCE.getRunning()) {
            return EspMaskRequest.NONE.withOutline(category.getColor().argb());
        }

        return EspMaskRequest.NONE;
    }
}
