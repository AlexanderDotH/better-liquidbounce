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
@file:JvmName("SeedCrackerHudComponentKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.integration.theme.component.components.seedcracker

import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleSeedCracker
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerRuntime
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerStatus
import net.ccbluex.liquidbounce.integration.theme.component.isBundledHudRendered
import net.ccbluex.liquidbounce.integration.theme.component.components.NativeHudComponent
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.config.types.group.Alignment
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.plus

/** Movable native HUD-layout component for the local SeedCracker state. */
object SeedCrackerHudComponent : NativeHudComponent(
    name = "SeedCracker",
    enabled = true,
    alignment = Alignment(
        horizontalAlignment = SeedCrackerHudLayout.HORIZONTAL_ALIGNMENT,
        horizontalOffset = SeedCrackerHudLayout.HORIZONTAL_OFFSET,
        verticalAlignment = SeedCrackerHudLayout.VERTICAL_ALIGNMENT,
        verticalOffset = SeedCrackerHudLayout.VERTICAL_OFFSET,
    ),
    description = "Shows SeedCracker evidence progress and the next useful local action.",
) {

    override val guiScaledWidth: Float = SeedCrackerHudLayout.WIDTH
    override val guiScaledHeight: Float = SeedCrackerHudLayout.HEIGHT

    init {
        registerComponentListen(this)
    }

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        val status = SeedCrackerRuntime.hudStatus()
        if (!shouldRenderSeedCrackerHud(
                moduleRunning = ModuleSeedCracker.running,
                appearanceHidden = HideAppearance.isHidingNow,
                hudHidden = mc.gui.hud.isHidden,
                statusAvailable = status != null,
            )
        ) {
            return@handler
        }

        render(event, checkNotNull(status))
    }

    private fun render(event: OverlayRenderEvent, status: SeedCrackerStatus) {
        val bounds = getGuiScaledBounds()
        val bundledHud = isBundledHudRendered()
        val hudColors = currentSeedCrackerHudColors(bundledHud)
        val chrome = resolveSeedCrackerHudChrome(
            hudTheme = ModuleHud.theme,
            bundledHud = bundledHud,
            hudAccent = hudColors.accent,
            classicSurface = hudColors.classicSurface,
        )
        val left = bounds.xMin.toInt()
        val top = bounds.yMin.toInt()
        val right = bounds.xMax.toInt()
        drawChrome(event, chrome, status.progressFraction())

        val fontRenderer = FontManager.FONT_RENDERER
        val fontScale = fontRenderer.scaleToVanillaFont
        val lines = seedCrackerStatusLines(status, chrome)
        lines.forEachIndexed { index, line ->
            val fitted = line.fitToWidth(
                maxWidth = (right - left) - HORIZONTAL_PADDING * 2,
                fontRenderer = fontRenderer,
                baseFontScale = fontScale,
            )
            with(event.context) {
                fontRenderer.draw(fontRenderer.process(fitted.component, Color4b(line.color))) {
                    x = (left + HORIZONTAL_PADDING).toFloat()
                    y = top + lineTop(index)
                    scale = fitted.scale
                }
            }
        }
    }

    private fun drawChrome(event: OverlayRenderEvent, chrome: SeedCrackerHudChrome, progress: Float) {
        val bounds = getGuiScaledBounds()
        if (chrome.cornerRadius == 0.0F) {
            event.context.fill(
                bounds.xMin.toInt(),
                bounds.yMin.toInt(),
                bounds.xMax.toInt(),
                bounds.yMax.toInt(),
                chrome.backgroundColor.argb,
            )
        } else {
            event.context.drawRoundedRect(
                bounds.xMin,
                bounds.yMin,
                bounds.xMax,
                bounds.yMax,
                chrome.cornerRadius,
                chrome.backgroundColor,
                chrome.borderColor,
                chrome.outlineWidth,
            )
            drawClassicHeader(event, chrome)
        }
        drawProgress(event, chrome, progress)
    }

    private fun drawClassicHeader(event: OverlayRenderEvent, chrome: SeedCrackerHudChrome) {
        if (chrome.headerBackgroundColor == chrome.backgroundColor) return

        val bounds = getGuiScaledBounds()
        val headerBottom = bounds.yMin + CLASSIC_HEADER_HEIGHT
        event.context.drawRoundedRect(
            bounds.xMin,
            bounds.yMin,
            bounds.xMax,
            headerBottom + chrome.cornerRadius,
            chrome.cornerRadius,
            chrome.headerBackgroundColor,
        )
        event.context.fill(
            bounds.xMin.toInt(),
            headerBottom.toInt(),
            bounds.xMax.toInt(),
            (headerBottom + chrome.cornerRadius).toInt(),
            chrome.backgroundColor.argb,
        )
    }

    private fun drawProgress(event: OverlayRenderEvent, chrome: SeedCrackerHudChrome, progress: Float) {
        val bounds = getGuiScaledBounds()
        val geometry = seedCrackerHudProgressGeometry(bounds.xMax - bounds.xMin, bounds.yMax - bounds.yMin)
        val left = bounds.xMin + geometry.left
        val top = bounds.yMin + geometry.top
        val right = bounds.xMin + geometry.right
        val bottom = bounds.yMin + geometry.bottom
        event.context.drawRoundedRect(
            left,
            top,
            right,
            bottom,
            PROGRESS_RADIUS,
            chrome.progressTrackColor,
        )
        if (progress <= 0.0F) return
        event.context.drawRoundedRect(
            left,
            top,
            bounds.xMin + geometry.fillRight(progress),
            bottom,
            PROGRESS_RADIUS,
            chrome.accentColor,
        )
    }

    private const val CLASSIC_HEADER_HEIGHT = 16.0F
    private const val HORIZONTAL_PADDING = 7
    private const val PROGRESS_RADIUS = 1.0F
}
