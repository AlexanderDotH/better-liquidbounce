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

package net.ccbluex.liquidbounce.interfaces;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;

/**
 * Stable lower-level boundary for the entity storage exposed by a Minecraft level.
 */
@FunctionalInterface
public interface LevelEntityAccess {

    LevelEntityGetter<Entity> liquid_bounce$getEntities();

    /**
     * Uses the bridge implemented on a level and intentionally preserves cast and provider failures.
     */
    static LevelEntityGetter<Entity> getEntities(Object level) {
        return ((LevelEntityAccess) level).liquid_bounce$getEntities();
    }
}
