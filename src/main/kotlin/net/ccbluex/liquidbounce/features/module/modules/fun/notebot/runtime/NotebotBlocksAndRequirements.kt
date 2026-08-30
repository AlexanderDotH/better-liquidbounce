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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime

import it.unimi.dsi.fastutil.objects.Object2IntMap
import net.ccbluex.fastutil.enumMapOf
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.InstrumentNote
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.ChatFormatting
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument

class NotebotBlocksAndRequirements(
    val availableBlocks: Map<NoteBlockInstrument, List<NoteBlockTracker>>,
    val requirements: Object2IntMap<InstrumentNote>,
) {
    fun validateRequirements(): Boolean {
        val totalRequired = requirements.values.sum()
        val totalAvailable = availableBlocks.values.sumOf { it.size }
        if (totalAvailable < totalRequired) return false

        val requirementByInstrument = enumMapOf<NoteBlockInstrument, Int>()
        requirements.forEach { (key, value) ->
            requirementByInstrument.merge(key.instrumentEnum, value, Int::plus)
        }
        return requirementByInstrument.all { (instrument, required) ->
            availableBlocks[instrument].let { it != null && it.size >= required }
        }
    }

    fun printRequirements() {
        val aggregatedRequirements = enumMapOf<NoteBlockInstrument, Int>()
        for ((key, count) in requirements) {
            aggregatedRequirements.merge(key.instrumentEnum, count, Int::plus)
        }

        val text = NotebotRuntimeBridge.message("notEnoughNoteBlocks").withStyle(ChatFormatting.RED)
        aggregatedRequirements.entries.sortedBy { -it.value }.forEach { (instrument, requiredCount) ->
            val availableCount = availableBlocks[instrument]?.size ?: 0
            val messageLine = "\n - ${instrument.name} ($availableCount/$requiredCount)"
            val color = when {
                availableCount >= requiredCount -> ChatFormatting.GREEN
                availableCount == 0 -> ChatFormatting.RED
                else -> ChatFormatting.YELLOW
            }
            text.append(messageLine.asPlainText(color))
        }
        NotebotRuntimeBridge.chat(text)
    }
}
