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

import net.ccbluex.liquidbounce.event.EventManager;
import net.ccbluex.liquidbounce.event.events.ChatSendEvent;
import net.ccbluex.liquidbounce.features.chat.ChatSubmission;
import net.ccbluex.liquidbounce.features.chat.ChatTabBounds;
import net.ccbluex.liquidbounce.features.chat.ChatTabLayout;
import net.ccbluex.liquidbounce.features.chat.ChatTabSpec;
import net.ccbluex.liquidbounce.features.chat.ChatTabTransition;
import net.ccbluex.liquidbounce.features.chat.ClientChatScreenBridge;
import net.ccbluex.liquidbounce.features.module.modules.misc.betterchat.ModuleBetterChat;
import net.ccbluex.liquidbounce.features.module.modules.misc.ModuleBetterTab;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.ArrayListDeque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

@Mixin(ChatScreen.class)
public abstract class MixinChatScreen extends MixinScreen {

    @Shadow
    protected EditBox input;

    @Shadow
    public abstract String normalizeChatMessage(String message);

    @Unique
    private static final int CHAT_BOTTOM_OFFSET = 40;

    @Unique
    private static final int CHAT_SIDE_GAP = 6;

    @Unique
    private ChatSubmission liquidbounce$lastSubmission;

    @Unique
    private boolean liquidbounce$suppressDraftSync;

    /**
     * Handle user chat messages
     *
     * @param chatText chat message by client user
     */
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void handleChatMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        var event = EventManager.INSTANCE.callEvent(new ChatSendEvent(chatText));
        var normalized = normalizeChatMessage(chatText);
        liquidbounce$lastSubmission = ClientChatScreenBridge.routeInput(normalized, event.isCancelled());

        switch (liquidbounce$lastSubmission) {
            case VANILLA -> {
                return;
            }
            case CLIENT_COMMAND -> minecraft.gui.hud.getChat().addRecentChat(chatText);
            case EXTERNAL_SENT -> {
                if (addToHistory) {
                    minecraft.gui.hud.getChat().addRecentChat(normalized);
                }
            }
            case EXTERNAL_FAILED -> {
                // Keep the current input and screen open in keyPressed.
            }
        }
        ci.cancel();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void initializeChatTabs(CallbackInfo ci) {
        liquidbounce$lastSubmission = null;
        liquidbounce$applyTransition(ClientChatScreenBridge.initialState(input.getValue()));
    }

    @Inject(method = "onEdited", at = @At("TAIL"))
    private void saveActiveDraft(String value, CallbackInfo ci) {
        if (liquidbounce$suppressDraftSync) {
            return;
        }
        ClientChatScreenBridge.saveDraft(value);
        if (!value.isEmpty()) {
            liquidbounce$lastSubmission = null;
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void saveActiveTab(CallbackInfo ci) {
        boolean clearDraft = liquidbounce$lastSubmission != null && !liquidbounce$lastSubmission.getKeepDraft();
        String vanillaDraft = ClientChatScreenBridge.finish(input.getValue(), liquidbounce$currentScroll(), clearDraft);
        if (vanillaDraft != null) {
            liquidbounce$setInput(vanillaDraft);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cycleChatTab(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        liquidbounce$lastSubmission = null;
        int direction = ClientChatScreenBridge.cycleDirection(
            event.key() == 258,
            event.hasControlDown(),
            event.hasShiftDown()
        );
        if (direction == 0) {
            return;
        }

        liquidbounce$applyTransition(ClientChatScreenBridge.cycle(
            direction,
            input.getValue(),
            liquidbounce$currentScroll()
        ));
        cir.setReturnValue(true);
    }

    @Inject(
        method = "keyPressed",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/ChatScreen;handleChatInput(Ljava/lang/String;Z)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void keepFailedExternalDraft(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (liquidbounce$lastSubmission == ChatSubmission.EXTERNAL_FAILED) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void renderChatTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        liquidbounce$renderTabRow(graphics, liquidbounce$networkTabBounds());
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void hookMouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == 0 && liquidbounce$handleTabClick(click.x(), click.y())) {
            cir.setReturnValue(true);
            return;
        }

        if (!(ModuleBetterChat.INSTANCE.getRunning() && ModuleBetterChat.Copy.INSTANCE.getRunning())) {
            return;
        }

        var activeMessage = getActiveMessage(click);

        if (activeMessage.isEmpty()) {
            return;
        }

        var chatHud = (MixinChatComponentAccessor) this.minecraft.gui.hud.getChat();

        var visibleMessages = chatHud.getTrimmedMessages();
        var messageBounds = ModuleBetterChat.resolveMessageBounds(visibleMessages, activeMessage.getAsInt());
        var messageParts = new ArrayListDeque<GuiMessage.Line>(messageBounds.getEndInclusive() - messageBounds.getStart() + 1);
        for (int index = messageBounds.getEndInclusive(); index >= messageBounds.getStart(); index--) {
            messageParts.addLast(visibleMessages.get(index));
        }

        if (messageParts.isEmpty())
            return;

        ModuleBetterChat.Copy.copyMessage(messageParts, click.button());
    }

    @Unique
    private boolean liquidbounce$handleTabClick(double mouseX, double mouseY) {
        String network = ChatTabLayout.hitTest(liquidbounce$networkTabBounds(), mouseX, mouseY);
        if (network == null) {
            return false;
        }
        var transition = ClientChatScreenBridge.switchTo(
            network,
            input.getValue(),
            liquidbounce$currentScroll()
        );
        if (transition == null) {
            return false;
        }
        liquidbounce$applyTransition(transition);
        return true;
    }

    @Unique
    private List<ChatTabBounds> liquidbounce$networkTabBounds() {
        var tabs = new ArrayList<ChatTabSpec>();
        for (var tab : ClientChatScreenBridge.visibleTabs(ModuleBetterTab.ClientPlayers.INSTANCE.getColor())) {
            tabs.add(new ChatTabSpec(
                tab.getId(),
                tab.getLabel(),
                font.width(tab.getLabel()),
                tab.getSelected(),
                tab.getColor(),
                tab.getStatus()
            ));
        }
        var chat = (MixinChatComponentAccessor) minecraft.gui.hud.getChat();
        double scale = Math.max(chat.invokeGetScale(), 0.01);
        int chatRight = (int) Math.ceil((Math.ceil(chat.invokeGetWidth() / scale) + 4.0) * scale);
        return ChatTabLayout.arrangeSide(
            tabs,
            width,
            chatRight + CHAT_SIDE_GAP,
            height - CHAT_BOTTOM_OFFSET
        );
    }

    @Unique
    private void liquidbounce$renderTabRow(GuiGraphicsExtractor graphics, List<ChatTabBounds> tabs) {
        for (var tab : tabs) {
            int background = tab.getSelected() ? 0xD0404040 : 0xA0000000;
            int textColor = tab.getSelected() ? tab.getColor() : (tab.getColor() & 0x00FFFFFF) | 0xCC000000;
            int statusColor = switch (tab.getStatus()) {
                case CONNECTED -> 0xFF45C46A;
                case CONNECTING -> 0xFFF0B84B;
                case DISCONNECTED -> 0xFF777777;
            };
            graphics.fill(tab.getLeft(), tab.getTop(), tab.getRight(), tab.getBottom(), background);
            if (tab.getSelected()) {
                graphics.fill(tab.getLeft(), tab.getBottom() - 1, tab.getRight(), tab.getBottom(), tab.getColor());
            }
            graphics.fill(tab.getRight() - 3, tab.getTop() + 2, tab.getRight() - 1, tab.getTop() + 4, statusColor);
            graphics.enableScissor(tab.getLeft(), tab.getTop(), tab.getRight(), tab.getBottom());
            graphics.text(font, tab.getLabel(), tab.getLeft() + 4, tab.getTop() + 2, textColor, false);
            graphics.disableScissor();
        }
    }

    @Unique
    private void liquidbounce$applyTransition(ChatTabTransition transition) {
        liquidbounce$setInput(transition.getDraft());
        var chat = minecraft.gui.hud.getChat();
        chat.rescaleChat();
        if (transition.getScrollPosition() > 0) {
            chat.scrollChat(transition.getScrollPosition());
        }
    }

    @Unique
    private void liquidbounce$setInput(String value) {
        liquidbounce$suppressDraftSync = true;
        try {
            input.setValue(value);
        } finally {
            liquidbounce$suppressDraftSync = false;
        }
    }

    @Unique
    private int liquidbounce$currentScroll() {
        return ((MixinChatComponentAccessor) minecraft.gui.hud.getChat()).getChatScrollbarPos();
    }

    @Unique
    private OptionalInt getActiveMessage(MouseButtonEvent click) {
        var chatHud = (MixinChatComponentAccessor) this.minecraft.gui.hud.getChat();
        var visibleMessages = chatHud.getTrimmedMessages();
        if (visibleMessages.isEmpty()) {
            return OptionalInt.empty();
        }

        double chatScale = chatHud.invokeGetScale();
        if (chatScale <= 0.0) {
            return OptionalInt.empty();
        }

        int chatWidth = (int) Math.ceil(chatHud.invokeGetWidth() / chatScale);
        double localMouseX = click.x() / chatScale - 4.0;
        if (localMouseX < 0.0 || localMouseX > chatWidth) {
            return OptionalInt.empty();
        }

        int lineHeight = chatHud.invokeGetLineHeight();
        if (lineHeight <= 0) {
            return OptionalInt.empty();
        }

        int guiHeight = this.minecraft.getWindow().getGuiScaledHeight();
        int chatBottom = (int) Math.floor((guiHeight - 40) / chatScale);
        double localMouseY = chatBottom - click.y() / chatScale;
        if (localMouseY < 0.0) {
            return OptionalInt.empty();
        }

        int lineIndex = (int) Math.floor(localMouseY / lineHeight);
        int visibleLineCount = Math.min(chatHud.invokeGetLinesPerPage(), visibleMessages.size() - chatHud.getChatScrollbarPos());
        if (lineIndex < 0 || lineIndex >= visibleLineCount) {
            return OptionalInt.empty();
        }

        int messageIndex = lineIndex + chatHud.getChatScrollbarPos();
        return messageIndex >= 0 && messageIndex < visibleMessages.size() ? OptionalInt.of(messageIndex) : OptionalInt.empty();
    }
}
