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
package net.ccbluex.liquidbounce.common.interop

/** Neutral marker for a packed ARGB color published through interop events. */
interface ThemeColorPayload {
    val argb: Int

    fun withArgb(argb: Int): ThemeColorPayload
}

@JvmRecord
data class PackedThemeColor(override val argb: Int) : ThemeColorPayload {
    override fun withArgb(argb: Int) = PackedThemeColor(argb)
}

fun parseHexArgb(hex: String): Int {
    val cleanHex = hex.removePrefix("#")
    require(cleanHex.length == 6 || cleanHex.length == 8)

    val parsed = cleanHex.toLong(16).toInt()
    return if (cleanHex.length == 8) parsed else parsed or 0xFF000000.toInt()
}
