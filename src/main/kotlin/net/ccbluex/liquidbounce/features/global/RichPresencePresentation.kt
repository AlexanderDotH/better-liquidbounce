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

package net.ccbluex.liquidbounce.features.global

import net.ccbluex.discordipc.DiscordActivity
import net.ccbluex.liquidbounce.common.ClientBuildMetadata
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.protocolVersion
import net.ccbluex.liquidbounce.utils.text.hideSensitiveAddress
import net.minecraft.SharedConstants

internal object RichPresencePresentation {

    fun buildText(parts: Set<RichPresencePart>, separator: String): String =
        joinResolvedParts(parts.map(RichPresencePart::getText), separator)

    fun joinResolvedParts(pieces: Iterable<String?>, separator: String): String =
        pieces.mapNotNull { it?.takeIf(String::isNotBlank) }.joinToString(separator)
}

internal enum class RichPresencePart(override val tag: String) : Tagged {
    CLIENT_NAME("ClientName"),
    CLIENT_VERSION("ClientVersion"),
    CLIENT_AUTHOR("ClientAuthor"),
    CLIENT_BRANCH("ClientBranch"),
    CLIENT_COMMIT("ClientCommit"),
    MODULES_SUMMARY("Modules"),
    MINECRAFT_VERSION("MinecraftVersion"),
    PROTOCOL_VERSION("ProtocolVersion"),
    SERVER("Server");

    fun getText(): String? = when (this) {
        CLIENT_NAME -> ClientBuildMetadata.NAME
        CLIENT_VERSION -> ClientBuildMetadata.version
        CLIENT_AUTHOR -> ClientBuildMetadata.AUTHOR
        MODULES_SUMMARY -> "${ModuleManager.count { it.running }}/${ModuleManager.count()} modules"
        MINECRAFT_VERSION -> SharedConstants.getCurrentVersion().name().let { "Minecraft $it" }
        PROTOCOL_VERSION -> protocolVersion.let { "Joined with Minecraft ${it.name}" }
        SERVER -> (mc.currentServer?.ip ?: "none").hideSensitiveAddress()
        CLIENT_BRANCH -> ClientBuildMetadata.branch
        CLIENT_COMMIT -> ClientBuildMetadata.commit
    }
}

@Suppress("unused")
internal enum class PresenceActivityType(
    override val tag: String,
    val activityType: DiscordActivity.Type,
) : Tagged {
    PLAYING("Playing", DiscordActivity.Type.PLAYING),
    LISTENING("Listening", DiscordActivity.Type.LISTENING),
    WATCHING("Watching", DiscordActivity.Type.WATCHING),
    COMPETING("Competing", DiscordActivity.Type.COMPETING),
}

@Suppress("unused")
internal enum class PresenceStatusDisplayType(
    override val tag: String,
    val statusDisplayType: DiscordActivity.StatusDisplayType,
) : Tagged {
    NAME("Name", DiscordActivity.StatusDisplayType.NAME),
    STATE("State", DiscordActivity.StatusDisplayType.STATE),
    DETAILS("Details", DiscordActivity.StatusDisplayType.DETAILS),
}

internal enum class PresenceAsset(
    override val tag: String,
    val assetValue: String?,
) : Tagged {
    LOGO("Logo", "liquidbounce"),
}
