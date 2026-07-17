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
import net.ccbluex.liquidbounce.utils.math.distanceToSqr
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

class ObservedAntiKnockbackCheck : ObserverCheck {
    override val category = PlayerCheatCheck.VELOCITY
    override val name = "ObservedAntiKnockback"
    override val sourceStableKey = "grim.velocity.anti_knockback"
    override val capability = DetectionCapability.DEGRADED_OBSERVER

    private data class PendingVelocity(val tick: Int, val vector: Vec3, var ignoredTicks: Int = 0)

    private val pendingVelocities = hashMapOf<UUID, PendingVelocity>()

    override fun handleAction(frame: ObservedActionFrame, strictness: DetectorStrictness): DetectionFlag? {
        if (frame.type != ObservedActionType.VELOCITY) {
            return null
        }

        val vector = frame.vector ?: return null
        if (vector.horizontalLength() < 0.08 && abs(vector.y) < 0.08) {
            return null
        }

        pendingVelocities[frame.playerId] = PendingVelocity(frame.tick, vector)
        return null
    }

    override fun handleMovement(frame: ObservedMovementFrame, strictness: DetectorStrictness): DetectionFlag? {
        val pending = pendingVelocities[frame.playerId] ?: return null
        val age = frame.tick - pending.tick
        val expired = age > 12 || frame.teleportLike
        val waiting = age <= 0

        val expectedHorizontal = pending.vector.horizontalLength()
        val observedHorizontal = frame.delta.horizontalLength()
        val verticalResponse = if (pending.vector.y > 0.0) frame.delta.y else 0.0
        val tookVelocity = observedHorizontal > expectedHorizontal * 0.22 || verticalResponse > pending.vector.y * 0.20

        return when {
            expired || tookVelocity -> {
                pendingVelocities.remove(frame.playerId)
                null
            }
            waiting -> null
            pending.bumpIgnoredTicks() < 3 -> null
            else -> frame.flag(
                this,
                confidence = (76 + pending.ignoredTicks * 4).coerceAtMost(98),
                severity = DetectionSeverity.ERROR,
                verbose = "expected=${expectedHorizontal.format()} " +
                    "observed=${observedHorizontal.format()} ticks=${pending.ignoredTicks}",
            )
        }
    }

    override fun reset(playerId: UUID) {
        pendingVelocities.remove(playerId)
    }

    override fun resetAll() {
        pendingVelocities.clear()
    }

    private fun PendingVelocity.bumpIgnoredTicks(): Int {
        ignoredTicks++
        return ignoredTicks
    }
}

class ObservedReachCheck : ObserverCheck {
    override val category = PlayerCheatCheck.REACH
    override val name = "ObservedReach"
    override val sourceStableKey = "grim.combat.reach"
    override val capability = DetectionCapability.DEGRADED_OBSERVER

    override fun handleAction(frame: ObservedActionFrame, strictness: DetectorStrictness): DetectionFlag? {
        if (frame.type != ObservedActionType.DAMAGE) {
            return null
        }

        val targetBox = frame.targetBoundingBox ?: return null
        val distance = sqrt(targetBox.distanceToSqr(frame.eyePosition))
        if (distance <= strictness.reachLimit) {
            return null
        }

        val targetName = frame.targetName ?: "unknown"
        return DetectionFlag(
            playerId = frame.playerId,
            playerName = frame.playerName,
            check = category,
            checkName = name,
            sourceStableKey = sourceStableKey,
            confidence = confidence(distance, strictness.reachLimit, base = 74),
            severity = DetectionSeverity.ERROR,
            verbose = "distance=${distance.format()} " +
                "limit=${strictness.reachLimit.format()} target=$targetName",
            observedAtTick = frame.tick,
        )
    }
}

class ObservedFarPlaceCheck : ObserverCheck {
    override val category = PlayerCheatCheck.SCAFFOLD
    override val name = "ObservedFarPlace"
    override val sourceStableKey = "grim.scaffolding.far_place"
    override val capability = DetectionCapability.DEGRADED_OBSERVER

    override fun handleAction(frame: ObservedActionFrame, strictness: DetectorStrictness): DetectionFlag? {
        if (frame.type != ObservedActionType.BLOCK_PLACE) {
            return null
        }

        val blockPos = frame.blockPos ?: return null
        val distance = frame.eyePosition.distanceTo(blockPos.center)
        if (distance <= strictness.placeLimit) {
            return null
        }

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
        if (frame.type != ObservedActionType.BLOCK_BREAK) {
            return null
        }

        val ticks = breakTicks.getOrPut(frame.playerId) { ArrayDeque() }
        ticks.addLast(frame.tick)
        while (ticks.isNotEmpty() && frame.tick - ticks.first() > 10) {
            ticks.removeFirst()
        }

        val limit = when (strictness) {
            DetectorStrictness.CONSERVATIVE -> 6
            DetectorStrictness.NORMAL -> 5
            DetectorStrictness.STRICT -> 4
        }

        if (ticks.size < limit) {
            return null
        }

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

private fun HashMap<UUID, Int>.add(playerId: UUID): Int {
    val next = (this[playerId] ?: 0) + 1
    this[playerId] = next
    return next
}

private fun ObservedMovementFrame.flag(
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

private fun confidence(value: Double, limit: Double, base: Int): Int {
    val over = ((value / limit) - 1.0).coerceAtLeast(0.0)
    return (base + over * 100.0).roundToInt().coerceIn(base, 99)
}

private fun Double.format() = "%.3f".format(this)

private fun Vec3.horizontalLength() = kotlin.math.sqrt(x * x + z * z)
