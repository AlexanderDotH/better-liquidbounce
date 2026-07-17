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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.entity.horizontalSpeed
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.withStrafe

class SpeedPulse(override val parent: ModeValueGroup<*>) : Mode("Pulse") {

    private val targetSpeed by float("TargetSpeed", 0.35f, 0.1f..2f)
    private val intervalTicks by int("IntervalTicks", 20, 1..200, "ticks")
    private val rampUpTicks by int("RampUpTicks", 5, 1..50, "ticks")
    private val rampDownTicks by int("RampDownTicks", 5, 1..50, "ticks")
    private val strength by float("Strength", 1f, 0.1f..1f)
    private val onlyWhenMoving by boolean("OnlyWhenMoving", true)

    private enum class PulseState {
        IDLE, RAMP_UP, RAMP_DOWN
    }

    private var state = PulseState.IDLE
    private var phaseTick = 0
    private var baselineSpeed = 0.0

    override fun enable() {
        resetState()
        super.enable()
    }

    override fun disable() {
        resetState()
        super.disable()
    }

    private fun resetState() {
        state = PulseState.IDLE
        phaseTick = 0
        baselineSpeed = 0.0
    }

    private fun shouldAdvancePhase(): Boolean = !onlyWhenMoving || player.moving

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        when (state) {
            PulseState.IDLE -> {
                if (!shouldAdvancePhase()) {
                    return@handler
                }

                phaseTick++
                if (phaseTick >= intervalTicks) {
                    if (!player.moving) {
                        return@handler
                    }
                    baselineSpeed = player.horizontalSpeed.coerceAtLeast(0.01)
                    phaseTick = 0
                    state = PulseState.RAMP_UP
                }
            }

            PulseState.RAMP_UP -> {
                if (!shouldAdvancePhase()) {
                    return@handler
                }

                phaseTick++
                if (player.moving) {
                    val t = (phaseTick / rampUpTicks.toDouble()).coerceIn(0.0, 1.0)
                    val speed = baselineSpeed + (targetSpeed - baselineSpeed) * t
                    player.deltaMovement = player.deltaMovement.withStrafe(
                        speed = speed,
                        strength = strength.toDouble(),
                    )
                }

                if (phaseTick >= rampUpTicks) {
                    state = PulseState.RAMP_DOWN
                    phaseTick = 0
                }
            }

            PulseState.RAMP_DOWN -> {
                if (!shouldAdvancePhase()) {
                    return@handler
                }

                phaseTick++
                if (player.moving) {
                    val t = (phaseTick / rampDownTicks.toDouble()).coerceIn(0.0, 1.0)
                    val speed = targetSpeed + (baselineSpeed - targetSpeed) * t
                    player.deltaMovement = player.deltaMovement.withStrafe(
                        speed = speed,
                        strength = strength.toDouble(),
                    )
                }

                if (phaseTick >= rampDownTicks) {
                    state = PulseState.IDLE
                    phaseTick = 0
                }
            }
        }
    }

}
