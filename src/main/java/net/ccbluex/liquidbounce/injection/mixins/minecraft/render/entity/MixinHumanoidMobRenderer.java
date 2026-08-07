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

package net.ccbluex.liquidbounce.injection.mixins.minecraft.render.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill;
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleFastUse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@NullMarked
@Mixin(HumanoidMobRenderer.class)
public abstract class MixinHumanoidMobRenderer {

    @ModifyExpressionValue(
        method = "extractHumanoidRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getTicksUsingItem(F)F")
    )
    private static float hookFastUseSpearAnimation(float ticksUsingItem, @Local(argsOnly = true, name = "entity") LivingEntity entity) {
        if (entity != Minecraft.getInstance().player) {
            return ticksUsingItem;
        }

        if (ModuleSpearKill.getControlsSpearAnimation()) {
            return ModuleSpearKill.getSpearAnimationTicks(entity, ticksUsingItem);
        }
        return ModuleFastUse.getSpearAnimationTicks(entity, ticksUsingItem);
    }

}
