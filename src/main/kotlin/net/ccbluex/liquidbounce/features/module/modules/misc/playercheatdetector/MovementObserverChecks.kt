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
import kotlin.math.abs

class ObservedMovementPredictionCheck : ObserverCheck {
    override val category = PlayerCheatCheck.MOVEMENT
    override val name = "ObservedMovementPrediction"
    override val sourceStableKey = "grim.movement.prediction"
    override val capability = DetectionCapability.DEGRADED_OBSERVER

    override fun handleMovement(frame: ObservedMovementFrame, strictness: DetectorStrictness): DetectionFlag? {
        if (frame.exemptFromMovementChecks || frame.previousPosition == null) {
            return null
        }

        val horizontalLimit = strictness.horizontalLimit + if (frame.sprinting) 0.12 else 0.0
        if (frame.horizontalSpeed > horizontalLimit) {
            return frame.flag(
                this,
                confidence = confidence(frame.horizontalSpeed, horizontalLimit, base = 68),
                verbose = "horizontal=${frame.horizontalSpeed.format()} limit=${horizontalLimit.format()}",
            )
        }

        if (!frame.nearGround && frame.delta.y > strictness.upwardLimit) {
            return frame.flag(
                this,
                confidence = confidence(frame.delta.y, strictness.upwardLimit, base = 72),
                verbose = "upward=${frame.delta.y.format()} limit=${strictness.upwardLimit.format()}",
            )
        }

        return null
    }
}

class ObservedFlightCheck : ObserverCheck {
    override val category = PlayerCheatCheck.MOVEMENT
    override val name = "ObservedFlight"
    override val sourceStableKey = "grim.flight.flight_a"
    override val capability = DetectionCapability.DEGRADED_OBSERVER

    private val hoverTicks = hashMapOf<UUID, Int>()

    override fun handleMovement(frame: ObservedMovementFrame, strictness: DetectorStrictness): DetectionFlag? {
        if (frame.exemptFromMovementChecks || frame.nearGround || frame.previousPosition == null) {
            hoverTicks.remove(frame.playerId)
            return null
        }

        val stableVertical = abs(frame.delta.y) < 0.01
        val suspicious = stableVertical && frame.horizontalSpeed > 0.08
        val ticks = if (suspicious) hoverTicks.add(frame.playerId) else 0

        if (!suspicious || ticks < strictness.hoverTicks) {
            return null
        }

        return frame.flag(
            this,
            confidence = (70 + ticks * 3).coerceAtMost(95),
            verbose = "hoverTicks=$ticks horizontal=${frame.horizontalSpeed.format()} dy=${frame.delta.y.format()}",
        )
    }

    override fun reset(playerId: UUID) {
        hoverTicks.remove(playerId)
    }

    override fun resetAll() {
        hoverTicks.clear()
    }
}

class ObservedGroundSpoofSymptomsCheck : ObserverCheck {
    override val category = PlayerCheatCheck.MOVEMENT
    override val name = "ObservedGroundSpoof"
    override val sourceStableKey = "grim.groundspoof.no_fall"
    override val capability = DetectionCapability.DEGRADED_OBSERVER

    private val invalidGroundTicks = hashMapOf<UUID, Int>()

    override fun handleMovement(frame: ObservedMovementFrame, strictness: DetectorStrictness): DetectionFlag? {
        if (frame.exemptFromMovementChecks || !frame.onGround || frame.nearGround) {
            invalidGroundTicks.remove(frame.playerId)
            return null
        }

        val ticks = invalidGroundTicks.add(frame.playerId)
        if (ticks < 3) {
            return null
        }

        return frame.flag(
            this,
            confidence = (72 + ticks * 5).coerceAtMost(96),
            verbose = "onGround=true nearGround=false ticks=$ticks",
        )
    }

    override fun reset(playerId: UUID) {
        invalidGroundTicks.remove(playerId)
    }

    override fun resetAll() {
        invalidGroundTicks.clear()
    }
}

internal fun HashMap<UUID, Int>.add(playerId: UUID): Int {
    val next = (this[playerId] ?: 0) + 1
    this[playerId] = next
    return next
}

internal fun ObservedMovementFrame.flag(
    check: ObserverCheck,
    confidence: Int,
    verbose: String,
    severity: DetectionSeverity = DetectionSeverity.INFO,
) = DetectionFlag(
    playerId = playerId,
    playerName = playerName,
    check = check.category,
    checkName = check.name,
    sourceStableKey = check.sourceStableKey,
    confidence = confidence,
    severity = severity,
    verbose = verbose,
    observedAtTick = tick,
)
