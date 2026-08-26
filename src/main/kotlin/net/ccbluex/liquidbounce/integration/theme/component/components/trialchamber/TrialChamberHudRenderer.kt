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
package net.ccbluex.liquidbounce.integration.theme.component.components.trialchamber

import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.gui.GuiGlowRenderer
import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.plus
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Style

internal object TrialChamberHudRenderer {

    fun render(
        context: GuiGraphicsExtractor,
        bounds: BoundingBox2f,
        chrome: TrialChamberHudChrome,
        presentation: TrialChamberHudPresentation,
    ) {
        requestBackgroundBlur(context, bounds, chrome)
        drawChrome(context, bounds, chrome)
        drawSpawnerPanel(context, bounds, chrome, presentation.spawners)
        drawMobsPanel(context, bounds, chrome, presentation.livingMobs)
        drawVaultPanel(context, bounds, chrome, presentation.vaults)
        drawLootPanel(context, bounds, chrome, presentation.loot)
    }

    private fun drawChrome(
        context: GuiGraphicsExtractor,
        bounds: BoundingBox2f,
        chrome: TrialChamberHudChrome,
    ) {
        if (chrome.cornerRadius == 0.0F) {
            context.fill(
                bounds.xMin.toInt(),
                bounds.yMin.toInt(),
                bounds.xMax.toInt(),
                bounds.yMax.toInt(),
                chrome.backgroundColor.argb,
            )
            return
        }

        context.drawRoundedRect(
            bounds.xMin,
            bounds.yMin,
            bounds.xMax,
            bounds.yMax,
            chrome.cornerRadius,
            chrome.backgroundColor,
        )
        if (chrome.outlineWidth > 0.0F) {
            context.drawRoundedRect(
                bounds.xMin,
                bounds.yMin,
                bounds.xMax,
                bounds.yMax,
                chrome.cornerRadius,
                Color4b.TRANSPARENT,
                chrome.borderColor,
                chrome.outlineWidth,
            )
        }
    }

    private fun requestBackgroundBlur(
        context: GuiGraphicsExtractor,
        bounds: BoundingBox2f,
        chrome: TrialChamberHudChrome,
    ) {
        GuiGlowRenderer.requestRoundedFrame(
            pose = context.pose(),
            x1 = bounds.xMin,
            y1 = bounds.yMin,
            x2 = bounds.xMax,
            y2 = bounds.yMax,
            radius = chrome.cornerRadius,
            color = Color4b.BLACK,
            style = BACKDROP_ONLY_STYLE,
            backgroundBlurRadius = chrome.backgroundBlurRadius,
        )
    }

    private fun drawSpawnerPanel(
        context: GuiGraphicsExtractor,
        bounds: BoundingBox2f,
        chrome: TrialChamberHudChrome,
        stats: List<TrialChamberHudStat>,
    ) {
        val left = bounds.xMin + HORIZONTAL_PADDING
        val right = bounds.xMax - HORIZONTAL_PADDING
        val top = bounds.yMin + SPAWNER_PANEL_TOP
        context.drawPanel(chrome, left, top, right, top + SPAWNER_PANEL_HEIGHT)
        context.drawSmallLabel(chrome, "SPAWNERS", left + PANEL_PADDING, top + PANEL_LABEL_TOP)
        context.drawStatBadgeRow(
            chrome = chrome,
            stats = stats,
            left = left + PANEL_PADDING,
            right = right - PANEL_PADDING,
            top = top + SPAWNER_BADGES_TOP,
            height = BADGE_HEIGHT,
            emptyLabel = "None observed",
        )
    }

    private fun drawMobsPanel(
        context: GuiGraphicsExtractor,
        bounds: BoundingBox2f,
        chrome: TrialChamberHudChrome,
        livingMobs: Int,
    ) {
        val left = bounds.xMin + HORIZONTAL_PADDING
        val right = left + MOBS_PANEL_WIDTH
        val top = bounds.yMin + SUMMARY_PANEL_TOP
        context.drawPanel(chrome, left, top, right, top + SUMMARY_PANEL_HEIGHT)
        context.drawSmallLabel(chrome, "MOBS", left + PANEL_PADDING, top + INLINE_LABEL_TOP)

        val fontRenderer = FontManager.FONT_RENDERER
        val scale = fontRenderer.scaleToVanillaFont * TrialChamberHudTypography.INLINE_VALUE_SCALE
        val value = "$livingMobs alive".asPlainText(Style.EMPTY + ChatFormatting.BOLD)
        val color = if (livingMobs > 0) chrome.warningColor else chrome.positiveColor
        context.drawHudText(
            fontRenderer,
            value,
            color.interpolateTo(chrome.valueColor, STATUS_TEXT_LIGHTEN),
            right - PANEL_PADDING - fontRenderer.scaledWidth(value, scale),
            top + INLINE_VALUE_TOP,
            scale,
        )
    }

    private fun drawVaultPanel(
        context: GuiGraphicsExtractor,
        bounds: BoundingBox2f,
        chrome: TrialChamberHudChrome,
        stats: List<TrialChamberHudStat>,
    ) {
        val left = bounds.xMin + HORIZONTAL_PADDING + MOBS_PANEL_WIDTH + PANEL_GAP
        val right = bounds.xMax - HORIZONTAL_PADDING
        val top = bounds.yMin + SUMMARY_PANEL_TOP
        context.drawPanel(chrome, left, top, right, top + SUMMARY_PANEL_HEIGHT)
        context.drawSmallLabel(chrome, "VAULTS", left + PANEL_PADDING, top + INLINE_LABEL_TOP)
        context.drawStatBadgeRow(
            chrome = chrome,
            stats = stats,
            left = left + VAULT_BADGES_LEFT,
            right = right - PANEL_PADDING,
            top = top + INLINE_BADGES_TOP,
            height = BADGE_HEIGHT,
            emptyLabel = "None observed",
        )
    }

    private fun drawLootPanel(
        context: GuiGraphicsExtractor,
        bounds: BoundingBox2f,
        chrome: TrialChamberHudChrome,
        stats: List<TrialChamberHudStat>,
    ) {
        val left = bounds.xMin + HORIZONTAL_PADDING
        val right = bounds.xMax - HORIZONTAL_PADDING
        val top = bounds.yMin + LOOT_PANEL_TOP
        if (stats.isEmpty()) {
            context.drawPanel(chrome, left, top, right, top + LOOT_PANEL_HEIGHT)
            context.drawSmallLabel(chrome, "LOOT", left + PANEL_PADDING, top + LOOT_INLINE_TOP)
            context.drawRightAlignedText(
                chrome,
                "None observed",
                right - PANEL_PADDING,
                top + LOOT_INLINE_TOP,
                TrialChamberHudTone.MUTED,
            )
            return
        }

        val tileWidth = (right - left - LOOT_TILE_GAP * (stats.size - 1)) / stats.size
        stats.forEachIndexed { index, stat ->
            val tileLeft = left + index * (tileWidth + LOOT_TILE_GAP)
            val tileRight = tileLeft + tileWidth
            context.drawPanel(chrome, tileLeft, top, tileRight, top + LOOT_PANEL_HEIGHT)
            context.drawCenteredLootStat(chrome, stat, tileLeft, tileRight, top)
        }
    }

    private const val HORIZONTAL_PADDING = 6.0F
    private const val STATUS_TEXT_LIGHTEN = 0.28
    private const val SPAWNER_PANEL_TOP = 4.0F
    private const val SPAWNER_PANEL_HEIGHT = 25.0F
    private const val SPAWNER_BADGES_TOP = 12.5F
    private const val SUMMARY_PANEL_TOP = 33.0F
    private const val SUMMARY_PANEL_HEIGHT = 19.0F
    private const val LOOT_PANEL_TOP = 56.0F
    private const val LOOT_PANEL_HEIGHT = 20.0F
    private const val MOBS_PANEL_WIDTH = 66.0F
    private const val PANEL_GAP = 4.0F
    private const val PANEL_PADDING = 5.0F
    private const val PANEL_LABEL_TOP = 3.0F
    private const val INLINE_LABEL_TOP = 5.5F
    private const val INLINE_VALUE_TOP = 5.0F
    private const val INLINE_BADGES_TOP = 4.5F
    private const val VAULT_BADGES_LEFT = 38.0F
    private const val LOOT_INLINE_TOP = 5.5F
    private const val BADGE_HEIGHT = 10.0F
    private const val LOOT_TILE_GAP = 3.0F

    private val BACKDROP_ONLY_STYLE = EspGlowStyle(
        radius = 4.0F,
        softness = 1.0F,
        intensity = 0.0F,
        coreSize = 0.0F,
        opacity = 0.0F,
    )
}
