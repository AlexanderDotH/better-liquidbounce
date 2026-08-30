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
package net.ccbluex.liquidbounce.features.module.modules.world.timer

import net.minecraft.util.Mth

internal enum class SmoothPulsePhase {
    DELAY,
    RAMP_UP,
    HOLD,
    RAMP_DOWN,
}

internal data class SmoothPulseDurations(
    val delay: Int,
    val rampUp: Int,
    val hold: Int,
    val rampDown: Int,
) {
    fun duration(phase: SmoothPulsePhase): Int = when (phase) {
        SmoothPulsePhase.DELAY -> delay
        SmoothPulsePhase.RAMP_UP -> rampUp
        SmoothPulsePhase.HOLD -> hold
        SmoothPulsePhase.RAMP_DOWN -> rampDown
    }
}

internal object SmoothPulsePolicy {
    fun initialPhase(durations: SmoothPulseDurations): SmoothPulsePhase =
        if (durations.delay == 0) SmoothPulsePhase.RAMP_UP else SmoothPulsePhase.DELAY

    fun nextActivePhase(current: SmoothPulsePhase, durations: SmoothPulseDurations): SmoothPulsePhase {
        var next = current
        do {
            next = nextPhase(next, durations)
        } while (durations.duration(next) == 0)
        return next
    }

    fun speed(
        phase: SmoothPulsePhase,
        phaseTick: Int,
        baseSpeed: Float,
        targetSpeed: Float,
        durations: SmoothPulseDurations,
    ): Float = when (phase) {
        SmoothPulsePhase.DELAY -> baseSpeed
        SmoothPulsePhase.HOLD -> targetSpeed
        SmoothPulsePhase.RAMP_UP -> Mth.lerp(
            smoothStep((phaseTick + 1).toFloat() / durations.rampUp),
            baseSpeed,
            targetSpeed,
        )
        SmoothPulsePhase.RAMP_DOWN -> Mth.lerp(
            smoothStep((phaseTick + 1).toFloat() / durations.rampDown),
            targetSpeed,
            baseSpeed,
        )
    }

    private fun nextPhase(current: SmoothPulsePhase, durations: SmoothPulseDurations) = when (current) {
        SmoothPulsePhase.DELAY -> SmoothPulsePhase.RAMP_UP
        SmoothPulsePhase.RAMP_UP -> if (durations.hold == 0) SmoothPulsePhase.RAMP_DOWN else SmoothPulsePhase.HOLD
        SmoothPulsePhase.HOLD -> SmoothPulsePhase.RAMP_DOWN
        SmoothPulsePhase.RAMP_DOWN -> if (durations.delay == 0) SmoothPulsePhase.RAMP_UP else SmoothPulsePhase.DELAY
    }

    private fun smoothStep(value: Float): Float {
        val clamped = value.coerceIn(0F, 1F)
        return clamped * clamped * (3F - 2F * clamped)
    }
}
