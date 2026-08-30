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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.minecraft.world.phys.Vec3

internal enum class SpearKillServerFallSafetyBlockReason {
    NON_FINITE_INPUT,
    INVALID_DISTANCE,
    INVALID_OUTBOUND_STEP_COUNT,
    ZERO_MOVEMENT,
    INVALID_GROUND_PROFILE,
    UNEXPECTED_NET_MOVEMENT,
}

internal sealed interface SpearKillServerFallSafetyPlanResult {
    data class Ready(val plan: SpearKillServerFallSafetyPlan) : SpearKillServerFallSafetyPlanResult
    data class Blocked(
        val reason: SpearKillServerFallSafetyBlockReason,
    ) : SpearKillServerFallSafetyPlanResult
}

internal data class SpearKillServerFallSafetyStep(
    val movement: Vec3,
    val groundExactPacket: Boolean,
)

/**
 * Immutable preflight of the complete server-visible outbound and exact inverse movement route.
 *
 * Ground flags are supplied by the live collision probe. Fall distance is tracked for diagnostics,
 * but it never fabricates an airborne ground packet or limits a full-speed downward movement.
 */
internal data class SpearKillServerFallSafetyPlan private constructor(
    val steps: List<SpearKillServerFallSafetyStep>,
    val outboundStepCount: Int,
    val initialFallDistance: Double,
    val safeFallDistance: Double,
) {
    val finalGroundingRequired: Boolean = steps.lastOrNull()?.groundExactPacket != true
    val netMovement: Vec3 = steps.fold(Vec3.ZERO) { total, step -> total.add(step.movement) }

    fun groundsExactPacket(stepIndex: Int): Boolean =
        steps.getOrNull(stepIndex)?.groundExactPacket == true

    companion object {
        fun create(
            outboundMovements: List<Vec3>,
            initialFallDistance: Double,
            safeFallDistance: Double,
            groundedSteps: List<Boolean>? = null,
        ): SpearKillServerFallSafetyPlanResult {
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

        /** Preflights an already assembled future queue without synthesizing another return leg. */
        fun createForMovements(
            movements: List<Vec3>,
            outboundStepCount: Int,
            initialFallDistance: Double,
            safeFallDistance: Double,
            groundedSteps: List<Boolean> = List(movements.size) { false },
            expectedNetMovement: Vec3? = null,
        ): SpearKillServerFallSafetyPlanResult {
            validateInputs(
                movements,
                initialFallDistance,
                safeFallDistance,
                groundedSteps,
                expectedNetMovement,
            )?.let {
                return SpearKillServerFallSafetyPlanResult.Blocked(it)
            }
            if (outboundStepCount !in 0..movements.size) {
                return SpearKillServerFallSafetyPlanResult.Blocked(
                    SpearKillServerFallSafetyBlockReason.INVALID_OUTBOUND_STEP_COUNT,
                )
            }
            val result = buildPlan(
                movements,
                outboundStepCount,
                initialFallDistance,
                safeFallDistance,
                groundedSteps,
            )
            return validateNetMovement(result, expectedNetMovement)
        }

        private fun validateInputs(
            movements: List<Vec3>,
            initialFallDistance: Double,
            safeFallDistance: Double,
            groundedSteps: List<Boolean>,
            expectedNetMovement: Vec3?,
        ): SpearKillServerFallSafetyBlockReason? {
            if (!initialFallDistance.isFinite() || !safeFallDistance.isFinite() ||
                movements.any { !it.isFinite() } || expectedNetMovement?.isFinite() == false
            ) {
                return SpearKillServerFallSafetyBlockReason.NON_FINITE_INPUT
            }
            if (initialFallDistance < 0.0 || safeFallDistance < 0.0) {
                return SpearKillServerFallSafetyBlockReason.INVALID_DISTANCE
            }
            if (movements.any { it.lengthSqr() == 0.0 }) {
                return SpearKillServerFallSafetyBlockReason.ZERO_MOVEMENT
            }
            if (groundedSteps.size != movements.size) {
                return SpearKillServerFallSafetyBlockReason.INVALID_GROUND_PROFILE
            }
            return null
        }

        private fun validateNetMovement(
            result: SpearKillServerFallSafetyPlanResult,
            expectedNetMovement: Vec3?,
        ): SpearKillServerFallSafetyPlanResult {
            val ready = result as? SpearKillServerFallSafetyPlanResult.Ready ?: return result
            if (!ready.plan.netMovement.isFinite()) {
                return SpearKillServerFallSafetyPlanResult.Blocked(
                    SpearKillServerFallSafetyBlockReason.NON_FINITE_INPUT,
                )
            }
            if (expectedNetMovement != null && ready.plan.netMovement.distanceToSqr(expectedNetMovement) >
                SPEAR_KILL_FALL_SAFETY_NET_EPSILON_SQUARED
            ) {
                return SpearKillServerFallSafetyPlanResult.Blocked(
                    SpearKillServerFallSafetyBlockReason.UNEXPECTED_NET_MOVEMENT,
                )
            }
            return ready
        }

        private fun buildPlan(
            movements: List<Vec3>,
            outboundStepCount: Int,
            initialFallDistance: Double,
            safeFallDistance: Double,
            groundedSteps: List<Boolean>,
        ): SpearKillServerFallSafetyPlanResult {
            val steps = ArrayList<SpearKillServerFallSafetyStep>(movements.size)
            for ((index, movement) in movements.withIndex()) {
                steps += SpearKillServerFallSafetyStep(movement, groundedSteps[index])
            }

            return SpearKillServerFallSafetyPlanResult.Ready(
                SpearKillServerFallSafetyPlan(
                    steps = steps.toList(),
                    outboundStepCount = outboundStepCount,
                    initialFallDistance = initialFallDistance,
                    safeFallDistance = safeFallDistance,
                ),
            )
        }
    }
}

private const val SPEAR_KILL_FALL_SAFETY_NET_EPSILON_SQUARED = 1.0E-12
