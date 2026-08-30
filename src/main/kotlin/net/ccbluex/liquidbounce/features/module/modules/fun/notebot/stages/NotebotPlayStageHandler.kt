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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.stages

import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotStage
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.InstrumentNote
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.SongData
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.resolveInstrumentNote
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime.NoteBlockTracker
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime.NotebotEngine
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime.NotebotStageHandler
import net.minecraft.ChatFormatting

internal class NotebotPlayStageHandler(
    private val availableBlocksForNote: Map<InstrumentNote, List<NoteBlockTracker>>
) : NotebotStageHandler {

    private val progressName = NotebotRuntimeBridge.message("progressPlay")
    private var songTickAccumulator = 0f
    private var currentSongTick = 0

    override val handledStage: NotebotStage
        get() = NotebotStage.PLAY

    override fun onTick(engine: NotebotEngine) {
        val songData = engine.songData

        songTickAccumulator += songData.songTicksPerGameTick

        while (songTickAccumulator >= 1f) {
            songTickAccumulator -= 1f
            currentSongTick++

            NotebotRuntimeBridge.sendProgress(progressName, currentSongTick, songData.songTickLength)

            if (currentSongTick > songData.songTickLength) {
                NotebotRuntimeBridge.chat(
                    NotebotRuntimeBridge.message("finished").withStyle(ChatFormatting.GREEN)
                )
                NotebotRuntimeBridge.disable()
                return
            }

            playNotesAtTick(currentSongTick, songData)
        }
    }

    private fun playNotesAtTick(tick: Int, songData: SongData) {
        val notes = songData.notesByTick[tick] ?: return
        val usedBlocks = hashSetOf<NoteBlockTracker>()

        notes.forEach { note ->
            val instrumentNote = resolveInstrumentNote(note, NotebotRuntimeBridge.pianoOnly())

            val blockToPlayWith = this.availableBlocksForNote[instrumentNote]!!.firstOrNull { it !in usedBlocks }

            if (blockToPlayWith != null) {
                blockToPlayWith.click()

                if (!NotebotRuntimeBridge.reuseBlocks()) {
                    usedBlocks.add(blockToPlayWith)
                }
            }
        }
    }

}
