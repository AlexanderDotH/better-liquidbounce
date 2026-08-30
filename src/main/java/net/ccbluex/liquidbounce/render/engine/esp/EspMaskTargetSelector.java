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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/** Stable injection facade for the feature-owned ESP mask selector. */
public final class EspMaskTargetSelector {

    private EspMaskTargetSelector() {
    }

    public static EspMaskRequest forEntity(@Nullable Entity entity) {
        return EspMaskFeatureSelectorRegistry.forEntity(entity);
    }

    public static EspMaskRequest forBlockEntity(@Nullable BlockEntity blockEntity) {
        return EspMaskFeatureSelectorRegistry.forBlockEntity(blockEntity);
    }
}
