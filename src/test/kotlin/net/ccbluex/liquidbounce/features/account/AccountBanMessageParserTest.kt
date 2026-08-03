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
package net.ccbluex.liquidbounce.features.account

import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class AccountBanMessageParserTest {

    @Test
    fun `parses colored CubeCraft day and hour ban`() {
        val message = """
            §cTemporarily Banned!
            §cYou have been banned from §9CubeCraft §cfor:
            §6Cheating: https://youtu.be/example
            §7Your ban will expire in:
            §c9 days and 22 hours
        """.trimIndent()

        val ban = assertNotNull(parseAccountBanMessage(message, NOW))

        assertEquals(NOW + (9 * DAY) + (22 * HOUR), ban.bannedUntil)
        assertFalse(ban.reason.contains('§'))
    }

    @Test
    fun `parses another server day and hour duration`() {
        val ban = assertNotNull(parseAccountBanMessage("Banned for 27 days and 5 hours", NOW))

        assertEquals(NOW + (27 * DAY) + (5 * HOUR), ban.bannedUntil)
    }

    @Test
    fun `parses compact ban duration`() {
        val ban = assertNotNull(parseAccountBanMessage("You are banned for 2d 3h 15m", NOW))

        assertEquals(NOW + (2 * DAY) + (3 * HOUR) + (15 * MINUTE), ban.bannedUntil)
    }

    @Test
    fun `parses permanent ban`() {
        val ban = assertNotNull(parseAccountBanMessage("§4You are permanently banned", NOW))

        assertEquals(-1L, ban.bannedUntil)
    }

    @Test
    fun `ignores disconnect messages without a ban`() {
        assertNull(parseAccountBanMessage("Failed to connect: Connection timed out", NOW))
    }

    @Test
    fun `extracts login phase disconnect reason`() {
        val packet = ClientboundLoginDisconnectPacket(Component.literal("Login ban message"))

        assertEquals("Login ban message", disconnectReason(packet))
    }

    @Test
    fun `extracts common phase disconnect reason`() {
        val packet = ClientboundDisconnectPacket(Component.literal("Common ban message"))

        assertEquals("Common ban message", disconnectReason(packet))
    }

    private companion object {
        const val NOW = 1_750_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }

}
