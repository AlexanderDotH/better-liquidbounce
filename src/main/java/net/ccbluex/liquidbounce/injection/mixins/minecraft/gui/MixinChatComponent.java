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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.ccbluex.liquidbounce.features.module.modules.misc.betterchat.ModuleBetterChat;
import net.ccbluex.liquidbounce.interfaces.GuiMessageLineAddition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Mutable
    @Shadow
    @Final
    public List<GuiMessage> allMessages;

    @Mutable
    @Shadow
    @Final
    public List<GuiMessage.Line> trimmedMessages;

    @Shadow
    public abstract boolean isChatFocused();

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    private boolean newMessageSinceScroll;

    @Shadow
    public abstract void scrollChat(int scroll);

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;)V", at = @At(value = "TAIL"))
    public void hookNewArrayList2(Minecraft minecraft, CallbackInfo ci) {
        allMessages = new ArrayListDeque<>(100);
        // ArrayDeque for addFirst operations
        trimmedMessages = new ArrayListDeque<>(100);
    }

    /**
     * Spoofs the message size to be empty to avoid deletion.
     * <pre>
     * while(this.messages.size() > 100) {
     *     this.messages.removeLast();
     * }
     * </pre>
     */
    @ModifyExpressionValue(method = "addMessageToQueue", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 0, remap = false))
    public int hookGetSize2(int original) {
        var betterChat = ModuleBetterChat.INSTANCE;
        if (betterChat.getRunning() && betterChat.getInfiniteLength()) {
            return 0;
        }

        return original;
    }

    /**
     * Cancels the message clearing.
     */
    @Inject(method = "clearMessages", at = @At(value = "HEAD"), cancellable = true)
    public void hookClear(boolean clearHistory, CallbackInfo ci) {
        var betterChat = ModuleBetterChat.INSTANCE;
        if (betterChat.getRunning() && betterChat.getAntiClear() && !betterChat.getAntiChatClearPaused()) {
            ci.cancel();
        }
    }

    /**
     * Modifies {@link ChatComponent#addMessageToDisplayQueue(GuiMessage)} so, that the id is
     * forwarded and if {@link ModuleBetterChat} is enabled, older lines won't be removed.
     */
    @Inject(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;isChatFocused()Z", shift = At.Shift.BEFORE), cancellable = true)
    public void hookAddVisibleMessage(GuiMessage message, CallbackInfo ci, @Local(name = "lines") List<FormattedCharSequence> lines) {
        var focused = isChatFocused();
        var removable = ((GuiMessageLineAddition) (Object) message);
        //noinspection DataFlowIssue
        var id = removable.liquid_bounce$getId();

        for (int j = 0; j < lines.size(); ++j) {
            FormattedCharSequence orderedText = lines.get(j);
            if (focused && chatScrollbarPos > 0) {
                newMessageSinceScroll = true;
                scrollChat(1);
            }

            boolean last = j == lines.size() - 1;
            var visible = new GuiMessage.Line(message, orderedText, last);
            //noinspection DataFlowIssue
            ((GuiMessageLineAddition) (Object) visible).liquid_bounce$setId(id);
            trimmedMessages.addFirst(visible);
        }

        var betterChat = ModuleBetterChat.INSTANCE;
        if (!betterChat.getRunning() || !betterChat.getInfiniteLength()) {
            if (trimmedMessages.size() > 100) {
                trimmedMessages.subList(100, trimmedMessages.size()).clear();
            }
        }

        ci.cancel();
    }

}
