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
 */

package net.ccbluex.liquidbounce.render.trajectory

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.projectiles.ProjectileAngleCalculator
import net.ccbluex.liquidbounce.utils.aiming.projectiles.SituationalProjectileAngleCalculator
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.entity.box
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryType
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

object VerifiedProjectileAngleCalculator : ProjectileAngleCalculator {
    override fun calculateAngleFor(
        projectileInfo: TrajectoryInfo,
        sourcePos: Vec3,
        targetPosFunction: PositionExtrapolation,
        targetShape: EntityDimensions,
    ): Rotation? {
        val rotation = SituationalProjectileAngleCalculator
            .calculateAngleFor(projectileInfo, sourcePos, targetPosFunction, targetShape) ?: return null
        val hit = TrajectoryInfoRenderer.getHypotheticalTrajectory(
            simulationOwner = player,
            trajectoryInfo = projectileInfo,
            rotation = rotation,
            trajectoryType = projectileInfo.trajectoryType(),
        ).runSimulation(300).hitResult as? EntityHitResult ?: return null

        val targetPosition = targetPosFunction.getPositionInTicks(0.0)
        return rotation.takeIf { hit.entity.box.intersects(targetShape.makeBoundingBox(targetPosition)) }
    }
}

private fun TrajectoryInfo.trajectoryType(): TrajectoryType = when {
    this == TrajectoryInfo.POTION -> TrajectoryType.Potion
    this == TrajectoryInfo.EXP_BOTTLE -> TrajectoryType.ExpBottle
    this == TrajectoryInfo.FISHING_ROD -> TrajectoryType.FishingBobber
    this == TrajectoryInfo.TRIDENT -> TrajectoryType.Trident
    this == TrajectoryInfo.FIREWORK_ROCKET -> TrajectoryType.FireworkRocket
    this == TrajectoryInfo.GENERIC -> TrajectoryType.Snowball
    matchesBowTrajectory() -> TrajectoryType.Arrow
    gravity == 0.0 && hitboxRadius >= 1.0 && copiesPlayerVelocity -> TrajectoryType.Fireball
    gravity == 0.0 && hitboxRadius >= 1.0 -> TrajectoryType.WindCharge
    else -> TrajectoryType.Arrow
}

private fun TrajectoryInfo.matchesBowTrajectory(): Boolean =
    hitboxRadius == TrajectoryInfo.BOW_FULL_PULL.hitboxRadius &&
        gravity == TrajectoryInfo.BOW_FULL_PULL.gravity &&
        drag == TrajectoryInfo.BOW_FULL_PULL.drag &&
        dragInWater == TrajectoryInfo.BOW_FULL_PULL.dragInWater &&
        copiesPlayerVelocity == TrajectoryInfo.BOW_FULL_PULL.copiesPlayerVelocity
