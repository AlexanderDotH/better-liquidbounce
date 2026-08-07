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
package net.ccbluex.liquidbounce.integration.theme.component.components.seedcracker

import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleSeedCracker
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackerState
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerRuntime
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerStatus
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.formattedEta
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.formattedPercent
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.formattedRate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.seedCrackerTranslation
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.integration.theme.component.isBundledHudRendered
import net.ccbluex.liquidbounce.integration.theme.component.components.NativeHudComponent
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.engine.font.FontRenderer
import net.ccbluex.liquidbounce.render.engine.font.processor.MinecraftTextProcessor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.render.Alignment
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.plus
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Style

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
        val lines = statusLines(status, chrome)
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

    private fun statusLines(status: SeedCrackerStatus, chrome: SeedCrackerHudChrome): List<StatusLine> = buildList {
        add(StatusLine(
            text = "SeedCracker · ${stateLabel(status.state)}",
            color = chrome.titleColor.argb,
            role = SeedCrackerHudLineRole.TITLE,
            bold = true,
        ))
        val candidate = status.candidate.takeIf { status.state == CrackerState.CANDIDATE }
        if (status.scope.isOverworld) {
            add(StatusLine(seedCrackerTranslation(
                "overlay.structures",
                status.acceptedStructureCount,
                status.pendingStructureCount,
            ).string, chrome.bodyColor.argb))
            status.structureProgress?.takeIf { candidate == null }?.let { progress ->
                add(StatusLine(seedCrackerTranslation(
                    "overlay.shipwreckProgress",
                    progress.acceptedIndependentEvidence,
                    progress.requiredIndependentEvidence,
                ).string, chrome.bodyColor.argb))
            }
        } else if (status.scope.isNether) {
            add(StatusLine(seedCrackerTranslation(
                "overlay.netherBedrock",
                status.acceptedNetherBedrockChunkCount,
                status.pendingNetherBedrockChunkCount,
            ).string, chrome.bodyColor.argb))
            status.netherSearchProgress?.takeIf { candidate == null }?.let { progress ->
                add(StatusLine(seedCrackerTranslation(
                    if (progress.paused) "overlay.netherProgressPaused" else "overlay.netherProgress",
                    progress.formattedPercent(),
                    progress.formattedRate(),
                    progress.formattedEta(),
                ).string, chrome.bodyColor.argb))
            }
        }
        if (candidate == null) {
            add(StatusLine(
                shortNextAction(status),
                chrome.actionColor.argb,
                role = SeedCrackerHudLineRole.ACTION,
            ))
        } else {
            seedCrackerHudCandidateLinePlan(candidate).forEach { line ->
                add(StatusLine(
                    text = seedCrackerTranslation(line.translationKey, *line.arguments.toTypedArray()).string,
                    color = when (line.role) {
                        SeedCrackerHudLineRole.RESULT -> chrome.actionColor.argb
                        else -> chrome.accentColor.argb
                    },
                    role = line.role,
                    bold = line.role == SeedCrackerHudLineRole.RESULT,
                ))
            }
        }
    }.take(MAX_LINES)

    private const val CLASSIC_HEADER_HEIGHT = 16.0F
    private const val HORIZONTAL_PADDING = 7
    private const val MAX_LINES = 4
    private const val PROGRESS_RADIUS = 1.0F
}

private fun currentSeedCrackerHudColors(bundledHud: Boolean): CurrentHudColors {
    if (!bundledHud) return CurrentHudColors(DEFAULT_HUD_ACCENT, Color4b.BLACK)

    return runCatching {
        val theme = ThemeManager.getScreenLocation(CustomScreenType.HUD).theme
        val colors = theme.colors.inner.filterIsInstance<Value<*>>()
        val defaultTint = theme.metadata.colors?.get("Tint")?.let(Color4b::fromHex) ?: Color4b.BLACK
        val configuredTint = colors.colorValue("Tint", defaultTint)
        CurrentHudColors(
            accent = colors.colorValue("Accent", DEFAULT_HUD_ACCENT),
            classicSurface = resolveClassicHudSurface(defaultTint, configuredTint),
        )
    }.getOrDefault(CurrentHudColors(DEFAULT_HUD_ACCENT, Color4b.BLACK))
}

private fun List<Value<*>>.colorValue(name: String, fallback: Color4b): Color4b =
    firstOrNull { it.name.equals(name, ignoreCase = true) }?.get() as? Color4b ?: fallback

private fun stateLabel(state: CrackerState): String = seedCrackerTranslation(
    "status.state.${state.localizationKey}",
).string

private fun shortNextAction(status: SeedCrackerStatus): String = when (status.nextAction.key) {
    "seedcracker.guidance.findStructure" -> seedCrackerTranslation(
        "overlay.next.findStructure",
        status.nextAction.arguments.firstOrNull().orEmpty(),
    ).string

    "seedcracker.guidance.confirmEvidence" -> seedCrackerTranslation("overlay.next.confirmEvidence").string
    "seedcracker.guidance.chooseEvidence" -> seedCrackerTranslation(
        "overlay.next.chooseEvidence",
        status.nextAction.arguments.firstOrNull().orEmpty(),
    ).string

    "seedcracker.guidance.collectNetherBedrock" -> seedCrackerTranslation("overlay.next.netherBedrock").string
    "seedcracker.guidance.solving", "seedcracker.guidance.waitForSolver" ->
        seedCrackerTranslation("overlay.next.solving").string

    else -> seedCrackerTranslation(
        status.nextAction.key.removePrefix("seedcracker.guidance."),
        *status.nextAction.arguments.toTypedArray(),
    ).string
}

private fun StatusLine.fitToWidth(
    maxWidth: Int,
    fontRenderer: FontRenderer,
    baseFontScale: Float,
): FittedStatusLine {
    val scale = baseFontScale * role.scaleMultiplier
    val component = styledComponent(text)
    val width = fontRenderer.scaledWidth(component, scale)
    if (role == SeedCrackerHudLineRole.RESULT && width > maxWidth) {
        return FittedStatusLine(component, scale * maxWidth / width)
    }
    if (width <= maxWidth) return FittedStatusLine(component, scale)

    var low = 0
    var high = text.length
    while (low < high) {
        val middle = (low + high + 1) / 2
        val candidate = styledComponent(text.substring(0, middle) + ELLIPSIS)
        if (fontRenderer.scaledWidth(candidate, scale) <= maxWidth) low = middle else high = middle - 1
    }
    return FittedStatusLine(styledComponent(text.substring(0, low) + ELLIPSIS), scale)
}

private fun StatusLine.styledComponent(text: String) = text.asPlainText(
    if (bold) Style.EMPTY + ChatFormatting.BOLD else Style.EMPTY,
)

private fun FontRenderer.scaledWidth(text: net.minecraft.network.chat.Component, scale: Float): Float {
    val processed = process(text)
    return try {
        getStringWidth(processed) * scale
    } finally {
        MinecraftTextProcessor.TEXT_POOL.recycle(processed)
    }
}

private val CrackerState.localizationKey: String
    get() = when (this) {
        CrackerState.COLLECTING -> "collecting"
        CrackerState.NEEDS_ACTION -> "needsAction"
        CrackerState.SOLVING -> "solving"
        CrackerState.CANDIDATE -> "candidate"
        CrackerState.CONTRADICTED -> "contradicted"
        CrackerState.PAUSED -> "paused"
    }

private fun lineTop(index: Int): Float = if (index == 0) TITLE_TOP else BODY_TOP + (index - 1) * BODY_LINE_HEIGHT

private data class StatusLine(
    val text: String,
    val color: Int,
    val role: SeedCrackerHudLineRole = SeedCrackerHudLineRole.METRIC,
    val bold: Boolean = false,
)

private data class FittedStatusLine(
    val component: net.minecraft.network.chat.Component,
    val scale: Float,
)

private data class CurrentHudColors(val accent: Color4b, val classicSurface: Color4b)

private fun SeedCrackerStatus.progressFraction(): Float {
    netherSearchProgress?.let { return normalizedSeedCrackerHudProgress(it.percent, 100.0) }
    structureProgress?.let {
        return normalizedSeedCrackerHudProgress(
            it.acceptedIndependentEvidence.toDouble(),
            it.requiredIndependentEvidence.toDouble(),
        )
    }
    if (scope.isNether) {
        return normalizedSeedCrackerHudProgress(
            acceptedNetherBedrockChunkCount.toDouble(),
            MINIMUM_NETHER_CHUNKS,
        )
    }
    return 0.0F
}

private const val TITLE_TOP = 6.0F
private const val BODY_TOP = 18.0F
private const val BODY_LINE_HEIGHT = 10.0F
private const val MINIMUM_NETHER_CHUNKS = 2.0
private const val ELLIPSIS = "…"

internal fun shouldRenderSeedCrackerHud(
    moduleRunning: Boolean,
    appearanceHidden: Boolean,
    hudHidden: Boolean,
    statusAvailable: Boolean,
): Boolean = moduleRunning && !appearanceHidden && !hudHidden && statusAvailable
