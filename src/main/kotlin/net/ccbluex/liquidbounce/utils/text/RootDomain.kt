/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */


@file:JvmName("TextExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.text

import net.ccbluex.fastutil.unmodifiable
import net.ccbluex.liquidbounce.utils.kotlin.unmodifiable
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import kotlin.contracts.contract

/**
 * Returns the root domain of the domain.
 *
 * This means it removes the subdomain from the domain.
 * If the domain is already a root domain or an IP address, do nothing.
 *
 * e.g.
 *   "sub.example.com" -> "example.com"
 *   "example.com." -> "example.com"
 *   "127.0.0.1" -> "127.0.0.1"
 */
fun String.rootDomain(): String {
    var domain = this.trim().lowercase()

    if (domain.matches(IP_REGEX)) {
        // IP address
        return domain
    }

    // Check if domain ends with dot, if so, remove it
    if (domain.endsWith('.')) {
        domain = domain.dropLast(1)
    }

    val parts = domain.split('.')
    if (parts.size <= 2) {
        // Already a root domain
        return domain
    }

    return "${parts[parts.lastIndex - 1]}.${parts.last()}"
}

/**
 * Converts milliseconds to seconds, minutes, hours and days when present.
 */
fun Int.formatAsTime(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m ${seconds % 60}s"
        hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

fun Long.formatAsCapacity(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = this.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) {
        "$this ${units[unitIndex]}"
    } else {
        "%.2f ${units[unitIndex]}".format(size)
    }
}

fun String.hideSensitiveAddress(): String {
    val idx = lastIndexOf(':')
    val host = if (idx == -1) this else substring(0, idx)

    // Hide possibly sensitive information from LiquidProxy
    val newHost = when {
        host.endsWith(".liquidbounce.net") -> "<redacted>.liquidbounce.net"
        host.endsWith(".liquidproxy.net") -> "<redacted>.liquidproxy.net"
        else -> host
    }

    return if (idx == -1) newHost else newHost + substring(idx)
}

@JvmRecord
data class ColoredChar(val char: Char, val color: TextColor)

inline fun Char.colored(color: TextColor) = ColoredChar(this, color)

fun Char.repeat(n: Int): String = CharArray(n) { this }.concatToString()

/**
 * Generates a progress bar based on the [percent]age (range 0 to 100).
 */
fun textLoadingBar(
    percent: Int,
    progress: ColoredChar = '█'.colored(TextColor.WHITE),
    remaining: ColoredChar = '░'.colored(TextColor.DARK_GRAY),
    length: Int = 10
): Component {
    val clampedPercent = percent.coerceIn(0, 100)
    val filledBars = clampedPercent * length / 100

    val progressPart = progress.char.repeat(filledBars)
    val remainingPart = remaining.char.repeat(length - filledBars)

    return textOf(
        progressPart.asPlainText(Style.EMPTY + progress.color),
        remainingPart.asPlainText(Style.EMPTY + remaining.color),
    )
}
