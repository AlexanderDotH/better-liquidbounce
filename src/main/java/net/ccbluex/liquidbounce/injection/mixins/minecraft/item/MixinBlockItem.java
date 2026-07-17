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
package net.ccbluex.liquidbounce.injection.mixins.minecraft.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.ccbluex.liquidbounce.features.module.modules.exploit.phase.modes.PhaseIntaveBlock;
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.intave.FlyIntave;
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.intave.SpeedIntaveInBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @ModifyExpressionValue(method = "canPlace", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isUnobstructed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Z"))
    private boolean hookPhaseIntaveBlockPlacement(
            boolean original,
            BlockPlaceContext context,
            BlockState state
    ) {
        return original
                || PhaseIntaveBlock.canPlaceThroughPlayer(context, state)
                || FlyIntave.canPlaceThroughPlayer(context, state)
                || SpeedIntaveInBlock.canPlaceThroughPlayer(context, state);
    }

}
