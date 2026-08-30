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
package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.planner

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafeEnvironment
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafePlannerConfiguration
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafePlannerPort
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafePointValidation
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafeRuntime
import net.ccbluex.liquidbounce.utils.entity.anyHorizontal
import net.ccbluex.liquidbounce.utils.entity.horizontalSpeed
import net.ccbluex.liquidbounce.utils.entity.initial
import net.ccbluex.liquidbounce.utils.entity.untransformed
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.utils.math.horizontalDistanceTo
import net.ccbluex.liquidbounce.utils.math.yaw
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.movement.getDegreesRelativeToView
import net.ccbluex.liquidbounce.utils.movement.getDirectionalInputForDegrees
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal object TargetStrafePlanner : MinecraftShortcuts, TargetStrafePlannerPort {
    private var direction = 1

    override fun handleInput(event: MovementInputEvent) {
        if (!event.directionalInput.isMoving) {
            TargetStrafeRuntime.renderState.reset()
            return
        }
        val plan = compute(player.horizontalSpeed, event.directionalInput) ?: return
        if (!plan.pointValid) return
        val degrees = getDegreesRelativeToView(plan.strafeVec, player.yRot)
        event.directionalInput = getDirectionalInputForDegrees(DirectionalInput.NONE, degrees)
    }

    override fun handleMotion(event: PlayerMoveEvent, speed: Double, hypixel: Boolean) {
        if (event.type != MoverType.SELF) return
        if (!player.input.initial.anyHorizontal) {
            TargetStrafeRuntime.renderState.reset()
            return
        }
        val plan = compute(speed, DirectionalInput(player.input.untransformed)) ?: return
        if (!plan.pointValid) return
        event.movement = event.movement.withStrafe(
            yaw = plan.strafeVec.yaw,
            speed = effectiveMotionSpeed(speed, hypixel),
            strength = hypixelStrafeStrength(hypixel),
            input = null,
        )
    }

    private fun compute(speed: Double, controlInput: DirectionalInput): TargetStrafePlan? {
        val context = targetContext() ?: return null
        updateDirection(controlInput)
        val createPlan = { range: Float -> createPlan(context, speed, range) }
        var plan = createPlan(TargetStrafeRuntime.orbitRange)
        if (!plan.pointValid) plan = recoverInvalidPlan(plan, createPlan)
        TargetStrafeRuntime.renderState.update(
            plan.target,
            plan.orbitRadius,
            plan.pointCoords,
            plan.pointValid,
        )
        return plan
    }

    private fun targetContext(): TargetStrafeContext? {
        if (!TargetStrafeRuntime.requirementsMet) return resetAndNull()
        val target = TargetStrafeRuntime.firstTarget() ?: return resetAndNull()
        val playerPosition = player.position()
        val targetPosition = target.position()
        val distance = playerPosition.horizontalDistanceTo(targetPosition)
        if (distance > TargetStrafeRuntime.followRange) return resetAndNull()
        return TargetStrafeContext(target, playerPosition, targetPosition, distance)
    }

    private fun resetAndNull(): TargetStrafeContext? {
        TargetStrafeRuntime.renderState.reset()
        return null
    }

    private fun updateDirection(controlInput: DirectionalInput) {
        if (player.horizontalCollision) direction = -direction
        if (!TargetStrafePlannerConfiguration.controlDirection || controlInput.left && controlInput.right) return
        when {
            controlInput.left -> direction = -1
            controlInput.right -> direction = 1
        }
    }

    private fun createPlan(context: TargetStrafeContext, speed: Double, range: Float): TargetStrafePlan {
        val yaw = atan2(
            context.targetPosition.z - context.playerPosition.z,
            context.targetPosition.x - context.playerPosition.x,
        )
        val vector = computeTargetStrafeDirection(yaw, context.distance, speed, range, direction)
        val point = context.playerPosition.add(vector)
        return TargetStrafePlan(
            context.target,
            range,
            vector,
            point,
            TargetStrafePointValidation.validatePoint(point),
        )
    }

    private fun recoverInvalidPlan(
        initial: TargetStrafePlan,
        createPlan: (Float) -> TargetStrafePlan,
    ): TargetStrafePlan {
        if (!TargetStrafePlannerConfiguration.adaptiveRangeEnabled) {
            direction = -direction
            return createPlan(TargetStrafeRuntime.orbitRange)
        }
        var plan = initial
        var currentRange = TargetStrafePlannerConfiguration.adaptiveRangeStep
        while (!plan.pointValid) {
            plan = createPlan(currentRange)
            currentRange += TargetStrafePlannerConfiguration.adaptiveRangeStep
            if (currentRange > TargetStrafePlannerConfiguration.adaptiveRangeMaximum) {
                direction = -direction
                plan = createPlan(TargetStrafeRuntime.orbitRange)
                break
            }
        }
        return plan
    }

    private fun effectiveMotionSpeed(speed: Double, hypixel: Boolean): Double = targetStrafeMotionSpeed(
        speed,
        hypixel,
        TargetStrafeEnvironment.speedRunning,
        player.onGround(),
    )

    private fun hypixelStrafeStrength(hypixel: Boolean) = targetStrafeStrength(
        hypixel,
        TargetStrafeEnvironment.speedRunning,
        TargetStrafeEnvironment.lowHopShouldStrafe,
    )
}

internal fun targetStrafeMotionSpeed(
    speed: Double,
    hypixel: Boolean,
    speedRunning: Boolean,
    onGround: Boolean,
): Double = if (hypixel && speedRunning) speed.coerceAtLeast(if (onGround) 0.48 else 0.281) else speed

internal fun targetStrafeStrength(
    hypixel: Boolean,
    speedRunning: Boolean,
    lowHopShouldStrafe: Boolean,
): Double = if (hypixel && speedRunning && !lowHopShouldStrafe) 0.02 else 1.0

internal data class TargetStrafePlan(
    val target: LivingEntity,
    val orbitRadius: Float,
    val strafeVec: Vec3,
    val pointCoords: Vec3,
    val pointValid: Boolean,
)

private data class TargetStrafeContext(
    val target: LivingEntity,
    val playerPosition: Vec3,
    val targetPosition: Vec3,
    val distance: Double,
)

internal fun computeTargetStrafeDirection(
    strafeYaw: Double,
    distance: Double,
    speed: Double,
    range: Float,
    direction: Int,
): Vec3 {
    val yaw = strafeYaw - Mth.HALF_PI
    val encirclement = maxOf(-speed, distance - range)
    val encirclementX = -sin(yaw) * encirclement
    val encirclementZ = cos(yaw) * encirclement
    val strafeX = -sin(strafeYaw) * speed * direction
    val strafeZ = cos(strafeYaw) * speed * direction
    return Vec3(encirclementX + strafeX, 0.0, encirclementZ + strafeZ)
}
