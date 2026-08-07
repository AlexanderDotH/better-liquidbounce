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

import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import net.ccbluex.liquidbounce.render.engine.type.Color4b

/** Immutable visual tokens for the native SeedCracker HUD card. */
internal data class SeedCrackerHudChrome(
    val backgroundColor: Color4b,
    val headerBackgroundColor: Color4b,
    val borderColor: Color4b,
    val accentColor: Color4b,
    val titleColor: Color4b,
    val bodyColor: Color4b,
    val actionColor: Color4b,
    val progressTrackColor: Color4b,
    val cornerRadius: Float,
    val outlineWidth: Float,
)

private val EXTERNAL_SEED_CRACKER_CHROME = SeedCrackerHudChrome(
    backgroundColor = Color4b(24, 24, 32, 176),
    headerBackgroundColor = Color4b(24, 24, 32, 176),
    borderColor = Color4b.TRANSPARENT,
    accentColor = Color4b(75, 205, 255),
    titleColor = Color4b(75, 205, 255),
    bodyColor = Color4b.WHITE,
    actionColor = Color4b(255, 220, 120),
    progressTrackColor = Color4b(255, 255, 255, 24),
    cornerRadius = 0.0F,
    outlineWidth = 0.0F,
)

internal fun resolveSeedCrackerHudChrome(
    hudTheme: HudTheme,
    bundledHud: Boolean,
    hudAccent: Color4b = DEFAULT_HUD_ACCENT,
    classicSurface: Color4b = Color4b.BLACK,
): SeedCrackerHudChrome {
    if (!bundledHud) {
        return EXTERNAL_SEED_CRACKER_CHROME
    }

    if (hudTheme == HudTheme.CLASSIC) {
        return SeedCrackerHudChrome(
            backgroundColor = classicSurface.alpha(128),
            headerBackgroundColor = classicSurface.alpha(173),
            borderColor = Color4b.TRANSPARENT,
            accentColor = hudAccent,
            titleColor = Color4b.WHITE,
            bodyColor = Color4b(211, 211, 211),
            actionColor = hudAccent,
            progressTrackColor = Color4b(255, 255, 255, 20),
            cornerRadius = 5.0F,
            outlineWidth = 0.0F,
        )
    }

    return SeedCrackerHudChrome(
        backgroundColor = Color4b(15, 18, 23, 230),
        headerBackgroundColor = Color4b(15, 18, 23, 230),
        borderColor = Color4b(255, 255, 255, 26),
        accentColor = hudAccent,
        titleColor = Color4b(238, 241, 245),
        bodyColor = Color4b(145, 154, 166),
        actionColor = hudAccent.interpolateTo(Color4b.WHITE, 0.18),
        progressTrackColor = Color4b(255, 255, 255, 18),
        cornerRadius = 10.0F,
        outlineWidth = 1.0F,
    )
}

internal fun resolveClassicHudSurface(defaultTint: Color4b, configuredTint: Color4b): Color4b =
    defaultTint.interpolateTo(configuredTint, CLASSIC_TINT_MIX).alpha(255)

internal val DEFAULT_HUD_ACCENT = Color4b(70, 119, 255)

private const val CLASSIC_TINT_MIX = 0.18
