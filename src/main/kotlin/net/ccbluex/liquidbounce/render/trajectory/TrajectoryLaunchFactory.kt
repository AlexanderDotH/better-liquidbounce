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
package net.ccbluex.liquidbounce.render.trajectory

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.math.minus
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.ccbluex.liquidbounce.utils.math.withLength
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryType
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

fun interface TrajectoryFreezeStateProvider {
    fun isRunning(): Boolean
}

object TrajectoryFreezeStateBridge {

    @Volatile
    private var provider: TrajectoryFreezeStateProvider? = null

    @JvmStatic
    @Synchronized
    fun install(provider: TrajectoryFreezeStateProvider) {
        check(this.provider == null) { "Trajectory freeze state provider is already installed" }
        this.provider = provider
    }

    internal fun isRunning(): Boolean = provider?.isRunning() == true

    @Synchronized
    internal fun <T> withProviderForTest(candidate: TrajectoryFreezeStateProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}

internal fun createHypotheticalTrajectory(
    simulationOwner: Entity,
    trajectoryInfo: TrajectoryInfo,
    trajectoryType: TrajectoryType,
    rotation: Rotation,
    icon: ItemStack,
    partialTicks: Float,
): TrajectoryInfoRenderer {
    val yawRadians = rotation.yaw.toRadians()
    val pitchRadians = rotation.pitch.toRadians()
    val interpolatedOffset = simulationOwner.interpolateCurrentPosition(partialTicks) - simulationOwner.position()
    val pos = Vec3(simulationOwner.x, simulationOwner.eyeY - 0.10000000149011612, simulationOwner.z)
    var velocity = projectileDirectionFromRotation(
        yawRadians = yawRadians,
        pitchRadians = pitchRadians,
        pitchWithRollRadians = (rotation.pitch + trajectoryInfo.roll).toRadians(),
    ).withLength(trajectoryInfo.initialVelocity)

    if (shouldCopyOwnerVelocity(trajectoryInfo.copiesPlayerVelocity, TrajectoryFreezeStateBridge.isRunning())) {
        velocity = velocity.add(
            simulationOwner.deltaMovement.x,
            if (simulationOwner.onGround()) 0.0 else simulationOwner.deltaMovement.y,
            simulationOwner.deltaMovement.z,
        )
    }

    return TrajectoryInfoRenderer(
        simulationOwner = simulationOwner,
        displayOwner = simulationOwner,
        icon = icon,
        velocity = velocity,
        pos = pos,
        trajectoryInfo = trajectoryInfo,
        trajectoryType = trajectoryType,
        type = TrajectoryInfoRenderer.Type.HYPOTHETICAL,
        renderOffset = interpolatedOffset.add(-cos(yawRadians) * 0.16, 0.0, -sin(yawRadians) * 0.16),
    )
}

internal fun shouldCopyOwnerVelocity(copiesPlayerVelocity: Boolean, freezeRunning: Boolean): Boolean =
    copiesPlayerVelocity && !freezeRunning

/** @see net.minecraft.world.entity.projectile.Projectile.shootFromRotation */
internal fun projectileDirectionFromRotation(
    yawRadians: Float,
    pitchRadians: Float,
    pitchWithRollRadians: Float,
): Vec3 = Vec3(
    -sin(yawRadians) * cos(pitchRadians).toDouble(),
    -sin(pitchWithRollRadians).toDouble(),
    cos(yawRadians) * cos(pitchRadians).toDouble(),
)
