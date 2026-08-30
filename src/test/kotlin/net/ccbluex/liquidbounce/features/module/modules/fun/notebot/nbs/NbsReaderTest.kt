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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs

import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NbsReaderTest {

    @Test
    fun `old NBS headers retain their implicit format defaults`() {
        val source = Buffer()
            .writeShortLe(12)
            .writeCommonHeader(layerCount = 3)
            .writeShortLe(0)

        val data = source.readNbsData()

        assertEquals(0, data.header.version.toInt())
        assertEquals(10, data.header.vanillaInstrumentCount.toInt())
        assertEquals(12, data.header.songLength.toInt())
        assertEquals(3, data.header.layerCount.toInt())
        assertEquals("Test Song", data.header.songName)
        assertEquals(0, data.header.loopOnOff.toInt())
        assertEquals(emptyList<NbsNoteBlock>(), data.noteBlocks)
    }

    @Test
    fun `version four NBS data keeps loop and per-note fields`() {
        val source = Buffer()
            .writeShortLe(0)
            .writeByte(4)
            .writeByte(16)
            .writeShortLe(42)
            .writeCommonHeader(layerCount = 6)
            .writeByte(1)
            .writeByte(5)
            .writeShortLe(9)
            .writeShortLe(1)
            .writeShortLe(1)
            .writeByte(3)
            .writeByte(45)
            .writeByte(80)
            .writeByte(123)
            .writeShortLe(4)
            .writeShortLe(0)
            .writeShortLe(0)

        val data = source.readNbsData()

        assertEquals(4, data.header.version.toInt())
        assertEquals(16, data.header.vanillaInstrumentCount.toInt())
        assertEquals(42, data.header.songLength.toInt())
        assertEquals(1, data.header.loopOnOff.toInt())
        assertEquals(5, data.header.maxLoopCount.toInt())
        assertEquals(9, data.header.loopStartTick.toInt())
        assertEquals(
            listOf(NbsNoteBlock(0, 0, 3, 45, 80, 123, 4)),
            data.noteBlocks,
        )
    }

    private fun Buffer.writeCommonHeader(layerCount: Int): Buffer =
        writeShortLe(layerCount)
            .writeNbsString("Test Song")
            .writeNbsString("Author")
            .writeNbsString("Original")
            .writeNbsString("Description")
            .writeShortLe(1_000)
            .writeByte(1)
            .writeByte(10)
            .writeByte(4)
            .writeIntLe(11)
            .writeIntLe(12)
            .writeIntLe(13)
            .writeIntLe(14)
            .writeIntLe(15)
            .writeNbsString("source.mid")

    private fun Buffer.writeNbsString(value: String): Buffer =
        writeIntLe(value.encodeToByteArray().size).writeUtf8(value)
}
