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

class GrimObserverEngine(
    private val checks: List<ObserverCheck> = ObserverCheckRegistry.supportedChecks(),
    private val buffer: ObserverViolationBuffer = ObserverViolationBuffer(),
) {

    fun handleMovement(
        frame: ObservedMovementFrame,
        enabledChecks: Set<PlayerCheatCheck>,
        strictness: DetectorStrictness,
        minConfidence: Int,
        cooldownMs: Long,
        nowMs: Long,
    ): List<DetectionNotice> {
        var notices: MutableList<DetectionNotice>? = null

        for (check in checks) {
            if (check.category !in enabledChecks) {
                continue
            }

            val flag = check.handleMovement(frame, strictness)
            if (flag == null) {
                buffer.reward(check.rewardKey(frame))
                continue
            }

            val notice = buffer.submit(flag, strictness, minConfidence, cooldownMs, nowMs) ?: continue
            val output = notices ?: mutableListOf<DetectionNotice>().also { notices = it }
            output += notice
        }

        return notices.orEmpty()
    }

    fun handleAction(
        frame: ObservedActionFrame,
        enabledChecks: Set<PlayerCheatCheck>,
        strictness: DetectorStrictness,
        minConfidence: Int,
        cooldownMs: Long,
        nowMs: Long,
    ): List<DetectionNotice> {
        var notices: MutableList<DetectionNotice>? = null

        for (check in checks) {
            if (check.category !in enabledChecks) {
                continue
            }

            val flag = check.handleAction(frame, strictness) ?: continue
            val notice = buffer.submit(flag, strictness, minConfidence, cooldownMs, nowMs) ?: continue
            val output = notices ?: mutableListOf<DetectionNotice>().also { notices = it }
            output += notice
        }

        return notices.orEmpty()
    }

    fun reset(playerId: java.util.UUID? = null) {
        buffer.reset(playerId)

        if (playerId == null) {
            return checks.forEach { it.resetAll() }
        }

        checks.forEach { it.reset(playerId) }
    }
}

object ObserverCheckRegistry {

    fun supportedChecks(): List<ObserverCheck> = listOf(
        ObservedMovementPredictionCheck(),
        ObservedFlightCheck(),
        ObservedGroundSpoofSymptomsCheck(),
        ObservedAntiKnockbackCheck(),
        ObservedReachCheck(),
        ObservedFarPlaceCheck(),
        ObservedFastBreakCheck(),
    )

    fun unsupportedChecks(): List<UnsupportedGrimCheck> = listOf(
        UnsupportedGrimCheck("BadPacketsA-Z", "grim.badpackets.*", "Requires private client packet stream"),
        UnsupportedGrimCheck("PacketOrderA-P", "grim.packetorder.*", "Requires private packet ordering"),
        UnsupportedGrimCheck("Timer", "grim.timer.timer", "Requires client movement packet cadence"),
        UnsupportedGrimCheck("TransactionOrder", "grim.misc.transaction_order", "Requires transaction round-trips"),
        UnsupportedGrimCheck(
            "FabricatedPlace",
            "grim.scaffolding.fabricated_place",
            "Requires block-place cursor packet",
        ),
        UnsupportedGrimCheck("RotationPlace", "grim.scaffolding.rotation_place", "Requires private look packet"),
        UnsupportedGrimCheck("Inventory compensation", null, "Requires private inventory packets"),
        UnsupportedGrimCheck("NoSlow/Sprint exact checks", "grim.sprint.*", "Requires private input/action packets"),
    )
}

data class UnsupportedGrimCheck(
    val name: String,
    val sourceStableKey: String?,
    val reason: String,
) {
    val capability: DetectionCapability = DetectionCapability.UNSUPPORTED_NO_SIGNAL
}
