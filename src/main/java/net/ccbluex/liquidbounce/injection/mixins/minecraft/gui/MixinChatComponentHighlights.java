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

import net.ccbluex.liquidbounce.features.module.modules.misc.betterchat.ChatNameHighlightHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponentHighlights {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final public List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("TAIL"))
    private void hookRenderHighlights(
        GuiGraphicsExtractor graphics,
        Font font,
        int tickCount,
        int globalMouseX,
        int globalMouseY,
        ChatComponent.DisplayMode displayMode,
        boolean changeCursorOnInsertions,
        CallbackInfo ci
    ) {
        var player = minecraft.player;
        boolean names = player != null && ChatNameHighlightHook.namesEnabled();
        boolean copy = displayMode.foreground && ChatNameHighlightHook.copyHighlightEnabled();
        if ((!names && !copy) || trimmedMessages.isEmpty()) return;

        var accessor = (MixinChatComponentAccessor) this;
        double scale = accessor.invokeGetScale();
        if (scale <= 0.0) return;
        int width = (int) Math.ceil(accessor.invokeGetWidth() / scale);
        int lineHeight = accessor.invokeGetLineHeight();
        if (lineHeight <= 0) return;
        int bottom = (int) Math.floor((minecraft.getWindow().getGuiScaledHeight() - 40) / scale);
        int visible = Math.min(accessor.invokeGetLinesPerPage(), trimmedMessages.size() - chatScrollbarPos);
        if (visible <= 0) return;

        if (names) {
            renderNameHighlights(graphics, tickCount, displayMode.foreground, player.getGameProfile().name(),
                scale, width, lineHeight, bottom, visible);
        }
        if (copy) renderCopyHighlight(graphics, globalMouseX, globalMouseY, scale, width, lineHeight, bottom, visible);
    }

    @Unique
    private void renderCopyHighlight(
        GuiGraphicsExtractor graphics, int mouseX, int mouseY, double scale,
        int width, int lineHeight, int bottom, int visible
    ) {
        double localX = mouseX / scale - 4.0;
        double localY = bottom - mouseY / scale;
        if (localX < 0.0 || localX > width || localY < 0.0) return;
        int lineIndex = (int) Math.floor(localY / lineHeight);
        if (lineIndex < 0 || lineIndex >= visible) return;
        int messageIndex = lineIndex + chatScrollbarPos;
        if (messageIndex < 0 || messageIndex >= trimmedMessages.size()) return;
        var bounds = ChatNameHighlightHook.resolveMessageBounds(trimmedMessages, messageIndex);
        int start = Math.max(bounds.getStart(), chatScrollbarPos);
        int end = Math.min(bounds.getEndInclusive(), chatScrollbarPos + visible - 1);
        if (start > end) return;
        int left = (int) Math.floor(4.0 * scale);
        int right = (int) Math.ceil((width + 4.0) * scale);
        int top = (int) Math.floor((bottom - (end - chatScrollbarPos + 1) * lineHeight) * scale);
        int lower = (int) Math.ceil((bottom - (start - chatScrollbarPos) * lineHeight) * scale);
        graphics.fill(left, top, right, lower, 0x4422AAFF);
    }

    @Unique
    private void renderNameHighlights(
        GuiGraphicsExtractor graphics, int tickCount, boolean foreground, String playerName,
        double scale, int width, int lineHeight, int bottom, int visible
    ) {
        int right = (int) Math.ceil((width + 12.0) * scale);
        GuiMessage previous = null;
        Integer color = null;
        for (int lineIndex = 0; lineIndex < visible; lineIndex++) {
            var line = trimmedMessages.get(lineIndex + chatScrollbarPos);
            var parent = line.parent();
            if (parent != previous) {
                float visibility = messageVisibility(tickCount, line, foreground);
                color = visibility > 1.0E-5F
                    ? ChatNameHighlightHook.colorFor(parent.content().getString(), playerName, visibility)
                    : null;
                previous = parent;
            }
            if (color == null || color >>> 24 == 0) continue;
            int top = (int) Math.floor((bottom - (lineIndex + 1) * lineHeight) * scale);
            int lower = (int) Math.ceil((bottom - lineIndex * lineHeight) * scale);
            graphics.fill(0, top, right, lower, color);
        }
    }

    @Unique
    private static float messageVisibility(int tickCount, GuiMessage.Line line, boolean foreground) {
        if (foreground) return 1.0F;
        double visibility = 1.0 - (tickCount - line.addedTime()) / 200.0;
        visibility = Mth.clamp(visibility * 10.0, 0.0, 1.0);
        return (float) (visibility * visibility);
    }
}
