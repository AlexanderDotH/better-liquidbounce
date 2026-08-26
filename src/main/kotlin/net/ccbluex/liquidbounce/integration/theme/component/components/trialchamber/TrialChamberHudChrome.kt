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

import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import net.ccbluex.liquidbounce.render.engine.type.Color4b

/** Immutable theme tokens for the native TrialChamber HUD card. */
internal data class TrialChamberHudChrome(
    val backgroundColor: Color4b,
    val borderColor: Color4b,
    val accentColor: Color4b,
    val labelColor: Color4b,
    val valueColor: Color4b,
    val panelColor: Color4b,
    val dividerColor: Color4b,
    val positiveColor: Color4b,
    val warningColor: Color4b,
    val cornerRadius: Float,
    val panelRadius: Float,
    val outlineWidth: Float,
    val backgroundBlurRadius: Float,
)

private val EXTERNAL_TRIAL_CHAMBER_CHROME = TrialChamberHudChrome(
    backgroundColor = Color4b(14, 17, 24, 205),
    borderColor = Color4b.TRANSPARENT,
    accentColor = Color4b(120, 225, 255),
    labelColor = Color4b(235, 240, 245),
    valueColor = Color4b(235, 240, 245),
    panelColor = Color4b(255, 255, 255, 18),
    dividerColor = Color4b(255, 255, 255, 30),
    positiveColor = Color4b(77, 172, 104),
    warningColor = Color4b(239, 191, 4),
    cornerRadius = 0.0F,
    panelRadius = 0.0F,
    outlineWidth = 0.0F,
    backgroundBlurRadius = 8.0F,
)

internal fun resolveTrialChamberHudChrome(
    hudTheme: HudTheme,
    bundledHud: Boolean,
    hudAccent: Color4b = DEFAULT_TRIAL_CHAMBER_HUD_ACCENT,
    classicSurface: Color4b = Color4b.BLACK,
): TrialChamberHudChrome {
    if (!bundledHud) return EXTERNAL_TRIAL_CHAMBER_CHROME

    if (hudTheme == HudTheme.CLASSIC) {
        return TrialChamberHudChrome(
            backgroundColor = classicSurface.alpha(190),
            borderColor = Color4b.TRANSPARENT,
            accentColor = hudAccent,
            labelColor = Color4b(230, 233, 237),
            valueColor = Color4b.WHITE,
            panelColor = Color4b(255, 255, 255, 18),
            dividerColor = Color4b(255, 255, 255, 30),
            positiveColor = Color4b(77, 172, 104),
            warningColor = Color4b(239, 191, 4),
            cornerRadius = 5.0F,
            panelRadius = 3.0F,
            outlineWidth = 0.0F,
            backgroundBlurRadius = 10.0F,
        )
    }

    return TrialChamberHudChrome(
        backgroundColor = Color4b(15, 18, 23, 214),
        borderColor = Color4b(255, 255, 255, 26),
        accentColor = hudAccent,
        labelColor = Color4b(220, 225, 232),
        valueColor = Color4b(238, 241, 245),
        panelColor = Color4b(255, 255, 255, 18),
        dividerColor = Color4b(255, 255, 255, 30),
        positiveColor = Color4b(77, 172, 104),
        warningColor = Color4b(239, 191, 4),
        cornerRadius = 10.0F,
        panelRadius = 5.0F,
        outlineWidth = 1.0F,
        backgroundBlurRadius = 12.0F,
    )
}

internal fun resolveTrialChamberClassicSurface(defaultTint: Color4b, configuredTint: Color4b): Color4b =
    defaultTint.interpolateTo(configuredTint, CLASSIC_TINT_MIX).alpha(255)

internal val DEFAULT_TRIAL_CHAMBER_HUD_ACCENT = Color4b(70, 119, 255)

private const val CLASSIC_TINT_MIX = 0.18
