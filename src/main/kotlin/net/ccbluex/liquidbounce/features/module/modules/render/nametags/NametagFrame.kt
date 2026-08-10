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
package net.ccbluex.liquidbounce.features.module.modules.render.nametags

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.type.Color4b

internal enum class NametagFrameKind {
    CLASSIC,
    MODERN,
    GLOW,
}

internal data class NametagFrameGlow(
    val color: Color4b,
    val style: EspGlowStyle,
    val backgroundBlurRadius: Float,
)

internal data class NametagFrameAppearance(
    val fill: Color4b,
    val border: Color4b,
    val borderWidth: Float,
    val radius: Float,
    val glow: NametagFrameGlow?,
)

internal const val MODERN_FRAME_BACKGROUND_OPACITY_PERCENT = 84
internal const val MODERN_FRAME_BACKGROUND_BLUR_RADIUS = 12f
internal const val MODERN_FRAME_RADIUS = 6f

private val MODERN_FILL = Color4b(15, 18, 23, 214)
private val MODERN_BORDER = Color4b(255, 255, 255, 26)
internal val MODERN_BLUE_ACCENT = Color4b(70, 119, 255, 255)

internal fun resolveNametagFrameAppearance(
    kind: NametagFrameKind,
    classicBorderWidth: Float,
    classicRadius: Float,
    glowColor: Color4b = MODERN_BLUE_ACCENT,
    glowStyle: EspGlowStyle = EspGlowStyle.DEFAULT,
    glowBackgroundOpacityPercent: Int = MODERN_FRAME_BACKGROUND_OPACITY_PERCENT,
    glowBackgroundBlurRadius: Float = MODERN_FRAME_BACKGROUND_BLUR_RADIUS,
    glowFrameRadius: Float = MODERN_FRAME_RADIUS,
): NametagFrameAppearance = when (kind) {
    NametagFrameKind.CLASSIC -> NametagFrameAppearance(
        fill = Color4b.DEFAULT_BG_COLOR,
        border = Color4b.BLACK,
        borderWidth = classicBorderWidth,
        radius = classicRadius,
        glow = null,
    )

    NametagFrameKind.MODERN -> modernNametagFrame()
    NametagFrameKind.GLOW -> modernNametagFrame().copy(
        fill = MODERN_FILL.with(a = opacityPercentageToAlpha(glowBackgroundOpacityPercent)),
        border = Color4b.TRANSPARENT,
        borderWidth = 0f,
        radius = glowFrameRadius,
        glow = NametagFrameGlow(glowColor.with(a = 255), glowStyle, glowBackgroundBlurRadius),
    )
}

private fun modernNametagFrame() = NametagFrameAppearance(
    fill = MODERN_FILL,
    border = MODERN_BORDER,
    borderWidth = 1f,
    radius = MODERN_FRAME_RADIUS,
    glow = null,
)

private fun opacityPercentageToAlpha(opacityPercentage: Int): Int =
    (opacityPercentage.coerceIn(0, 100) * 255 + 50) / 100

/**
 * Moves the two former root frame values into the Classic choice. The new Frame value deliberately
 * stays on Modern so profiles created before the redesign adopt the requested new default.
 */
internal fun migrateLegacyNametagFrame(config: JsonObject) {
    val values = config.getAsJsonArray("value") ?: return
    if (values.any { it.asJsonObject["name"]?.asString == "Frame" }) return

    val classicValues = JsonArray()
    val iterator = values.iterator()
    while (iterator.hasNext()) {
        val value = iterator.next().asJsonObject
        if (value["name"]?.asString !in LEGACY_FRAME_VALUES) continue

        iterator.remove()
        classicValues.add(value)
    }

    if (classicValues.isEmpty) return
    values.add(frameValue(classicValues))
}

private fun frameValue(classicValues: JsonArray) = JsonObject().apply {
    addProperty("name", "Frame")
    addProperty("active", "Modern")
    add("value", JsonArray())
    add("choices", JsonObject().apply {
        add("Classic", choiceValue("Classic", classicValues))
        add("Modern", choiceValue("Modern", JsonArray()))
        add("Glow", choiceValue("Glow", JsonArray()))
    })
}

private fun choiceValue(name: String, values: JsonArray) = JsonObject().apply {
    addProperty("name", name)
    add("value", values)
}

private val LEGACY_FRAME_VALUES = setOf("BorderWidth", "BackgroundRadius")
