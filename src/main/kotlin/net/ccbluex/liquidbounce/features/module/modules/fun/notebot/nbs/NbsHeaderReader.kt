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

import okio.BufferedSource

internal class NbsHeaderReader(private val source: BufferedSource) {

    fun read(): NbsHeader {
        val format = readFormat()
        val metadata = readMetadata()
        val statistics = readStatistics()
        val loop = readLoop(format.version)
        return NbsHeader(
            version = format.version,
            vanillaInstrumentCount = format.vanillaInstrumentCount,
            songLength = format.songLength,
            layerCount = metadata.layerCount,
            songName = metadata.songName,
            songAuthor = metadata.songAuthor,
            songOriginalAuthor = metadata.songOriginalAuthor,
            songDescription = metadata.songDescription,
            tempo = metadata.tempo,
            autoSaving = metadata.autoSaving,
            autoSavingDuration = metadata.autoSavingDuration,
            timeSignature = metadata.timeSignature,
            minutesSpent = statistics.minutesSpent,
            leftClicks = statistics.leftClicks,
            rightClicks = statistics.rightClicks,
            noteBlocksAdded = statistics.noteBlocksAdded,
            noteBlocksRemoved = statistics.noteBlocksRemoved,
            midiFileName = statistics.midiFileName,
            loopOnOff = loop.enabled,
            maxLoopCount = loop.maxCount,
            loopStartTick = loop.startTick,
        )
    }

    private fun readFormat(): NbsFormat {
        val firstShort = source.readShortLe()
        if (firstShort.toInt() != 0) {
            return NbsFormat(version = 0, vanillaInstrumentCount = 10, songLength = firstShort)
        }

        val version = source.readByte()
        return NbsFormat(
            version = version,
            vanillaInstrumentCount = source.readByte(),
            songLength = if (version >= 3) source.readShortLe() else 0,
        )
    }

    private fun readMetadata() = NbsMetadata(
        layerCount = source.readShortLe(),
        songName = source.readNbsString(),
        songAuthor = source.readNbsString(),
        songOriginalAuthor = source.readNbsString(),
        songDescription = source.readNbsString(),
        tempo = source.readShortLe(),
        autoSaving = source.readByte(),
        autoSavingDuration = source.readByte(),
        timeSignature = source.readByte(),
    )

    private fun readStatistics() = NbsStatistics(
        minutesSpent = source.readIntLe(),
        leftClicks = source.readIntLe(),
        rightClicks = source.readIntLe(),
        noteBlocksAdded = source.readIntLe(),
        noteBlocksRemoved = source.readIntLe(),
        midiFileName = source.readNbsString(),
    )

    private fun readLoop(version: Byte): NbsLoopSettings = if (version >= 4) {
        NbsLoopSettings(
            enabled = source.readByte(),
            maxCount = source.readByte(),
            startTick = source.readShortLe(),
        )
    } else {
        NbsLoopSettings(enabled = 0, maxCount = 0, startTick = 0)
    }

    private fun BufferedSource.readNbsString(): String = readUtf8(readIntLe().toLong())
}

private data class NbsFormat(
    val version: Byte,
    val vanillaInstrumentCount: Byte,
    val songLength: Short,
)

private data class NbsMetadata(
    val layerCount: Short,
    val songName: String,
    val songAuthor: String,
    val songOriginalAuthor: String,
    val songDescription: String,
    val tempo: Short,
    val autoSaving: Byte,
    val autoSavingDuration: Byte,
    val timeSignature: Byte,
)

private data class NbsStatistics(
    val minutesSpent: Int,
    val leftClicks: Int,
    val rightClicks: Int,
    val noteBlocksAdded: Int,
    val noteBlocksRemoved: Int,
    val midiFileName: String,
)

private data class NbsLoopSettings(
    val enabled: Byte,
    val maxCount: Byte,
    val startTick: Short,
)
