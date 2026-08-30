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

import net.ccbluex.liquidbounce.utils.math.distanceToSqr
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

class ObservedAntiKnockbackCheck : ObserverCheck {
    override val category = PlayerCheatCheck.VELOCITY
    override val name = "ObservedAntiKnockback"
    override val sourceStableKey = "grim.velocity.anti_knockback"
    override val capability = DetectionCapability.DEGRADED_OBSERVER

    private data class PendingVelocity(val tick: Int, val vector: Vec3, var ignoredTicks: Int = 0)
    private val pendingVelocities = hashMapOf<UUID, PendingVelocity>()

    override fun handleAction(frame: ObservedActionFrame, strictness: DetectorStrictness): DetectionFlag? {
        if (frame.type != ObservedActionType.VELOCITY) return null
        val vector = frame.vector ?: return null
        if (vector.horizontalLength() < 0.08 && abs(vector.y) < 0.08) return null
        pendingVelocities[frame.playerId] = PendingVelocity(frame.tick, vector)
        return null
    }

    override fun handleMovement(frame: ObservedMovementFrame, strictness: DetectorStrictness): DetectionFlag? {
        val pending = pendingVelocities[frame.playerId] ?: return null
        val age = frame.tick - pending.tick
        val expectedHorizontal = pending.vector.horizontalLength()
        val observedHorizontal = frame.delta.horizontalLength()
        val verticalResponse = if (pending.vector.y > 0.0) frame.delta.y else 0.0
        val tookVelocity = observedHorizontal > expectedHorizontal * 0.22 || verticalResponse > pending.vector.y * 0.20
        return when {
            age > 12 || frame.teleportLike || tookVelocity -> {
                pendingVelocities.remove(frame.playerId)
                null
            }
            age <= 0 -> null
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

    private fun PendingVelocity.bumpIgnoredTicks(): Int = ++ignoredTicks
}

class ObservedReachCheck : ObserverCheck {
    override val category = PlayerCheatCheck.REACH
    override val name = "ObservedReach"
    override val sourceStableKey = "grim.combat.reach"
    override val capability = DetectionCapability.DEGRADED_OBSERVER

    override fun handleAction(frame: ObservedActionFrame, strictness: DetectorStrictness): DetectionFlag? {
        if (frame.type != ObservedActionType.DAMAGE) return null
        val targetBox = frame.targetBoundingBox ?: return null
        val distance = sqrt(targetBox.distanceToSqr(frame.eyePosition))
        if (distance <= strictness.reachLimit) return null
        return DetectionFlag(
            playerId = frame.playerId,
            playerName = frame.playerName,
            check = category,
            checkName = name,
            sourceStableKey = sourceStableKey,
            confidence = confidence(distance, strictness.reachLimit, base = 74),
            severity = DetectionSeverity.ERROR,
            verbose = "distance=${distance.format()} limit=${strictness.reachLimit.format()} " +
                "target=${frame.targetName ?: "unknown"}",
            observedAtTick = frame.tick,
        )
    }
}

internal fun Vec3.horizontalLength() = kotlin.math.sqrt(x * x + z * z)
