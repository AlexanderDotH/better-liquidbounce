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


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.minecraft.world.phys.Vec3

internal enum class MaceKillServerFallSafetyBlockReason {
    NON_FINITE_INPUT,
    INVALID_DISTANCE,
    INVALID_OUTBOUND_STEP_COUNT,
    ZERO_MOVEMENT,
    INVALID_GROUND_PROFILE,
    UNEXPECTED_NET_MOVEMENT,
}

internal sealed interface MaceKillServerFallSafetyPlanResult {
    data class Ready(val plan: MaceKillServerFallSafetyPlan) : MaceKillServerFallSafetyPlanResult
    data class Blocked(val reason: MaceKillServerFallSafetyBlockReason) : MaceKillServerFallSafetyPlanResult
}

internal data class MaceKillServerFallSafetyStep(
    val movement: Vec3,
    val groundExactPacket: Boolean,
)

internal data class MaceKillServerFallSafetyPlan private constructor(
    val steps: List<MaceKillServerFallSafetyStep>,
    val outboundStepCount: Int,
    val initialFallDistance: Double,
    val safeFallDistance: Double,
) {
    val netMovement: Vec3 = steps.fold(Vec3.ZERO) { total, step -> total.add(step.movement) }

    companion object {
        fun create(
            outboundMovements: List<Vec3>,
            initialFallDistance: Double,
            safeFallDistance: Double,
            groundedSteps: List<Boolean>? = null,
        ): MaceKillServerFallSafetyPlanResult {
            val roundTrip = outboundMovements + outboundMovements.asReversed().map { it.scale(-1.0) }
            return createForMovements(
                movements = roundTrip,
                outboundStepCount = outboundMovements.size,
                initialFallDistance = initialFallDistance,
                safeFallDistance = safeFallDistance,
                groundedSteps = groundedSteps ?: List(roundTrip.size) { false },
                expectedNetMovement = Vec3.ZERO,
            )
        }

        fun createForMovements(
            movements: List<Vec3>,
            outboundStepCount: Int,
            initialFallDistance: Double,
            safeFallDistance: Double,
            groundedSteps: List<Boolean> = List(movements.size) { false },
            expectedNetMovement: Vec3? = null,
        ): MaceKillServerFallSafetyPlanResult {
            validateInputs(movements, initialFallDistance, safeFallDistance, groundedSteps, expectedNetMovement)?.let {
                return MaceKillServerFallSafetyPlanResult.Blocked(it)
            }
            if (outboundStepCount !in 0..movements.size) {
                return MaceKillServerFallSafetyPlanResult.Blocked(
                    MaceKillServerFallSafetyBlockReason.INVALID_OUTBOUND_STEP_COUNT,
                )
            }
            val plan = MaceKillServerFallSafetyPlan(
                steps = movements.mapIndexed { index, movement ->
                    MaceKillServerFallSafetyStep(movement, groundedSteps[index])
                },
                outboundStepCount = outboundStepCount,
                initialFallDistance = initialFallDistance,
                safeFallDistance = safeFallDistance,
            )
            if (expectedNetMovement != null &&
                plan.netMovement.distanceToSqr(expectedNetMovement) > MACE_KILL_FALL_SAFETY_NET_EPSILON_SQUARED
            ) {
                return MaceKillServerFallSafetyPlanResult.Blocked(
                    MaceKillServerFallSafetyBlockReason.UNEXPECTED_NET_MOVEMENT,
                )
            }
            return MaceKillServerFallSafetyPlanResult.Ready(plan)
        }

        private fun validateInputs(
            movements: List<Vec3>,
            initialFallDistance: Double,
            safeFallDistance: Double,
            groundedSteps: List<Boolean>,
            expectedNetMovement: Vec3?,
        ): MaceKillServerFallSafetyBlockReason? = when {
            !initialFallDistance.isFinite() || !safeFallDistance.isFinite() ||
                movements.any { !it.hasFiniteMaceKillFallSafetyCoordinates() } ||
                expectedNetMovement?.hasFiniteMaceKillFallSafetyCoordinates() == false ->
                MaceKillServerFallSafetyBlockReason.NON_FINITE_INPUT
            initialFallDistance < 0.0 || safeFallDistance < 0.0 ->
                MaceKillServerFallSafetyBlockReason.INVALID_DISTANCE
            movements.any { it.lengthSqr() == 0.0 } -> MaceKillServerFallSafetyBlockReason.ZERO_MOVEMENT
            groundedSteps.size != movements.size -> MaceKillServerFallSafetyBlockReason.INVALID_GROUND_PROFILE
            else -> null
        }
    }
}

private fun Vec3.hasFiniteMaceKillFallSafetyCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
private const val MACE_KILL_FALL_SAFETY_NET_EPSILON_SQUARED = 1.0E-12
