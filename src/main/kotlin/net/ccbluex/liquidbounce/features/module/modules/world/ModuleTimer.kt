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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Timer module
 *
 * Changes the speed of the entire game.
 */
object ModuleTimer : ClientModule("Timer", ModuleCategories.WORLD, disableOnQuit = true) {

    val modes = choices("Mode", Classic, arrayOf(Classic, Pulse, SmoothPulse, Boost)).apply { tagBy(this) }

    object Classic : Mode("Classic") {

        override val parent: ModeValueGroup<Mode>
            get() = modes

        private val speed by float("Speed", 2f, 0.1f..20f)

        val repeatable = tickHandler {
            Timer.requestTimerSpeed(speed, Priority.IMPORTANT_FOR_USAGE_1, ModuleTimer)
        }

    }

    object Pulse : Mode("Pulse") {

        override val parent: ModeValueGroup<Mode>
            get() = modes

        private val normalSpeed: Float by float("NormalSpeed", 0.5f, 0.1f..20f)
        private val normalSpeedTicks by int("NormalSpeedTicks", 20, 1..500, "ticks")
        private val boostSpeed by float("BoostSpeed", 2f, 0.1f..20f)
        private val boostSpeedTicks by int("BoostSpeedTicks", 20, 1..500, "ticks")
        private val onMove by boolean("OnMove", false)
        private var currentState: TimerState = TimerState.NORMAL_SPEED

        override fun enable() {
            currentState = TimerState.NORMAL_SPEED
        }

        val repeatable = tickHandler {
            if (onMove && !ModuleTimer.player.moving) {
                return@tickHandler
            }

            val (nextState, currentSpeed, expirationTicks) = when (currentState) {
                TimerState.NORMAL_SPEED -> Triple(TimerState.BOOST_SPEED, normalSpeed, normalSpeedTicks)
                TimerState.BOOST_SPEED -> Triple(TimerState.NORMAL_SPEED, boostSpeed, boostSpeedTicks)
            }

            currentState = nextState

            Timer.requestTimerSpeed(
                timerSpeed = currentSpeed,
                priority = Priority.IMPORTANT_FOR_USAGE_1,
                provider = ModuleTimer,
                resetAfterTicks = expirationTicks
            )

            waitTicks(expirationTicks)

            return@tickHandler
        }

        enum class TimerState {
            NORMAL_SPEED, BOOST_SPEED
        }

    }

    object SmoothPulse : Mode("SmoothPulse") {

        override val parent: ModeValueGroup<Mode>
            get() = modes

        private val targetSpeed by float("TargetSpeed", 1.15f, 0.1f..20f)
        private val baseSpeed by float("BaseSpeed", 1.0f, 0.1f..20f)
        private val delayTicks by int("DelayTicks", 40, 0..500, "ticks")
        private val rampUpTicks by int("RampUpTicks", 8, 1..100, "ticks")
        private val holdTicks by int("HoldTicks", 4, 0..100, "ticks")
        private val rampDownTicks by int("RampDownTicks", 8, 1..100, "ticks")
        private val onMove by boolean("OnMove", true)

        private enum class Phase {
            Delay, RampUp, Hold, RampDown
        }

        private var phase = Phase.Delay
        private var phaseTick = 0

        override fun enable() {
            phase = initialPhase()
            phaseTick = 0
        }

        override fun disable() {
            phase = Phase.Delay
            phaseTick = 0
        }

        private fun initialPhase(): Phase =
            if (delayTicks == 0) Phase.RampUp else Phase.Delay

        private fun phaseDuration(phase: Phase): Int = when (phase) {
            Phase.Delay -> delayTicks
            Phase.RampUp -> rampUpTicks
            Phase.Hold -> holdTicks
            Phase.RampDown -> rampDownTicks
        }

        private fun nextPhase(current: Phase): Phase = when (current) {
            Phase.Delay -> Phase.RampUp
            Phase.RampUp -> if (holdTicks == 0) Phase.RampDown else Phase.Hold
            Phase.Hold -> Phase.RampDown
            Phase.RampDown -> if (delayTicks == 0) Phase.RampUp else Phase.Delay
        }

        private fun advancePhase() {
            phaseTick = 0
            do {
                phase = nextPhase(phase)
            } while (phaseDuration(phase) == 0)
        }

        private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)

        private fun speedForPhase(phase: Phase, phaseTick: Int): Float = when (phase) {
            Phase.Delay -> baseSpeed
            Phase.Hold -> targetSpeed
            Phase.RampUp -> {
                val t = smoothStep(((phaseTick + 1).toFloat() / rampUpTicks).coerceIn(0f, 1f))
                Mth.lerp(t, baseSpeed, targetSpeed)
            }
            Phase.RampDown -> {
                val t = smoothStep(((phaseTick + 1).toFloat() / rampDownTicks).coerceIn(0f, 1f))
                Mth.lerp(t, targetSpeed, baseSpeed)
            }
        }

        val repeatable = tickHandler {
            if (onMove && !ModuleTimer.player.moving) {
                phase = initialPhase()
                phaseTick = 0
                Timer.requestTimerSpeed(baseSpeed, Priority.IMPORTANT_FOR_USAGE_1, ModuleTimer)
                return@tickHandler
            }

            Timer.requestTimerSpeed(
                speedForPhase(phase, phaseTick),
                Priority.IMPORTANT_FOR_USAGE_1,
                ModuleTimer
            )

            phaseTick++
            if (phaseTick >= phaseDuration(phase)) {
                advancePhase()
            }
        }

    }

    object Boost : Mode("Boost") {

        override val parent: ModeValueGroup<Mode>
            get() = modes

        private val boostSpeed by float("BoostSpeed", 1.3f, 0.1f..20f)
        private val slowSpeed by float("SlowSpeed", 0.6f, 0.1f..20f)

        private val timeBoostTicks by int("TimeBoostTicks", 12, 1..60, "ticks")
        private var boostCapable = 0

        // basically timer balance
        private val accountTimerValue by boolean("AccountTimerValues", true)

        private val normalizeDuringCombat by boolean("NormalizeDuringCombat", true)
        private val allowNegative by boolean("AllowNegative", false)

        val repeatable = tickHandler {
            if (normalizeDuringCombat && CombatManager.isInCombat) {
                Timer.requestTimerSpeed(1f, Priority.IMPORTANT_FOR_USAGE_1, ModuleTimer)
                return@tickHandler
            }

            if (boostCapable < 0) {
                val ticks = abs(boostCapable)
                Timer.requestTimerSpeed(
                    slowSpeed,
                    Priority.IMPORTANT_FOR_USAGE_1,
                    ModuleTimer,
                    resetAfterTicks = ticks
                )

                notification(
                    "Timer", "Slowing down for $ticks ticks",
                    NotificationEvent.Severity.INFO
                )
                boostCapable = 0
                waitTicks(ticks)
            }

            if (!player.moving) {
                if (mc.gui.screen() is InventoryScreen || mc.gui.screen() is ContainerScreen) {
                    boostCapable = 0
                    return@tickHandler
                }

                Timer.requestTimerSpeed(slowSpeed, Priority.IMPORTANT_FOR_USAGE_1, ModuleTimer)

                val addition = if (accountTimerValue) (1 / slowSpeed).toInt() else 1
                boostCapable = (boostCapable + addition).coerceAtMost(timeBoostTicks)
            } else {
                val speedUp = boostCapable > 0 ||
                        (allowNegative && (CombatManager.isInCombat || ModuleScaffold.running))

                if (!speedUp) {
                    return@tickHandler
                }

                val ticks = if (boostCapable > 0) boostCapable else timeBoostTicks
                val speedUpTicks = if (accountTimerValue) ceil(ticks / boostSpeed).toInt() else ticks

                if (speedUpTicks == 0) {
                    return@tickHandler
                }

                Timer.requestTimerSpeed(
                    boostSpeed,
                    Priority.IMPORTANT_FOR_USAGE_1,
                    ModuleTimer,
                    resetAfterTicks = speedUpTicks
                )
                notification(
                    "Timer", "Boosted for $speedUpTicks ticks",
                    NotificationEvent.Severity.INFO
                )
                boostCapable -= ticks
                waitTicks(speedUpTicks)
            }
        }

    }

    override fun onDisabled() {
        Timer.requestTimerSpeed(1f, Priority.NOT_IMPORTANT, this@ModuleTimer)
    }

}
