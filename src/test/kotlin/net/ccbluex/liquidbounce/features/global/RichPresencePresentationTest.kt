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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RichPresencePresentationTest {

    @Test
    fun `presence parts retain declaration order and persisted tags`() {
        assertEquals(
            listOf(
                "ClientName",
                "ClientVersion",
                "ClientAuthor",
                "ClientBranch",
                "ClientCommit",
                "Modules",
                "MinecraftVersion",
                "ProtocolVersion",
                "Server",
            ),
            RichPresencePart.entries.map { it.tag },
        )
    }

    @Test
    fun `activity and status choices retain Discord mappings`() {
        assertEquals(
            listOf(
                "Playing" to DiscordActivity.Type.PLAYING,
                "Listening" to DiscordActivity.Type.LISTENING,
                "Watching" to DiscordActivity.Type.WATCHING,
                "Competing" to DiscordActivity.Type.COMPETING,
            ),
            PresenceActivityType.entries.map { it.tag to it.activityType },
        )
        assertEquals(
            listOf(
                "Name" to DiscordActivity.StatusDisplayType.NAME,
                "State" to DiscordActivity.StatusDisplayType.STATE,
                "Details" to DiscordActivity.StatusDisplayType.DETAILS,
            ),
            PresenceStatusDisplayType.entries.map { it.tag to it.statusDisplayType },
        )
        assertEquals(listOf("Logo" to "liquidbounce"), PresenceAsset.entries.map { it.tag to it.assetValue })
    }

    @Test
    fun `resolved pieces retain order while null and blank pieces are omitted`() {
        assertEquals(
            "first | second",
            RichPresencePresentation.joinResolvedParts(
                listOf(null, "", "first", "   ", "second"),
                separator = " | ",
            ),
        )
        assertEquals("", RichPresencePresentation.joinResolvedParts(listOf(null, "", "  "), " - "))
    }

    @Test
    fun `static client metadata parts retain their established values`() {
        assertEquals(ClientBuildMetadata.NAME, RichPresencePart.CLIENT_NAME.getText())
        assertEquals(ClientBuildMetadata.version, RichPresencePart.CLIENT_VERSION.getText())
        assertEquals(ClientBuildMetadata.AUTHOR, RichPresencePart.CLIENT_AUTHOR.getText())
        assertEquals(ClientBuildMetadata.branch, RichPresencePart.CLIENT_BRANCH.getText())
        assertEquals(ClientBuildMetadata.commit, RichPresencePart.CLIENT_COMMIT.getText())
    }
}
