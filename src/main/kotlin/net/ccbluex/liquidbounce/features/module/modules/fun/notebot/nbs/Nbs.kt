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
import java.io.IOException

/**
 * https://opennbs.org/nbs
 */
@Throws(IOException::class)
fun BufferedSource.readNbsData(): NbsData {
    val header = NbsHeaderReader(this).read()
    val noteBlocks = mutableListOf<NbsNoteBlock>()

    // Parse note blocks
    var tick = -1
    while (true) {
        val jumps = this.readShortLe()
        if (jumps.toInt() == 0) break
        tick += jumps.toInt()
        var layer = -1
        while (true) {
            val jumpsLayer = this.readShortLe()
            if (jumpsLayer.toInt() == 0) {
                break
            }

            layer += jumpsLayer.toInt()
            val instrument = this.readByte()
            val key = this.readByte()
            var velocity: Byte = 100 // Default for old format
            var panning = 100 // Default for old format
            var pitch: Short = 2 // Default for old format
            if (header.version >= 4) {
                velocity = this.readByte()
                panning = this.readUByte()
                pitch = this.readShortLe()
            }
            noteBlocks.add(NbsNoteBlock(tick, layer, instrument, key, velocity, panning, pitch))
        }
    }

    return NbsData(header = header, noteBlocks = noteBlocks)
}

@Throws(IOException::class)
private fun BufferedSource.readUByte(): Int = readByte().toUByte().toInt()
