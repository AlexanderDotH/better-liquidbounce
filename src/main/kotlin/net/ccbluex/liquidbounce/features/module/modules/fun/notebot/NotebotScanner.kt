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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot

import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.ccbluex.fastutil.enumMapOf
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.InstrumentNote
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.SongData
import net.ccbluex.liquidbounce.utils.block.getSortedSphere
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime.NoteBlockTracker
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime.NotebotBlocksAndRequirements
import net.ccbluex.liquidbounce.utils.math.toBlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument

object NotebotScanner : MinecraftShortcuts {
    fun scanBlocksAndCheckRequirements(songData: SongData): NotebotBlocksAndRequirements {
        return NotebotBlocksAndRequirements(
            availableBlocks = scanSurroundingNoteBlocks(songData),
            requirements = calculateRequirements(songData)
        )
    }

    private fun scanSurroundingNoteBlocks(songData: SongData): Map<NoteBlockInstrument, MutableList<NoteBlockTracker>> {
        val result = enumMapOf<NoteBlockInstrument, ArrayDeque<NoteBlockTracker>>()

        val surroundings = player.eyePosition.toBlockPos().getSortedSphere(ModuleNotebot.range)
        val noteBlocks = surroundings.filter { pos ->
            pos.state?.block === Blocks.NOTE_BLOCK && pos.above().stateOrEmpty.isAir
        }

        val requiredInstruments = ModuleNotebot.getRequiredInstruments(songData)
        noteBlocks.forEach { pos ->
            val instrument = pos.below().stateOrEmpty.instrument()
            if (instrument in requiredInstruments) {
                result.getOrPut(instrument) { ArrayDeque() }.add(NoteBlockTracker(pos))
            }
        }

        return result
    }

    // technically we'd need even more blocks than returned by this function
    // since a song tick != a game tick thus this is technically incorrect but works well enough
    // it has the advantage that we don't get super huge requirements for very fast songs -
    // and well playing the same sound multiple times a tick due to minecraft's limitations
    // would sound weird anyway
    private fun calculateRequirements(songData: SongData): Object2IntMap<InstrumentNote> {
        val maxConcurrentCounts = Object2IntOpenHashMap<InstrumentNote>()
        val countsInTick = Object2IntOpenHashMap<InstrumentNote>()
        for (notes in songData.notesByTick.values) {
            countsInTick.clear()
            for (note in notes) {
                val instrumentNote = ModuleNotebot.getPlayedNote(note)

                if (ModuleNotebot.reuseBlocks) {
                    maxConcurrentCounts.put(instrumentNote, 1)
                } else {
                    countsInTick.addTo(instrumentNote, 1)
                }
            }

            if (ModuleNotebot.reuseBlocks) {
                continue
            }

            for ((instrumentNote, count) in countsInTick) {
                maxConcurrentCounts.mergeInt(instrumentNote, count, ::maxOf)
            }
        }

        return maxConcurrentCounts
    }
}
