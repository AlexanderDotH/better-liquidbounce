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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.movement.buildLinearTeleportPath
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal suspend fun ReachHitRuntime.executeAdaptiveHit(
    target: LivingEntity,
    origin: Vec3,
    targetPosition: Vec3,
    rotation: Rotation,
    keepSprint: Boolean,
    generation: Long,
): Boolean {
    val destination = calculateReachHitDestination(
        origin,
        targetPosition,
        player.bbWidth.toDouble(),
        target.bbWidth.toDouble(),
    )
    val config = owner.modeConfiguration.adaptive
    val stepSizes = calculateReachHitAdaptiveStepSizes(
        config.initialStep.toDouble(),
        config.minimumStep.toDouble(),
        config.retries,
    )
    return executeAdaptiveReachHit(
        stepSizes,
        attempt = { step -> attemptAdaptiveTravel(destination, rotation, step) },
        onAccepted = { step ->
            completeAdaptiveAttack(target, origin, destination, rotation, keepSprint, generation, step)
        },
        onExhausted = { recoverRejectedAdaptiveRoute(origin, rotation, stepSizes.last()) },
    )
}

private suspend fun ReachHitRuntime.attemptAdaptiveTravel(
    destination: Vec3,
    rotation: Rotation,
    step: Double,
): Boolean {
    setbackDetected = false
    desyncPlayerPosition = null
    val path = buildLinearTeleportPath(player.position(), destination, step)
    return travelPath(path, rotation) && waitForAdaptiveAcceptance()
}

private suspend fun ReachHitRuntime.completeAdaptiveAttack(
    target: LivingEntity,
    origin: Vec3,
    destination: Vec3,
    rotation: Rotation,
    keepSprint: Boolean,
    generation: Long,
    acceptedStep: Double,
): Boolean {
    val attacked = attackTarget(target, destination, keepSprint, generation)
    if (attacked && !setbackDetected) {
        travelPath(buildLinearTeleportPath(destination, origin, acceptedStep), rotation)
        waitForAdaptiveAcceptance()
    }
    return attacked
}

private suspend fun ReachHitRuntime.recoverRejectedAdaptiveRoute(
    origin: Vec3,
    rotation: Rotation,
    fallbackStep: Double,
) {
    chat(markAsError("Adaptive route was rejected after all smaller-step retries."))
    setbackDetected = false
    desyncPlayerPosition = null
    val current = player.position()
    if (current.distanceToSqr(origin) > REACH_HIT_HOME_DISTANCE_SQUARED) {
        travelPath(buildLinearTeleportPath(current, origin, fallbackStep), rotation)
    }
}

private suspend fun ReachHitRuntime.waitForAdaptiveAcceptance(): Boolean {
    waitTicks(owner.modeConfiguration.adaptive.verifyTicks)
    return !setbackDetected
}
