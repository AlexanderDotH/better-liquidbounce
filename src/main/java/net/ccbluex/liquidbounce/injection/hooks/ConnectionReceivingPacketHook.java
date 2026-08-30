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
package net.ccbluex.liquidbounce.injection.hooks;

import net.ccbluex.liquidbounce.event.EventManager;
import net.ccbluex.liquidbounce.event.events.PacketEvent;
import net.ccbluex.liquidbounce.event.events.TransferOrigin;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.RunningOnDifferentThreadException;

import java.util.function.Consumer;

public final class ConnectionReceivingPacketHook {

    private ConnectionReceivingPacketHook() {
    }

    public static void handle(
        Packet<?> packet,
        PacketListener listener,
        Runnable cancel,
        Consumer<Packet<?>> receiver
    ) {
        if (packet instanceof ClientboundBundlePacket bundlePacket) {
            cancel.run();
            receiveBundlePackets(bundlePacket, receiver);
            return;
        }

        dispatchIncomingPacket(packet, cancel);
    }

    private static void receiveBundlePackets(ClientboundBundlePacket bundlePacket, Consumer<Packet<?>> receiver) {
        for (Packet<?> packetInBundle : bundlePacket.subPackets()) {
            receiveSafely(packetInBundle, receiver);
        }
    }

    private static void receiveSafely(Packet<?> packet, Consumer<Packet<?>> receiver) {
        try {
            receiver.accept(packet);
        } catch (RunningOnDifferentThreadException ignored) {
        }
    }

    private static void dispatchIncomingPacket(Packet<?> packet, Runnable cancel) {
        final PacketEvent event = new PacketEvent(TransferOrigin.INCOMING, packet, true);
        EventManager.INSTANCE.callEvent(event);
        if (event.isCancelled()) {
            cancel.run();
        }
    }
}
