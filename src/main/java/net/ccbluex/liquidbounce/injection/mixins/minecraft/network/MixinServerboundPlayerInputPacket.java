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

package net.ccbluex.liquidbounce.injection.mixins.minecraft.network;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.ccbluex.liquidbounce.additions.ServerboundPlayerInputPacketAddition;
import net.ccbluex.liquidbounce.additions.ServerboundPlayerInputPacketAdditionKt;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@NullMarked
@Mixin(ServerboundPlayerInputPacket.class)
public abstract class MixinServerboundPlayerInputPacket implements ServerboundPlayerInputPacketAddition {

    @Shadow
    @Final
    private Input input;

    @Unique
    private boolean liquidBounce$forceSneak = false;

    @Unique
    private boolean liquidBounce$suppressSneak = false;

    @Unique
    private boolean liquidBounce$suppressJump = false;

    @Unique
    private boolean liquidBounce$forceSprint = false;

    @Override
    public void setLiquidBounce$forceSneak(boolean b) {
        this.liquidBounce$forceSneak = b;
    }

    @Override
    public boolean getLiquidBounce$forceSneak() {
        return this.liquidBounce$forceSneak;
    }

    @Override
    public void setLiquidBounce$suppressSneak(boolean b) {
        this.liquidBounce$suppressSneak = b;
    }

    @Override
    public boolean getLiquidBounce$suppressSneak() {
        return this.liquidBounce$suppressSneak;
    }

    @Override
    public void setLiquidBounce$suppressJump(boolean b) {
        this.liquidBounce$suppressJump = b;
    }

    @Override
    public boolean getLiquidBounce$suppressJump() {
        return this.liquidBounce$suppressJump;
    }

    @Override
    public void setLiquidBounce$forceSprint(boolean b) {
        this.liquidBounce$forceSprint = b;
    }

    @Override
    public boolean getLiquidBounce$forceSprint() {
        return this.liquidBounce$forceSprint;
    }

    public Input liquidBounce$getRawInput() {
        return this.input;
    }

    @ModifyReturnValue(method = "input", at = @At("RETURN"))
    private Input applyForcedInput(Input original) {
        boolean jump = ServerboundPlayerInputPacketAdditionKt.resolveServerboundPlayerInputJump(
            original.jump(),
            this.liquidBounce$suppressJump
        );
        boolean shift = ServerboundPlayerInputPacketAdditionKt.resolveServerboundPlayerInputSneak(
            original.shift(),
            this.liquidBounce$suppressSneak,
            this.liquidBounce$forceSneak
        );
        boolean sprint = original.sprint() || this.liquidBounce$forceSprint;

        if (jump != original.jump() || shift != original.shift() || sprint != original.sprint()) {
            return new Input(
                original.forward(),
                original.backward(),
                original.left(),
                original.right(),
                jump,
                shift,
                sprint
            );
        } else {
            return original;
        }
    }

}
