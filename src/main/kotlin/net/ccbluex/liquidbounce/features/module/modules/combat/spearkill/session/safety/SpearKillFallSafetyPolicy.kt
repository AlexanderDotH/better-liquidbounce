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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
internal data class SpearKillFallSafetyFinishAction(
    val resetLocalFallDistance: Boolean,
    val sendGroundedPacket: Boolean,
) {
    companion object {
        val NONE = SpearKillFallSafetyFinishAction(
            resetLocalFallDistance = false,
            sendGroundedPacket = false,
        )
    }
}

internal enum class SpearKillFallSafetyPendingStepGate {
    CLEAR,
    BLOCKED,
}

internal enum class SpearKillFallSafetyPendingStepAction {
    DELIVER,
    STABILIZE,
    BLOCKED,
}

internal fun resolveSpearKillFallSafetyPendingStepAction(
    gate: SpearKillFallSafetyPendingStepGate,
    stabilizationRequired: Boolean,
): SpearKillFallSafetyPendingStepAction = when {
    gate == SpearKillFallSafetyPendingStepGate.BLOCKED -> SpearKillFallSafetyPendingStepAction.BLOCKED
    stabilizationRequired -> SpearKillFallSafetyPendingStepAction.STABILIZE
    else -> SpearKillFallSafetyPendingStepAction.DELIVER
}

/**
 * Instant's airborne ground bit resets Vanilla fall state but is not collision evidence. Keeping
 * those concepts separate preserves the preflighted route plan while the wire packet stays safe.
 */
internal fun resolveSpearKillFallSafetyPacketGrounded(
    packetGrounded: Boolean,
    instantGroundSpoof: Boolean,
    physicallyNearGround: Boolean,
): Boolean = if (instantGroundSpoof) physicallyNearGround else packetGrounded

internal class ActiveSpearKillFallSafetySession(
    val plan: SpearKillServerFallSafetyPlan,
) {
    val confirmedFallState = SpearKillVirtualFallState().apply {
        begin(plan.initialFallDistance)
    }
    var confirmedMovementCount = 0
    var awaitingFinalGrounding = false
    var finalGroundingConfirmed = false
    var lastConfirmedPacketGrounded = false
    var stabilizationDeliveredForPendingMovement = false
}

/** Owns delivery-confirmed fall state for one fully preflighted Packet route. */
