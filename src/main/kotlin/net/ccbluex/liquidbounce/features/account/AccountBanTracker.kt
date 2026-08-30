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
package net.ccbluex.liquidbounce.features.account

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.stripMinecraftColorCodes
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket

internal data class ParsedAccountBan(val reason: String, val bannedUntil: Long)

internal fun parseAccountBanMessage(message: String, now: Long = System.currentTimeMillis()): ParsedAccountBan? {
    val plainMessage = message.stripMinecraftColorCodes().trim()
    if (!BAN_MARKER_REGEX.containsMatchIn(plainMessage)) {
        return null
    }

    if (PERMANENT_REGEX.containsMatchIn(plainMessage)) {
        return ParsedAccountBan(plainMessage, -1L)
    }

    val duration = DURATION_REGEX.findAll(plainMessage).fold(0L) { total, match ->
        val amount = match.groupValues[1].toLongOrNull() ?: return@fold total
        val unitMillis = durationUnitMillis(match.groupValues[2])
        val boundedAmount = amount.coerceAtMost((MAX_BAN_DURATION_MILLIS - total) / unitMillis)
        total + (boundedAmount * unitMillis)
    }
    if (duration == 0L) {
        return null
    }

    val bannedUntil = if (now > Long.MAX_VALUE - duration) Long.MAX_VALUE else now + duration
    return ParsedAccountBan(plainMessage, bannedUntil)
}

object AccountBanTracker : EventListener {

    private val serverEndpoint = AccountServerEndpoint

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.origin != TransferOrigin.INCOMING || !event.original) {
            return@handler
        }

        val reason = disconnectReason(event.packet) ?: return@handler
        val ban = parseAccountBanMessage(reason) ?: return@handler
        val serverName = serverEndpoint.serverName ?: return@handler

        mc.execute {
            AccountManager.trackCurrentAccountBan(serverName, ban.reason, ban.bannedUntil)
        }
    }

}

internal fun disconnectReason(packet: Packet<*>): String? = when (packet) {
    is ClientboundLoginDisconnectPacket -> packet.reason.string
    is ClientboundDisconnectPacket -> packet.reason.string
    else -> null
}

private fun durationUnitMillis(unit: String): Long = when (unit.lowercase().first()) {
    'w' -> WEEK_MILLIS
    'd' -> DAY_MILLIS
    'h' -> HOUR_MILLIS
    'm' -> MINUTE_MILLIS
    else -> SECOND_MILLIS
}

private val BAN_MARKER_REGEX = Regex("\\bban(?:ned)?\\b", RegexOption.IGNORE_CASE)
private val PERMANENT_REGEX = Regex(
    "\\bpermanent(?:ly)?\\b[\\s\\S]*\\bban(?:ned)?\\b|\\bban(?:ned)?\\b[\\s\\S]*\\bpermanent(?:ly)?\\b",
    RegexOption.IGNORE_CASE,
)
private val DURATION_REGEX = Regex(
    "(\\d+)\\s*(weeks?|w|days?|d|hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b",
    RegexOption.IGNORE_CASE,
)

private const val SECOND_MILLIS = 1_000L
private const val MINUTE_MILLIS = 60 * SECOND_MILLIS
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
private const val DAY_MILLIS = 24 * HOUR_MILLIS
private const val WEEK_MILLIS = 7 * DAY_MILLIS
private const val MAX_BAN_DURATION_MILLIS = 100 * 365 * DAY_MILLIS
