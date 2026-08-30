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

import net.ccbluex.liquidbounce.utils.math.center
import java.util.UUID

class ObservedFarPlaceCheck : ObserverCheck {
    override val category = PlayerCheatCheck.SCAFFOLD
    override val name = "ObservedFarPlace"
    override val sourceStableKey = "grim.scaffolding.far_place"
    override val capability = DetectionCapability.DEGRADED_OBSERVER

    override fun handleAction(frame: ObservedActionFrame, strictness: DetectorStrictness): DetectionFlag? {
        if (frame.type != ObservedActionType.BLOCK_PLACE) return null
        val blockPos = frame.blockPos ?: return null
        val distance = frame.eyePosition.distanceTo(blockPos.center)
        if (distance <= strictness.placeLimit) return null
        return DetectionFlag(
            playerId = frame.playerId,
            playerName = frame.playerName,
            check = category,
            checkName = name,
            sourceStableKey = sourceStableKey,
            confidence = confidence(distance, strictness.placeLimit, base = 70),
            severity = DetectionSeverity.INFO,
            verbose = "distance=${distance.format()} limit=${strictness.placeLimit.format()} block=$blockPos",
            observedAtTick = frame.tick,
        )
    }
}

class ObservedFastBreakCheck : ObserverCheck {
    override val category = PlayerCheatCheck.BREAKING
    override val name = "ObservedFastBreak"
    override val sourceStableKey = "grim.breaking.fast_break"
    override val capability = DetectionCapability.DEGRADED_OBSERVER
    private val breakTicks = hashMapOf<UUID, ArrayDeque<Int>>()

    override fun handleAction(frame: ObservedActionFrame, strictness: DetectorStrictness): DetectionFlag? {
        if (frame.type != ObservedActionType.BLOCK_BREAK) return null
        val ticks = breakTicks.getOrPut(frame.playerId) { ArrayDeque() }
        ticks.addLast(frame.tick)
        while (ticks.isNotEmpty() && frame.tick - ticks.first() > 10) ticks.removeFirst()
        val limit = when (strictness) {
            DetectorStrictness.CONSERVATIVE -> 6
            DetectorStrictness.NORMAL -> 5
            DetectorStrictness.STRICT -> 4
        }
        if (ticks.size < limit) return null
        return DetectionFlag(
            playerId = frame.playerId,
            playerName = frame.playerName,
            check = category,
            checkName = name,
            sourceStableKey = sourceStableKey,
            confidence = (68 + ticks.size * 4).coerceAtMost(92),
            severity = DetectionSeverity.INFO,
            verbose = "breaks=${ticks.size}/10ticks block=${frame.blockPos}",
            observedAtTick = frame.tick,
        )
    }

    override fun reset(playerId: UUID) {
        breakTicks.remove(playerId)
    }

    override fun resetAll() {
        breakTicks.clear()
    }
}
