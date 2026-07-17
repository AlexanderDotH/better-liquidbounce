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
package net.ccbluex.liquidbounce.features.module.modules.misc.playercheatdetector

import java.util.UUID
import kotlin.math.max

class ObserverViolationBuffer {

    data class Key(val playerId: UUID, val checkName: String)

    private data class State(
        var violationLevel: Double = 0.0,
        var lastNoticeMs: Long = Long.MIN_VALUE,
    )

    private val states = hashMapOf<Key, State>()

    fun submit(
        flag: DetectionFlag,
        strictness: DetectorStrictness,
        minConfidence: Int,
        cooldownMs: Long,
        nowMs: Long,
    ): DetectionNotice? {
        if (flag.confidence < minConfidence) {
            return null
        }

        val key = Key(flag.playerId, flag.checkName)
        val state = states.getOrPut(key) { State() }
        state.violationLevel += flag.confidence / 100.0

        if (state.violationLevel < strictness.notificationViolationLevel) {
            return null
        }

        if (state.lastNoticeMs != Long.MIN_VALUE && nowMs - state.lastNoticeMs < cooldownMs) {
            return null
        }

        state.lastNoticeMs = nowMs
        return DetectionNotice(flag, state.violationLevel)
    }

    fun reward(key: Key, amount: Double = 0.05) {
        val state = states[key] ?: return
        state.violationLevel = max(0.0, state.violationLevel - amount)
    }

    fun violationLevel(key: Key): Double = states[key]?.violationLevel ?: 0.0

    fun reset(playerId: UUID? = null) {
        if (playerId == null) {
            states.clear()
            return
        }

        states.keys.removeIf { it.playerId == playerId }
    }
}
