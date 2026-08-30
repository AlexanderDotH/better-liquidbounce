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



import net.minecraft.world.phys.Vec3
import kotlin.math.max

/**
 * Delivery-confirmed fall state for SpearKill's virtual packet position.
 *
 * The physical player's fall distance cannot describe a server-side A* route. This state is
 * therefore advanced only after the exact SpearKill movement packet passed the packet pipeline.
 */
internal class SpearKillVirtualFallState {

    var fallDistance: Double = 0.0
        private set

    fun begin(initialFallDistance: Double) {
        require(initialFallDistance.isFinite()) { "Initial fall distance must be finite" }
        fallDistance = max(initialFallDistance, 0.0)
    }

    fun confirmMovement(movement: Vec3) {
        require(movement.isFinite()) { "Movement must be finite" }
        when {
            movement.y > 0.0 -> reset()
            movement.y < 0.0 -> fallDistance -= movement.y
        }
    }

    fun requiresGroundingBefore(nextMovement: Vec3, safeFallDistance: Double): Boolean {
        require(nextMovement.isFinite()) { "Movement must be finite" }
        require(safeFallDistance.isFinite()) { "Safe fall distance must be finite" }

        val safeDistance = max(safeFallDistance, 0.0)
        return when {
            nextMovement.y > 0.0 -> false
            nextMovement.y < 0.0 -> fallDistance - nextMovement.y > safeDistance
            else -> fallDistance > 0.0
        }
    }

    fun confirmGrounded() {
        reset()
    }

    fun reset() {
        fallDistance = 0.0
    }
}

internal fun spearKillSafeVirtualVerticalStep(safeFallDistance: Double): Double {
    require(safeFallDistance.isFinite()) { "Safe fall distance must be finite" }
    return (safeFallDistance - SPEAR_KILL_VIRTUAL_FALL_SAFETY_MARGIN).coerceAtLeast(0.0)
}

internal fun shouldStabilizeSpearKillVirtualFall(
    groundingDelivered: Boolean,
    physicalFallDanger: Boolean,
    state: SpearKillVirtualFallState,
    nextMovement: Vec3,
    safeFallDistance: Double,
): Boolean = !groundingDelivered && (
    physicalFallDanger || state.requiresGroundingBefore(nextMovement, safeFallDistance)
)

private const val SPEAR_KILL_VIRTUAL_FALL_SAFETY_MARGIN = 0.05
