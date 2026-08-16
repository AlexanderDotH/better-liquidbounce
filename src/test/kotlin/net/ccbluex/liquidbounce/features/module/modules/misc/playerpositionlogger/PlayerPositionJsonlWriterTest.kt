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
package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PlayerPositionJsonlWriterTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `writer persists one complete json object per line`() {
        val writer = PlayerPositionJsonlWriter.create(temporaryDirectory.toFile(), "session")
        val entry = PlayerPositionLogEntry(
            timestampMs = 1234L,
            tick = 9,
            dimension = "minecraft:overworld",
            origin = PlayerPositionLogOrigin.OUTGOING,
            kind = PlayerPositionLogKind.LOCAL_MOVEMENT,
            packetType = "ServerboundMovePlayerPacket.Pos",
            original = true,
            cancelled = false,
            player = PlayerPositionIdentity(1, "uuid", "Local", true),
            packetState = PlayerPositionPacketState(
                resolvedPosition = LoggedVector(1.0, 64.0, 2.0),
                onGround = true,
            ),
        )

        writer.write(entry)
        writer.close()

        val lines = writer.file.readLines()
        assertEquals(1, lines.size)
        val json = JsonParser.parseString(lines.single()).asJsonObject
        assertEquals(1234L, json["timestampMs"].asLong)
        assertEquals("OUTGOING", json["origin"].asString)
        assertEquals("LOCAL_MOVEMENT", json["kind"].asString)
        assertEquals(64.0, json["packetState"].asJsonObject["resolvedPosition"].asJsonObject["y"].asDouble)
        assertTrue(lines.single().startsWith("{"))
        assertTrue(lines.single().endsWith("}"))
    }

    @Test
    fun `writer avoids overwriting an existing session`() {
        temporaryDirectory.resolve("session.jsonl").toFile().writeText("existing")

        val writer = PlayerPositionJsonlWriter.create(temporaryDirectory.toFile(), "session")
        writer.close()

        assertEquals("session_1.jsonl", writer.file.name)
        assertEquals("existing", temporaryDirectory.resolve("session.jsonl").toFile().readText())
    }
}
