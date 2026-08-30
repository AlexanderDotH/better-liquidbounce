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
package net.ccbluex.liquidbounce.injection.mixins.minecraft.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.ccbluex.liquidbounce.additions.GuiGraphicsExtractorAddition;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleBetterInventory;
import net.ccbluex.liquidbounce.utils.math.BoundingBox2f;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2ic;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinGuiGraphicsExtractor implements GuiGraphicsExtractorAddition {

    @Unique
    private static final float LIQUIDBOUNCE_TOOLTIP_BACKGROUND_MARGIN = 12F;

    @Unique
    private @Nullable List<ItemStack> liquidbounce$containerItemViewStacks;

    @Unique
    private float liquidbounce$containerItemViewCenterX;

    @Unique
    private float liquidbounce$containerItemViewCenterY;

    @Unique
    private float liquidbounce$containerItemViewScale;

    @Shadow
    protected abstract void itemBar(ItemStack stack, int x, int y);

    @Shadow
    protected abstract void itemCount(Font textRenderer, ItemStack stack, int x, int y,
        @Nullable String stackCountText);

    @Shadow
    protected abstract void itemCooldown(ItemStack stack, int x, int y);

    @Inject(method = "itemCooldown", at = @At("TAIL"))
    private void drawCooldownProgress(ItemStack stack, int x, int y, CallbackInfo ci) {
        ModuleBetterInventory.INSTANCE.drawTextCooldownProgress((GuiGraphicsExtractor) (Object) this, stack, x, y);
    }

    @WrapOperation(
        method = "tooltip",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"
        )
    )
    private Vector2ic drawContainerItemViewOutsideTooltip(
            ClientTooltipPositioner positioner, int screenWidth, int screenHeight, int mouseX, int mouseY,
            int tooltipWidth, int tooltipHeight, Operation<Vector2ic> original) {
        var position = original.call(positioner, screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);
        var stacks = this.liquidbounce$containerItemViewStacks;
        this.liquidbounce$containerItemViewStacks = null;

        if (stacks == null) {
            return position;
        }

        // TooltipRenderUtil expands the content rectangle by 3 px padding and a 9 px margin per side.
        var tooltipBounds = new BoundingBox2f(
            position.x() - LIQUIDBOUNCE_TOOLTIP_BACKGROUND_MARGIN,
            position.y() - LIQUIDBOUNCE_TOOLTIP_BACKGROUND_MARGIN,
            position.x() + tooltipWidth + LIQUIDBOUNCE_TOOLTIP_BACKGROUND_MARGIN,
            position.y() + tooltipHeight + LIQUIDBOUNCE_TOOLTIP_BACKGROUND_MARGIN
        );
        ModuleBetterInventory.INSTANCE.drawContainerItemView(
            (GuiGraphicsExtractor) (Object) this,
            stacks,
            this.liquidbounce$containerItemViewCenterX,
            this.liquidbounce$containerItemViewCenterY,
            this.liquidbounce$containerItemViewScale,
            tooltipBounds
        );
        return position;
    }

    @Override
    public void liquidbounce$drawItemBar(ItemStack stack, int x, int y) {
        itemBar(stack, x, y);
    }

    @Override
    public void liquidbounce$drawStackCount(Font textRenderer, ItemStack stack, int x, int y,
            @Nullable String stackCountText) {
        itemCount(textRenderer, stack, x, y, stackCountText);
    }

    @Override
    public void liquidbounce$drawCooldownProgress(ItemStack stack, int x, int y) {
        itemCooldown(stack, x, y);
    }

    @Override
    public void liquidbounce$queueContainerItemView(
            List<ItemStack> stacks, float centerX, float centerY, float scale) {
        this.liquidbounce$containerItemViewStacks = List.copyOf(stacks);
        this.liquidbounce$containerItemViewCenterX = centerX;
        this.liquidbounce$containerItemViewCenterY = centerY;
        this.liquidbounce$containerItemViewScale = scale;
    }
}
