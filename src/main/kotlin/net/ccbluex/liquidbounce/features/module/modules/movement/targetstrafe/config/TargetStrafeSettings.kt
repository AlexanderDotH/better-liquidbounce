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
package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.config

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafeEnvironment
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafePlannerConfiguration
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafePointValidation
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.wouldFallIntoVoid
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

internal object TargetStrafePlannerSettings : ToggleableValueGroup(name = "Planner", enabled = true) {
    val controlDirection by boolean("ControlDirection", true)

    init {
        TargetStrafePointValidation.bind(Validation::validatePoint)
        TargetStrafePlannerConfiguration.bind(
            controlDirectionProvider = { controlDirection },
            adaptiveRangeEnabledProvider = { AdaptiveRange.enabled },
            adaptiveRangeStepProvider = { AdaptiveRange.rangeStep },
            adaptiveRangeMaximumProvider = { AdaptiveRange.maxRange },
        )
        tree(Validation)
        tree(AdaptiveRange)
    }

    object Validation : ToggleableValueGroup(TargetStrafePlannerSettings, "Validation", true) {
        init {
            tree(EdgeCheck)
            tree(VoidCheck)
        }

        object EdgeCheck : ToggleableValueGroup(Validation, "EdgeCheck", true) {
            val maxFallHeight by float("MaxFallHeight", 1.2f, 0.1f..4f)
        }

        object VoidCheck : ToggleableValueGroup(Validation, "VoidCheck", true) {
            val safetyExpand by float("SafetyExpand", 0.1f, 0.0f..5f)
        }

        fun validatePoint(point: Vec3): Boolean {
            if (!validateCollision(point)) return false
            if (!enabled) return true
            if (EdgeCheck.enabled && isCloseToFall(point)) return false
            if (VoidCheck.enabled && player.wouldFallIntoVoid(
                    point,
                    safetyExpand = VoidCheck.safetyExpand.toDouble(),
                )) {
                return false
            }
            return true
        }

        private fun validateCollision(point: Vec3, expand: Double = 0.0): Boolean {
            val hitbox = targetStrafeStandingCollisionBox(point, player.getDimensions(Pose.STANDING))
                .inflate(expand, 0.0, expand)
            return world.noCollision(player, hitbox)
        }

        private fun isCloseToFall(position: Vec3): Boolean {
            position.y = floor(position.y)
            val hitbox = player.getDimensions(Pose.STANDING).makeBoundingBox(position)
                .inflate(-0.05, 0.0, -0.05)
                .move(0.0, -EdgeCheck.maxFallHeight.toDouble(), 0.0)
            return world.noCollision(player, hitbox)
        }
    }

    object AdaptiveRange : ToggleableValueGroup(TargetStrafePlannerSettings, "AdaptiveRange", false) {
        val maxRange by float("MaxRange", 4f, 1f..5f)
        val rangeStep by float("RangeStep", 0.5f, 0.1f..1.0f)
    }
}

internal fun targetStrafeStandingCollisionBox(position: Vec3, dimensions: EntityDimensions): AABB =
    dimensions.makeBoundingBox(position).deflate(1.0E-7)

internal enum class TargetStrafeRequirement(
    override val tag: String,
    val meets: () -> Boolean,
) : Tagged {
    SPACE("Space", { mc.options.keyJump.isDown }),
    SPEED("Speed", { TargetStrafeEnvironment.speedRunning }),
    KILLAURA("KillAura", { TargetStrafeEnvironment.killAuraRunning }),
    GROUND("Ground", { mc.player!!.onGround() }),
}
