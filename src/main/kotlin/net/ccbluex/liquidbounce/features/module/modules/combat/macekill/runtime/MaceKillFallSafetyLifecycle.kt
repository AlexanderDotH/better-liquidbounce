/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.minecraft.world.phys.Vec3
import kotlin.math.max

internal enum class MaceKillFallSafetyPendingStepGate {
    CLEAR,
    BLOCKED,
}

internal data class MaceKillFallSafetyFinishAction(
    val resetLocalFallDistance: Boolean,
    val sendGroundedPacket: Boolean,
) {
    companion object {
        val NONE = MaceKillFallSafetyFinishAction(false, false)
    }
}

internal class MaceKillFallSafetyLifecycle {
    private var session: ActiveMaceKillFallSafetySession? = null

    val active: Boolean
        get() = session != null

    val confirmedMovementCount: Int
        get() = session?.confirmedMovementCount ?: 0

    val confirmedFallDistance: Double
        get() = session?.confirmedFallState?.fallDistance ?: 0.0

    fun begin(plan: MaceKillServerFallSafetyPlan) {
        session = ActiveMaceKillFallSafetySession(plan)
    }

    fun replan(plan: MaceKillServerFallSafetyPlan) = begin(plan)

    fun invalidate() {
        session = null
    }

    fun gatePendingMovement(
        movement: Vec3,
        physicallyNearGround: Boolean = shouldGroundPendingMovement(movement),
    ): MaceKillFallSafetyPendingStepGate {
        val current = session ?: return MaceKillFallSafetyPendingStepGate.BLOCKED
        val step = current.plan.steps.getOrNull(current.confirmedMovementCount)
            ?: return MaceKillFallSafetyPendingStepGate.BLOCKED
        if (!sameMaceKillMovement(step.movement, movement) || step.groundExactPacket != physicallyNearGround) {
            return MaceKillFallSafetyPendingStepGate.BLOCKED
        }
        return MaceKillFallSafetyPendingStepGate.CLEAR
    }

    private fun shouldGroundPendingMovement(movement: Vec3): Boolean {
        val current = session ?: return false
        val step = current.plan.steps.getOrNull(current.confirmedMovementCount) ?: return false
        return sameMaceKillMovement(step.movement, movement) && step.groundExactPacket
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
        if (!delivered || gatePendingMovement(movement, exactPacketGrounded) != MaceKillFallSafetyPendingStepGate.CLEAR) {
            return false
        }
        val current = session ?: return false
        if (exactPacketGrounded) {
            current.confirmedFallState.confirmGrounded()
        } else {
            current.confirmedFallState.confirmMovement(movement)
        }
        current.lastConfirmedPacketGrounded = exactPacketGrounded
        current.confirmedMovementCount++
        return true
    }

    fun finish(
        finalPositionKnown: Boolean,
        connectionOpen: Boolean,
        physicallyNearGround: Boolean = false,
    ): MaceKillFallSafetyFinishAction {
        val current = session ?: return MaceKillFallSafetyFinishAction.NONE
        if (current.confirmedMovementCount != current.plan.steps.size) return MaceKillFallSafetyFinishAction.NONE
        if (current.finalGroundingConfirmed || current.lastConfirmedPacketGrounded) {
            invalidate()
            return MaceKillFallSafetyFinishAction(resetLocalFallDistance = true, sendGroundedPacket = false)
        }
        if (!finalPositionKnown || !connectionOpen || !physicallyNearGround) {
            invalidate()
            return MaceKillFallSafetyFinishAction.NONE
        }
        if (current.awaitingFinalGrounding) return MaceKillFallSafetyFinishAction.NONE
        current.awaitingFinalGrounding = true
        return MaceKillFallSafetyFinishAction(resetLocalFallDistance = false, sendGroundedPacket = true)
    }
}

private class ActiveMaceKillFallSafetySession(val plan: MaceKillServerFallSafetyPlan) {
    val confirmedFallState = MaceKillVirtualFallState().apply { begin(plan.initialFallDistance) }
    var confirmedMovementCount = 0
    var awaitingFinalGrounding = false
    var finalGroundingConfirmed = false
    var lastConfirmedPacketGrounded = false
}

private class MaceKillVirtualFallState {
    var fallDistance = 0.0
        private set

    fun begin(initialFallDistance: Double) {
        require(initialFallDistance.isFinite())
        fallDistance = max(initialFallDistance, 0.0)
    }

    fun confirmMovement(movement: Vec3) {
        require(movement.x.isFinite() && movement.y.isFinite() && movement.z.isFinite())
        when {
            movement.y > 0.0 -> confirmGrounded()
            movement.y < 0.0 -> fallDistance -= movement.y
        }
    }

    fun confirmGrounded() {
        fallDistance = 0.0
    }
}

private fun sameMaceKillMovement(first: Vec3, second: Vec3): Boolean =
    first.distanceToSqr(second) <= MACE_KILL_FALL_SAFETY_MOVEMENT_EPSILON_SQUARED

private const val MACE_KILL_FALL_SAFETY_MOVEMENT_EPSILON_SQUARED = 1.0E-12
