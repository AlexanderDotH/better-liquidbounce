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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield

import net.minecraft.world.item.component.BlocksAttacks
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The part of [BlocksAttacks] which can be evaluated before starting item use.
 * Values remain data-driven so custom shields and future vanilla changes retain
 * their own blocking cone and warm-up delay.
 */
data class SpearShieldPolicy(
    val horizontalBlockingAngleDegrees: Float,
    val blockDelayTicks: Int,
    val releaseDelayTicks: Int,
) {
    init {
        require(horizontalBlockingAngleDegrees.isFinite() && horizontalBlockingAngleDegrees >= 0F)
        require(blockDelayTicks >= 0)
        require(releaseDelayTicks >= 0)
    }

    fun isAligned(
        serverYawDegrees: Float,
        attackerDeltaX: Double,
        attackerDeltaZ: Double,
    ): Boolean {
        val attackerDistance = hypot(attackerDeltaX, attackerDeltaZ)
        if (!attackerDistance.isFinite() || attackerDistance <= DIRECTION_EPSILON) {
            return false
        }

        val yawRadians = Math.toRadians(serverYawDegrees.toDouble())
        val lookX = -sin(yawRadians)
        val lookZ = cos(yawRadians)
        val dot = (lookX * attackerDeltaX + lookZ * attackerDeltaZ) / attackerDistance
        val angleDegrees = Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0)))

        return angleDegrees <= horizontalBlockingAngleDegrees + ANGLE_EPSILON
    }

    fun isReady(useTicks: Int): Boolean = useTicks >= blockDelayTicks

    companion object {
        private const val DIRECTION_EPSILON = 1E-7
        private const val ANGLE_EPSILON = 1E-5

        fun from(
            component: BlocksAttacks,
            releaseDelayTicks: Int,
        ): SpearShieldPolicy? {
            val horizontalBlockingAngle = component.damageReductions()
                .maxOfOrNull(BlocksAttacks.DamageReduction::horizontalBlockingAngle)
                ?: return null

            return SpearShieldPolicy(
                horizontalBlockingAngleDegrees = horizontalBlockingAngle,
                blockDelayTicks = component.blockDelayTicks(),
                releaseDelayTicks = releaseDelayTicks,
            )
        }
    }
}
