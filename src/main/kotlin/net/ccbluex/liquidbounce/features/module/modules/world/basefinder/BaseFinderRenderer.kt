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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowSource
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderRenderer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.render.WorldToScreen
import kotlin.math.roundToInt

internal object BaseFinderRenderer {
    fun renderWorld(event: WorldRenderEvent, batch: BaseFinderRenderBatch, glowStyle: EspGlowStyle?) {
        if (batch.entries.isEmpty()) return
        event.renderEnvironment {
            for (entry in batch.entries) drawBox(entry.cameraRelativeBox, entry.faceColor, entry.outlineColor)
        }
        if (glowStyle == null) return
        batch.contributeGlowIfPresent {
            EspShaderRenderer.contributeGlow(event, EspGlowSource.BASE_FINDER, glowStyle) {
                for (entry in it.entries) drawBox(entry.cameraRelativeBox, entry.glowMaskColor, null)
            }
        }
    }

    fun renderMismatchWorld(event: WorldRenderEvent, batch: SeedMismatchRenderBatch) {
        if (batch.entries.isEmpty()) return
        event.renderEnvironment {
            for (entry in batch.entries) drawBox(entry.cameraRelativeBox, entry.faceColor, entry.outlineColor)
        }
    }

    fun renderLabels(event: OverlayRenderEvent, batch: BaseFinderRenderBatch) {
        val width = mc.window.guiScaledWidth.toFloat()
        val height = mc.window.guiScaledHeight.toFloat()
        for (label in batch.labels) renderLabel(event, label, width, height)
    }

    private fun renderLabel(event: OverlayRenderEvent, label: BaseFinderRenderLabel, width: Float, height: Float) {
        val screen = WorldToScreen.calculateScreenPos(label.position) ?: return
        if (screen.x !in 0f..width || screen.y !in 0f..height) return
        event.context.pose().withPush {
            translate(screen.x, screen.y)
            scale(label.scale, label.scale)
            val lineHeight = mc.font.lineHeight + 1
            event.context.drawCenteredText(label.headline, 0, label.color)
            event.context.drawCenteredText(label.details, lineHeight, Color4b.WHITE)
            label.evidenceLines.forEachIndexed { index, line ->
                event.context.drawCenteredText(line, lineHeight * (index + 2), Color4b.WHITE)
            }
        }
    }

    private fun net.minecraft.client.gui.GuiGraphicsExtractor.drawCenteredText(
        content: String,
        y: Int,
        color: Color4b,
    ) {
        val x = (-mc.font.width(content) * 0.5f).roundToInt()
        text(mc.font, content, x, y, color.argb, true)
    }
}
