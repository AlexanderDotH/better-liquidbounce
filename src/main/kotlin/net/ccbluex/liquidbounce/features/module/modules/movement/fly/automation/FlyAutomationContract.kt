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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation

import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.movement.getDirectionalInputForDegrees
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2

/** Describes which axes the selected Fly mode can safely steer through its own mechanics. */
internal data class FlyAutomationCapabilities(
    val horizontal: Boolean,
    val ascend: Boolean,
    val descend: Boolean,
    val landing: Boolean,
    val kind: FlyAutomationKind,
    val resource: String? = null,
    val reliableSpeed: Boolean = false,
)

internal enum class FlyAutomationKind {
    CONTINUOUS,
    BURST,
}

internal sealed interface FlyAutomationReadiness {
    data object Ready : FlyAutomationReadiness
    data class Arming(val reason: String) : FlyAutomationReadiness
    data class Unavailable(val reason: String) : FlyAutomationReadiness
}

internal data class FlyAutomationEnd(val reason: String)

/** Implemented by every selectable top-level Fly mode. */
internal interface FlyAutomationProfile {
    val automationCapabilities: FlyAutomationCapabilities

    fun automationReadiness(): FlyAutomationReadiness

    fun consumeAutomaticEnd(): FlyAutomationEnd? = null
}

/** A route controller's desired motion in world coordinates. Magnitude does not change Fly speeds. */
internal data class FlySteeringIntent(
    val worldDirection: Vec3,
    val sprint: Boolean = false,
) {
    init {
        require(worldDirection.x.isFinite() && worldDirection.y.isFinite() && worldDirection.z.isFinite()) {
            "Fly steering direction must be finite"
        }
    }
}

/** Pure input conversion kept separate from the live lease for focused collision/movement tests. */
internal object FlyAutomationInputResolver {

    fun directional(intent: FlySteeringIntent?, physical: DirectionalInput, playerYaw: Float): DirectionalInput {
        val desiredYaw = desiredYaw(intent, physical) ?: return physical
        val relativeYaw = Mth.wrapDegrees(desiredYaw - playerYaw)
        return getDirectionalInputForDegrees(DirectionalInput.NONE, relativeYaw)
    }

    fun jump(intent: FlySteeringIntent?, physical: Boolean): Boolean {
        return physical || intent?.worldDirection?.y?.let { it > VERTICAL_EPSILON } == true
    }

    fun sneak(intent: FlySteeringIntent?, physical: Boolean): Boolean {
        return physical || intent?.worldDirection?.y?.let { it < -VERTICAL_EPSILON } == true
    }

    fun sprint(intent: FlySteeringIntent?, physical: Boolean): Boolean = physical || intent?.sprint == true

    fun desiredYaw(intent: FlySteeringIntent?, physical: DirectionalInput): Float? {
        if (physical.isMoving) return null
        val direction = intent?.worldDirection ?: return null
        if (direction.x * direction.x + direction.z * direction.z <= HORIZONTAL_EPSILON_SQUARED) return null

        return Mth.wrapDegrees(Math.toDegrees(atan2(-direction.x, direction.z)).toFloat())
    }

    private const val VERTICAL_EPSILON = 1.0E-6
    private const val HORIZONTAL_EPSILON_SQUARED = 1.0E-12
}
