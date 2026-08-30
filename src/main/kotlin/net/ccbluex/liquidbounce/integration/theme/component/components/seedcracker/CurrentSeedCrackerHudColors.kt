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

import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackerState
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerStatus
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.formattedEta
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.formattedPercent
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.formattedRate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.seedCrackerTranslation
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.integration.theme.component.isBundledHudRendered
import net.ccbluex.liquidbounce.render.drawRoundedRect
import net.ccbluex.liquidbounce.render.engine.font.FontRenderer
import net.ccbluex.liquidbounce.render.engine.font.processor.MinecraftTextProcessor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.plus
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Style

internal fun currentSeedCrackerHudColors(bundledHud: Boolean): CurrentHudColors {
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

internal fun List<Value<*>>.colorValue(name: String, fallback: Color4b): Color4b =
    firstOrNull { it.name.equals(name, ignoreCase = true) }?.get() as? Color4b ?: fallback

internal fun stateLabel(state: CrackerState): String = seedCrackerTranslation(
    "status.state.${state.localizationKey}",
).string

internal fun shortNextAction(status: SeedCrackerStatus): String = when (status.nextAction.key) {
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

internal fun StatusLine.fitToWidth(
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

internal fun StatusLine.styledComponent(text: String) = text.asPlainText(
    if (bold) Style.EMPTY + ChatFormatting.BOLD else Style.EMPTY,
)

internal fun FontRenderer.scaledWidth(text: net.minecraft.network.chat.Component, scale: Float): Float {
    val processed = process(text)
    return try {
        getStringWidth(processed) * scale
    } finally {
        MinecraftTextProcessor.TEXT_POOL.recycle(processed)
    }
}

internal val CrackerState.localizationKey: String
    get() = when (this) {
        CrackerState.COLLECTING -> "collecting"
        CrackerState.NEEDS_ACTION -> "needsAction"
        CrackerState.SOLVING -> "solving"
        CrackerState.CANDIDATE -> "candidate"
        CrackerState.CONTRADICTED -> "contradicted"
        CrackerState.PAUSED -> "paused"
    }

internal fun lineTop(index: Int): Float = if (index == 0) TITLE_TOP else BODY_TOP + (index - 1) * BODY_LINE_HEIGHT

internal data class StatusLine(
    val text: String,
    val color: Int,
    val role: SeedCrackerHudLineRole = SeedCrackerHudLineRole.METRIC,
    val bold: Boolean = false,
)

internal data class FittedStatusLine(
    val component: net.minecraft.network.chat.Component,
    val scale: Float,
)

internal data class CurrentHudColors(val accent: Color4b, val classicSurface: Color4b)

internal fun SeedCrackerStatus.progressFraction(): Float {
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

internal const val TITLE_TOP = 6.0F
internal const val BODY_TOP = 18.0F
internal const val BODY_LINE_HEIGHT = 10.0F
internal const val MINIMUM_NETHER_CHUNKS = 2.0
internal const val ELLIPSIS = "…"

internal fun shouldRenderSeedCrackerHud(
    moduleRunning: Boolean,
    appearanceHidden: Boolean,
    hudHidden: Boolean,
    statusAvailable: Boolean,
): Boolean = moduleRunning && !appearanceHidden && !hudHidden && statusAvailable
