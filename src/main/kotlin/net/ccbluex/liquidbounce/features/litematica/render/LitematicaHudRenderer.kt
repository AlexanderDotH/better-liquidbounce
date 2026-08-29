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
package net.ccbluex.liquidbounce.features.litematica.render

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object LitematicaHudRenderer {
    fun render(context: GuiGraphicsExtractor, snapshot: LitematicaHudSnapshot) {
        val lines = LitematicaHudPresenter.present(snapshot).lines
        if (lines.isEmpty()) return

        val font = mc.font
        val maximumTextWidth = (context.guiWidth() - 2 * (HORIZONTAL_MARGIN + PADDING)).coerceAtLeast(0)
        val visibleLines = lines.map { it.copy(text = font.plainSubstrByWidth(it.text, maximumTextWidth)) }
        val textWidth = visibleLines.maxOf { font.width(it.text) }
        val left = context.guiWidth() - textWidth - HORIZONTAL_MARGIN
        val top = VERTICAL_MARGIN
        val lineHeight = font.lineHeight + LINE_GAP
        val bottom = top + lines.size * lineHeight

        context.fill(
            left - PADDING,
            top - PADDING,
            left + textWidth + PADDING,
            bottom + PADDING - LINE_GAP,
            BACKGROUND.argb,
        )
        visibleLines.forEachIndexed { index, line ->
            context.text(font, line.text, left, top + index * lineHeight, colorFor(line.tone).argb, true)
        }
    }

    private fun colorFor(tone: LitematicaHudTone) = when (tone) {
        LitematicaHudTone.TITLE -> Color4b.LIQUID_BOUNCE
        LitematicaHudTone.NORMAL -> Color4b.WHITE
        LitematicaHudTone.WARNING -> Color4b.YELLOW
        LitematicaHudTone.ERROR -> Color4b.RED
        LitematicaHudTone.MUTED -> Color4b.LIGHT_GRAY
    }

    private const val HORIZONTAL_MARGIN = 8
    private const val VERTICAL_MARGIN = 8
    private const val PADDING = 4
    private const val LINE_GAP = 2
    private val BACKGROUND = Color4b(16, 18, 24, 180)
}
