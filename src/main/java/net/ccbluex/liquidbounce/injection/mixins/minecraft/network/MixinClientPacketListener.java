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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.llamalad7.mixinextras.sugar.Local;
import net.ccbluex.liquidbounce.common.ChunkUpdateFlag;
import net.ccbluex.liquidbounce.event.EventManager;
import net.ccbluex.liquidbounce.event.events.*;
import net.ccbluex.liquidbounce.integration.gameplay.PacketListenerAntiExploitHook;
import net.ccbluex.liquidbounce.integration.gameplay.PacketListenerChatHook;
import net.ccbluex.liquidbounce.integration.gameplay.PacketListenerSessionHook;
import net.ccbluex.liquidbounce.integration.gameplay.PacketListenerTriggerHook;
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Optional;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener extends ClientCommonPacketListenerImpl {

    protected MixinClientPacketListener(Minecraft client, Connection connection, CommonListenerCookie connectionState) {
        super(client, connection, connectionState);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void resetPlayerModelStateForNewSession(CallbackInfo ci) {
        PacketListenerSessionHook.resetPlayerModelState();
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void resetPlayerModelStateOnRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        PacketListenerSessionHook.resetPlayerModelState();
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void injectChunkLoadEvent(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        EventManager.INSTANCE.callEvent(new ChunkLoadEvent(packet.getX(), packet.getZ()));
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("RETURN"))
    private void injectUnloadEvent(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        EventManager.INSTANCE.callEvent(new ChunkUnloadEvent(packet.pos()));
    }

    @WrapMethod(method = "handleChunkBlocksUpdate")
    private void onChunkDeltaUpdateStart(ClientboundSectionBlocksUpdatePacket packet, Operation<Void> original) {
        ChunkUpdateFlag.withChunkDeltaUpdating(() -> {
            original.call(packet);
            EventManager.INSTANCE.callEvent(new ChunkDeltaUpdateEvent(packet));
        });
    }

    @Inject(method = "handleTeleportEntity", at = @At("RETURN"))
    private void hookOnEntityPosition(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        PacketListenerTriggerHook.entityMoved(packet);
    }

    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void hookOnBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        PacketListenerTriggerHook.blockUpdated(packet);
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void hookOnChunkDeltaUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        PacketListenerTriggerHook.chunkBlocksUpdated(packet);
    }

    @Inject(method = "handleAddEntity", at = @At("RETURN"))
    private void hookOnEntitySpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        PacketListenerTriggerHook.entityAdded(packet);
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("RETURN"))
    private void hookOnPlaySoundFromEntity(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        PacketListenerTriggerHook.entitySoundPlayed(packet);
    }

    @Inject(method = "handleRemoveEntities", at = @At("RETURN"))
    private void hookOnEntitiesDestroy(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        PacketListenerTriggerHook.entitiesRemoved(packet);
    }

    @ModifyExpressionValue(method = "setTitleText", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundSetTitleTextPacket;text()Lnet/minecraft/network/chat/Component;"))
    private @Nullable Component hookOnTitle(@Nullable Component original, @Cancellable CallbackInfo ci) {
        var event = new TitleEvent.Title(original);
        EventManager.INSTANCE.callEvent(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
        return event.getText();
    }

    @ModifyExpressionValue(method = "setSubtitleText", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundSetSubtitleTextPacket;text()Lnet/minecraft/network/chat/Component;"))
    private @Nullable Component hookOnSubtitle(@Nullable Component original, @Cancellable CallbackInfo ci) {
        var event = new TitleEvent.Subtitle(original);
        EventManager.INSTANCE.callEvent(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
        return event.getText();
    }

    @ModifyArgs(method = "setTitlesAnimation", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;setTimes(III)V"))
    private void hookOnTitleFade(Args args, @Cancellable CallbackInfo ci) {
        var event = new TitleEvent.Fade(args.get(0), args.get(1), args.get(2));
        EventManager.INSTANCE.callEvent(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
        args.set(0, event.getFadeInTicks());
        args.set(1, event.getStayTicks());
        args.set(2, event.getFadeOutTicks());
    }

    /**
     * This injection rewrites the method!!!
     */
    @Inject(method = "handleTitlesClear", at = @At(value = "HEAD"), cancellable = true)
    private void hookOnTitleClear(ClientboundClearTitlesPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread(packet, (ClientGamePacketListener) this, this.minecraft.packetProcessor());
        var event = new TitleEvent.Clear(packet.shouldResetTimes());
        EventManager.INSTANCE.callEvent(event);
        if (event.isCancelled()) {
            ci.cancel();
            return;
        }
        this.minecraft.gui.hud.clearTitles();
        if (event.getReset()) {
            this.minecraft.gui.hud.resetTitleTimes();
        }
        ci.cancel();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @ModifyExpressionValue(method = "handleExplosion", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;playerKnockback()Ljava/util/Optional;"))
    private Optional<Vec3> onExplosionVelocity(Optional<Vec3> original) {
        return PacketListenerAntiExploitHook.limitExplosion(original);
    }

    @ModifyExpressionValue(method = "handleParticleEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;getCount()I", ordinal = 1))
    private int onParticleAmount(int original) {
        return PacketListenerAntiExploitHook.limitParticleAmount(original);
    }

    @ModifyExpressionValue(method = "handleParticleEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;getMaxSpeed()F"))
    private float onParticleSpeed(float original) {
        return PacketListenerAntiExploitHook.limitParticleSpeed(original);
    }

    @ModifyExpressionValue(method = "handleGameEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundGameEventPacket;getEvent()Lnet/minecraft/network/protocol/game/ClientboundGameEventPacket$Type;"))
    private ClientboundGameEventPacket.Type onGameStateChange(ClientboundGameEventPacket.Type original) {
        return PacketListenerAntiExploitHook.filterGameEvent(original);
    }

    @Inject(method = "handleSetHealth", at = @At("HEAD"))
    private void injectHealthUpdate(ClientboundSetHealthPacket packet, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;

        if (player == null) {
            return;
        }

        EventManager.INSTANCE.callEvent(new HealthUpdateEvent(packet.getHealth(), packet.getFood(), packet.getSaturation(), player.getHealth()));

        if (packet.getHealth() == 0) {
            EventManager.INSTANCE.callEvent(DeathEvent.INSTANCE);
        }
    }

    @Inject(method = "handlePlayerAbilities", at = @At("RETURN"))
    private void suppressServerGrantedFlight(ClientboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;

        if (player != null) {
            PacketListenerSessionHook.serverAbilitiesApplied(packet, player);
        }
    }

    @Unique
    private final ThreadLocal<Rotation> rotationThreadLocal = ThreadLocal.withInitial(() -> null);

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"))
    private void injectPlayerPositionLook(
        ClientboundPlayerPositionPacket packet, CallbackInfo ci, @Local(name = "player") Player player) {
        PacketListenerSessionHook.beforeCorrection(packet, player);
        rotationThreadLocal.set(new Rotation(player.getYRot(), player.getXRot(), true));
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void injectNoRotateSet(ClientboundPlayerPositionPacket packet, CallbackInfo ci, @Local(name = "player") Player player) {
        PacketListenerSessionHook.afterCorrection(packet, player);

        if (!PacketListenerSessionHook.shouldRestoreRotation()) {
            return;
        }

        var prevRotation = this.rotationThreadLocal.get();
        if (prevRotation == null) {
            return;
        }
        this.rotationThreadLocal.remove();
        PacketListenerSessionHook.restoreRotation(player, prevRotation);
    }

    @ModifyVariable(method = "sendChat", at = @At("HEAD"), argsOnly = true, name = "content")
    private String handleSendMessage(String content) {
        return PacketListenerChatHook.modifyOutgoingMessage(content);
    }

}
