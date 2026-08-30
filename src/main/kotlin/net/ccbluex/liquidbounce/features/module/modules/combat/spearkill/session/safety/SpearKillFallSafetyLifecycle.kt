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



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.minecraft.world.phys.Vec3

/** Owns delivery-confirmed fall state for one fully preflighted Packet route. */
internal class SpearKillFallSafetyLifecycle {

    private var session: ActiveSpearKillFallSafetySession? = null

    val active: Boolean
        get() = session != null

    val confirmedMovementCount: Int
        get() = session?.confirmedMovementCount ?: 0

    val confirmedFallDistance: Double
        get() = session?.confirmedFallState?.fallDistance ?: 0.0

    fun begin(plan: SpearKillServerFallSafetyPlan) {
        session = ActiveSpearKillFallSafetySession(plan)
    }

    fun replan(plan: SpearKillServerFallSafetyPlan) {
        begin(plan)
    }

    fun invalidate() {
        session = null
    }

    fun gatePendingMovement(movement: Vec3): SpearKillFallSafetyPendingStepGate =
        gatePendingMovement(movement, shouldGroundPendingMovement(movement))

    fun gatePendingMovement(
        movement: Vec3,
        physicallyNearGround: Boolean,
    ): SpearKillFallSafetyPendingStepGate {
        val current = session ?: return SpearKillFallSafetyPendingStepGate.BLOCKED
        val step = current.plan.steps.getOrNull(current.confirmedMovementCount)
            ?: return SpearKillFallSafetyPendingStepGate.BLOCKED
        if (!sameMovement(step.movement, movement)) return SpearKillFallSafetyPendingStepGate.BLOCKED
        if (step.groundExactPacket != physicallyNearGround) {
            return SpearKillFallSafetyPendingStepGate.BLOCKED
        }
        return SpearKillFallSafetyPendingStepGate.CLEAR
    }

    fun shouldGroundPendingMovement(movement: Vec3): Boolean {
        val current = session ?: return false
        val step = current.plan.steps.getOrNull(current.confirmedMovementCount) ?: return false
        return sameMovement(step.movement, movement) && step.groundExactPacket
    }

    /** Reuses Direct's delivered ground-spoof credit without consuming the pending route step. */
    fun shouldStabilizePendingMovement(
        movement: Vec3,
        physicalFallDanger: Boolean,
    ): Boolean {
        val current = session ?: return false
        val step = current.plan.steps.getOrNull(current.confirmedMovementCount) ?: return false
        if (!sameMovement(step.movement, movement)) return false

        return shouldStabilizeSpearKillVirtualFall(
            groundingDelivered = current.stabilizationDeliveredForPendingMovement,
            physicalFallDanger = physicalFallDanger,
            state = current.confirmedFallState,
            nextMovement = movement,
            safeFallDistance = current.plan.safeFallDistance,
        )
    }

    fun confirmStabilization(delivered: Boolean): Boolean {
        val current = session ?: return false
        val pendingStep = current.plan.steps.getOrNull(current.confirmedMovementCount)
        if (!delivered || pendingStep == null) return false

        current.confirmedFallState.confirmGrounded()
        current.stabilizationDeliveredForPendingMovement = true
        return true
    }

    fun confirmGrounding(delivered: Boolean): Boolean {
        val current = session ?: return false
        if (!delivered) {
            current.awaitingFinalGrounding = false
            return false
        }
        if (!current.awaitingFinalGrounding) return false
        current.confirmedFallState.confirmGrounded()
        current.awaitingFinalGrounding = false
        current.finalGroundingConfirmed = true
        return true
    }

    fun confirmMovement(
        movement: Vec3,
        delivered: Boolean,
        exactPacketGrounded: Boolean = false,
    ): Boolean {
        if (!delivered || gatePendingMovement(
                movement,
                physicallyNearGround = exactPacketGrounded,
            ) != SpearKillFallSafetyPendingStepGate.CLEAR
        ) {
            return false
        }
        val current = session ?: return false
        val step = current.plan.steps.getOrNull(current.confirmedMovementCount) ?: return false
        if (step.groundExactPacket != exactPacketGrounded) return false
        if (exactPacketGrounded) {
            current.confirmedFallState.confirmGrounded()
        } else {
            current.confirmedFallState.confirmMovement(movement)
        }
        current.stabilizationDeliveredForPendingMovement = false
        current.lastConfirmedPacketGrounded = exactPacketGrounded
        current.confirmedMovementCount++
        return true
    }

    fun finish(
        finalPositionKnown: Boolean,
        connectionOpen: Boolean,
        physicallyNearGround: Boolean = false,
    ): SpearKillFallSafetyFinishAction {
        val current = session ?: return SpearKillFallSafetyFinishAction.NONE
        if (current.confirmedMovementCount != current.plan.steps.size) {
            return SpearKillFallSafetyFinishAction.NONE
        }
        if (current.finalGroundingConfirmed) {
            invalidate()
            return SpearKillFallSafetyFinishAction(
                resetLocalFallDistance = true,
                sendGroundedPacket = false,
            )
        }
        if (current.lastConfirmedPacketGrounded) {
            invalidate()
            return SpearKillFallSafetyFinishAction(
                resetLocalFallDistance = true,
                sendGroundedPacket = false,
            )
        }
        if (!finalPositionKnown || !connectionOpen || !physicallyNearGround) {
            invalidate()
            return SpearKillFallSafetyFinishAction.NONE
        }
        if (current.awaitingFinalGrounding) return SpearKillFallSafetyFinishAction.NONE

        current.awaitingFinalGrounding = true
        return SpearKillFallSafetyFinishAction(
            resetLocalFallDistance = false,
            sendGroundedPacket = true,
        )
    }
}

private fun sameMovement(first: Vec3, second: Vec3): Boolean =
    first.distanceToSqr(second) <= SPEAR_KILL_FALL_SAFETY_MOVEMENT_EPSILON_SQUARED

private const val SPEAR_KILL_FALL_SAFETY_MOVEMENT_EPSILON_SQUARED = 1.0E-12
