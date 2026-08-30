/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs

import net.ccbluex.fastutil.enumSetOf
import net.minecraft.util.Mth
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument

internal fun resolveInstrumentNote(note: NbsNoteBlock, pianoOnly: Boolean): InstrumentNote {
    val noteValue = Mth.clamp(note.key - 33, 0, 24)
    val instrument = if (pianoOnly) 0 else note.instrument.toInt()
    return InstrumentNote(instrument, noteValue)
}

internal fun resolveRequiredInstruments(songData: SongData, pianoOnly: Boolean): Set<NoteBlockInstrument> {
    if (pianoOnly) return setOf(NoteBlockInstrument.HARP)
    return songData.nbs.noteBlocks.mapTo(enumSetOf()) {
        InstrumentNote.getInstrumentEnumFromId(it.instrument.toInt())
    }
}
