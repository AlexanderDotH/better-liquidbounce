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
import net.ccbluex.liquidbounce.render.engine.font.FontRenderer
import net.ccbluex.liquidbounce.render.engine.font.processor.MinecraftTextProcessor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.plus
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

internal fun FontRenderer.scaledWidth(text: Component, scale: Float): Float {
    val processed = process(text)
    return try {
        getStringWidth(processed) * scale
    } finally {
        MinecraftTextProcessor.TEXT_POOL.recycle(processed)
    }
}

internal fun GuiGraphicsExtractor.drawHudText(
    fontRenderer: FontRenderer,
    text: Component,
    color: Color4b,
    x: Float,
    y: Float,
    scale: Float,
) {
    fontRenderer.draw(fontRenderer.process(text, color)) {
        this.x = x
        this.y = y
        this.scale = scale
        shadow = TrialChamberHudTypography.TEXT_SHADOW
    }
}

internal fun GuiGraphicsExtractor.drawPanel(
    chrome: TrialChamberHudChrome,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) = drawRoundedRect(
    left,
    top,
    right,
    bottom,
    chrome.panelRadius,
    chrome.panelColor,
    chrome.dividerColor,
    PANEL_OUTLINE_WIDTH,
)

internal fun GuiGraphicsExtractor.drawSmallLabel(
    chrome: TrialChamberHudChrome,
    label: String,
    x: Float,
    y: Float,
) {
    val fontRenderer = FontManager.FONT_RENDERER
    drawHudText(
        fontRenderer,
        label.asPlainText(Style.EMPTY + ChatFormatting.BOLD),
        chrome.labelColor,
        x,
        y,
        fontRenderer.scaleToVanillaFont * TrialChamberHudTypography.SECTION_LABEL_SCALE,
    )
}

internal fun GuiGraphicsExtractor.drawStatBadgeRow(
    chrome: TrialChamberHudChrome,
    stats: List<TrialChamberHudStat>,
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    emptyLabel: String,
) {
    if (stats.isEmpty()) {
        drawSmallMutedText(chrome, emptyLabel, left, top + BADGE_TEXT_TOP)
        return
    }

    val fontRenderer = FontManager.FONT_RENDERER
    val texts = stats.map { stat ->
        "${stat.label} ${stat.count}".asPlainText(Style.EMPTY + ChatFormatting.BOLD)
    }
    val baseScale = fontRenderer.scaleToVanillaFont * TrialChamberHudTypography.BADGE_TEXT_SCALE
    val fixedWidth = stats.size * BADGE_HORIZONTAL_PADDING * 2.0F + (stats.size - 1) * BADGE_GAP
    val textWidth = texts.sumOf { fontRenderer.scaledWidth(it, baseScale).toDouble() }.toFloat()
    val availableTextWidth = (right - left - fixedWidth).coerceAtLeast(1.0F)
    val scale = if (textWidth > availableTextWidth) {
        baseScale * availableTextWidth / textWidth
    } else {
        baseScale
    }

    var x = left
    stats.forEachIndexed { index, stat ->
        val text = texts[index]
        val badgeWidth = fontRenderer.scaledWidth(text, scale) + BADGE_HORIZONTAL_PADDING * 2.0F
        val color = chrome.toneColor(stat.tone)
        drawRoundedRect(
            x,
            top,
            x + badgeWidth,
            top + height,
            height * 0.5F,
            color.alpha(BADGE_BACKGROUND_ALPHA),
        )
        drawHudText(
            fontRenderer,
            text,
            color.interpolateTo(chrome.valueColor, BADGE_TEXT_LIGHTEN),
            x + BADGE_HORIZONTAL_PADDING,
            top + BADGE_TEXT_TOP,
            scale,
        )
        x += badgeWidth + BADGE_GAP
    }
}

internal fun GuiGraphicsExtractor.drawCenteredLootStat(
    chrome: TrialChamberHudChrome,
    stat: TrialChamberHudStat,
    left: Float,
    right: Float,
    top: Float,
) {
    val fontRenderer = FontManager.FONT_RENDERER
    val labelScale = fontRenderer.scaleToVanillaFont * TrialChamberHudTypography.LOOT_LABEL_SCALE
    val valueScale = fontRenderer.scaleToVanillaFont * TrialChamberHudTypography.LOOT_VALUE_SCALE
    val label = stat.label.uppercase().asPlainText(Style.EMPTY + ChatFormatting.BOLD)
    val value = stat.count.toString().asPlainText(Style.EMPTY + ChatFormatting.BOLD)
    drawHudText(
        fontRenderer,
        label,
        chrome.labelColor,
        (left + right - fontRenderer.scaledWidth(label, labelScale)) * 0.5F,
        top + LOOT_LABEL_TOP,
        labelScale,
    )
    drawHudText(
        fontRenderer,
        value,
        chrome.valueColor,
        (left + right - fontRenderer.scaledWidth(value, valueScale)) * 0.5F,
        top + LOOT_VALUE_TOP,
        valueScale,
    )
}

internal fun GuiGraphicsExtractor.drawRightAlignedText(
    chrome: TrialChamberHudChrome,
    text: String,
    right: Float,
    top: Float,
    tone: TrialChamberHudTone,
) {
    val fontRenderer = FontManager.FONT_RENDERER
    val scale = fontRenderer.scaleToVanillaFont * TrialChamberHudTypography.INLINE_VALUE_SCALE
    val component = text.asPlainText()
    drawHudText(
        fontRenderer,
        component,
        chrome.toneColor(tone),
        right - fontRenderer.scaledWidth(component, scale),
        top,
        scale,
    )
}

internal fun GuiGraphicsExtractor.drawSmallMutedText(
    chrome: TrialChamberHudChrome,
    text: String,
    x: Float,
    y: Float,
) {
    val fontRenderer = FontManager.FONT_RENDERER
    drawHudText(
        fontRenderer,
        text.asPlainText(),
        chrome.labelColor,
        x,
        y,
        fontRenderer.scaleToVanillaFont * TrialChamberHudTypography.BADGE_TEXT_SCALE,
    )
}

internal fun TrialChamberHudChrome.toneColor(tone: TrialChamberHudTone): Color4b = when (tone) {
    TrialChamberHudTone.ACCENT -> accentColor
    TrialChamberHudTone.POSITIVE -> positiveColor
    TrialChamberHudTone.WARNING -> warningColor
    TrialChamberHudTone.MUTED -> labelColor
}

private const val BADGE_HORIZONTAL_PADDING = 3.0F
private const val BADGE_GAP = 3.0F
private const val BADGE_TEXT_TOP = 1.2F
private const val BADGE_BACKGROUND_ALPHA = 54
private const val BADGE_TEXT_LIGHTEN = 0.35
private const val LOOT_LABEL_TOP = 2.0F
private const val LOOT_VALUE_TOP = 10.0F
private const val PANEL_OUTLINE_WIDTH = 0.5F
