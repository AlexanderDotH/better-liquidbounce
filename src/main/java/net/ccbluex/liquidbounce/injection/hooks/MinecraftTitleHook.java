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

import net.ccbluex.liquidbounce.common.ClientBuildMetadata;
import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager;
import net.ccbluex.liquidbounce.integration.backend.browser.GlobalBrowserSettings;
import net.ccbluex.liquidbounce.interfaces.MinecraftClientFeatureBridge;
import net.ccbluex.liquidbounce.utils.client.ClientUtilsKt;
import net.ccbluex.liquidbounce.utils.client.vfp.VfpCompatibility;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static net.ccbluex.liquidbounce.utils.client.ProtocolUtilKt.getUsesViaFabricPlus;

public final class MinecraftTitleHook {

    private MinecraftTitleHook() {
    }

    @Nullable
    public static String buildTitle(
        Supplier<@Nullable ClientPacketListener> connectionSupplier,
        Supplier<@Nullable ServerData> serverDataSupplier,
        Supplier<@Nullable IntegratedServer> integratedServerSupplier,
        BooleanSupplier hotkeyAvailable
    ) {
        if (MinecraftClientFeatureBridge.isAppearanceHidden()) {
            return null;
        }

        ClientUtilsKt.getLogger().debug("Modifying window title");
        StringBuilder titleBuilder = createBaseTitle();
        appendBrowserAcceleration(titleBuilder, hotkeyAvailable);
        appendConnectionState(titleBuilder, connectionSupplier, serverDataSupplier, integratedServerSupplier);
        return titleBuilder.toString();
    }

    private static StringBuilder createBaseTitle() {
        StringBuilder titleBuilder = new StringBuilder(ClientBuildMetadata.NAME);
        titleBuilder.append(" v");
        titleBuilder.append(ClientBuildMetadata.INSTANCE.getVersion());
        titleBuilder.append(" ");

        if (ClientBuildMetadata.IN_DEVELOPMENT) {
            titleBuilder.append("(dev) ");
        }

        titleBuilder.append(ClientBuildMetadata.INSTANCE.getCommit());
        titleBuilder.append(" | ");
        titleBuilder.append(currentProtocolName());
        return titleBuilder;
    }

    private static String currentProtocolName() {
        if (getUsesViaFabricPlus()) {
            var protocolVersion = VfpCompatibility.INSTANCE.unsafeGetProtocolVersion();
            if (protocolVersion != null) {
                return protocolVersion.name();
            }
        }

        return SharedConstants.getCurrentVersion().name();
    }

    private static void appendBrowserAcceleration(
        StringBuilder titleBuilder,
        BooleanSupplier hotkeyAvailable
    ) {
        var backend = BrowserBackendManager.INSTANCE.getBackend();
        if (backend == null || !backend.isInitialized() || !backend.getAccelerationFlags().isSupported()) {
            return;
        }

        var accelerated = GlobalBrowserSettings.INSTANCE.getAccelerated();
        if (accelerated == null || !accelerated.get()) {
            return;
        }

        titleBuilder.append(" | Accelerated Paint is ON");
        if (hotkeyAvailable.getAsBoolean()) {
            titleBuilder.append(" [Hotkey: F12]");
        }
    }

    private static void appendConnectionState(
        StringBuilder titleBuilder,
        Supplier<@Nullable ClientPacketListener> connectionSupplier,
        Supplier<@Nullable ServerData> serverDataSupplier,
        Supplier<@Nullable IntegratedServer> integratedServerSupplier
    ) {
        ClientPacketListener connection = connectionSupplier.get();
        if (connection == null || !connection.getConnection().isConnected()) {
            return;
        }

        titleBuilder.append(" - ");
        ServerData serverData = serverDataSupplier.get();
        IntegratedServer integratedServer = integratedServerSupplier.get();
        appendServerType(titleBuilder, serverData, integratedServer);
    }

    private static void appendServerType(
        StringBuilder titleBuilder,
        @Nullable ServerData serverData,
        @Nullable IntegratedServer integratedServer
    ) {
        if (integratedServer != null && !integratedServer.isPublished()) {
            titleBuilder.append(I18n.get("title.singleplayer"));
        } else if (serverData != null && serverData.isRealm()) {
            titleBuilder.append(I18n.get("title.multiplayer.realms"));
        } else if (integratedServer == null && (serverData == null || !serverData.isLan())) {
            titleBuilder.append(I18n.get("title.multiplayer.other"));
        } else {
            titleBuilder.append(I18n.get("title.multiplayer.lan"));
        }
    }
}
