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

import net.ccbluex.fastutil.enumMapOf
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotStage
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.InstrumentNote
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime.NoteBlockTracker
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime.NotebotEngine
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime.NotebotStageHandler
import net.minecraft.ChatFormatting

internal class NotebotTuneStageHandler(engine: NotebotEngine) : NotebotStageHandler {

    private val progressName = NotebotRuntimeBridge.message("progressTune")
    private val assignments: Map<InstrumentNote, List<NoteBlockTracker>> = this.assignBlocks(engine)
    private val blocks: List<Pair<NoteBlockTracker, InstrumentNote>> = assignments.flatMap { note ->
        note.value.map { block -> block to note.key }
    }

    override val handledStage: NotebotStage
        get() = NotebotStage.TUNE

    init {
        NotebotRuntimeBridge.setRenderedBlocks(blocks.map { it.first.pos })
    }

    override fun onTick(engine: NotebotEngine) {
        val untunedBlocks = blocks.filter { (block, note) -> block.currentNote != note.noteValue }

        if (untunedBlocks.isEmpty()) {
            NotebotRuntimeBridge.chat(
                NotebotRuntimeBridge.message("startPlaying").withStyle(ChatFormatting.GREEN)
            )
            engine.changeStage(NotebotPlayStageHandler(this.assignments))

            return
        }

        val blockToTune = untunedBlocks.firstOrNull { (block, _) -> block.canTuneRightNow() }

        blockToTune?.first?.tuneOnce()

        NotebotRuntimeBridge.sendProgress(progressName, blocks.size - untunedBlocks.size, blocks.size)
    }

    private fun assignBlocks(engine: NotebotEngine): Map<InstrumentNote, List<NoteBlockTracker>> {
        val blocksAndRequirements = engine.blocksAndRequirements

        val requiredNotesByInstrument = blocksAndRequirements.requirements
            .object2IntEntrySet()
            .flatMap { (note, requiredTimes) ->
                List(requiredTimes) { note }
            }
            .groupByTo(enumMapOf()) { it.instrumentEnum }

        return buildMap<InstrumentNote, MutableList<NoteBlockTracker>> {
            for ((instrument, notesOfInstrument) in requiredNotesByInstrument) {
                assignBlocksOfInstrument(this, blocksAndRequirements.availableBlocks[instrument]!!, notesOfInstrument)
            }
        }
    }

    private fun assignBlocksOfInstrument(
        output: MutableMap<InstrumentNote, MutableList<NoteBlockTracker>>,
        blocksForInstrument: List<NoteBlockTracker>,
        notesOfInstrument: List<InstrumentNote>
    ) {
        val availableBlocksSortedByPitch = blocksForInstrument.sortedBy { tracker ->
            requireNotNull(tracker.currentNote) {
                "Cannot assign note block at ${tracker.pos} because its current note is still unknown."
            }
        }
        val notesToAssignSortedByPitch = notesOfInstrument.sortedBy { it.noteValue }

        val availableBlocksQueue = ArrayDeque(availableBlocksSortedByPitch)

        // Should yield the ideal solution. Prove me wrong if you like.
        // O(n^2)? I don't care.
        notesToAssignSortedByPitch.forEach { note ->
            val matchingBlockIndex = availableBlocksQueue.indexOfFirst { it.currentNote == note.noteValue }

            val firstTuneCost = calculateTuneCost(availableBlocksQueue.first().currentNote!!, note.noteValue)
            val lastTuneCost = calculateTuneCost(availableBlocksQueue.last().currentNote!!, note.noteValue)

            val bestBlock = when {
                matchingBlockIndex != -1 -> availableBlocksQueue.removeAt(matchingBlockIndex)
                firstTuneCost < lastTuneCost -> availableBlocksQueue.removeFirst()
                else -> availableBlocksQueue.removeLast()
            }

            output.getOrPut(note) { ArrayList() }.add(bestBlock)
        }
    }

    private fun calculateTuneCost(from: Int, to: Int): Int {
        return if (from < to) {
            to - from
        } else {
            24 - from + to
        }
    }
}
