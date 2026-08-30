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

package net.ccbluex.liquidbounce.injection.mixins.minecraft.client;

import net.ccbluex.liquidbounce.interfaces.LevelEntityAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public abstract class MixinLevelEntityAccess implements LevelEntityAccess {

    @Override
    public LevelEntityGetter<Entity> liquid_bounce$getEntities() {
        return ((MixinLevelInvoker) (Object) this).invokeGetEntities();
    }
}
