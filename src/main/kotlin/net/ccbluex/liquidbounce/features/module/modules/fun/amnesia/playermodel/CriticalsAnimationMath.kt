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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.CriticalsMode
import kotlin.math.PI
import kotlin.math.sin

internal object CriticalsAnimationMath {

    fun verticalOffset(
        mode: CriticalsMode,
        microHopHeight: Float,
        packetJitter: Float,
        progress: Float,
    ): Float {
        val microHop = when (mode) {
            CriticalsMode.MICRO_HOP,
            CriticalsMode.BOTH -> sin(progress * PI).toFloat() * microHopHeight
            CriticalsMode.PACKET -> 0f
        }
        val packet = when (mode) {
            CriticalsMode.PACKET,
            CriticalsMode.BOTH -> packetOffset(packetJitter, progress)
            CriticalsMode.MICRO_HOP -> 0f
        }
        return microHop + packet
    }

    fun packetOffset(packetJitter: Float, progress: Float): Float = when {
        progress < 0.18f -> packetJitter
        progress < 0.36f -> 0f
        progress < 0.54f -> packetJitter * 0.5f
        else -> 0f
    }

    fun swingProgress(progress: Float): Float = sin(progress * PI).toFloat().coerceIn(0f, 1f)
}
