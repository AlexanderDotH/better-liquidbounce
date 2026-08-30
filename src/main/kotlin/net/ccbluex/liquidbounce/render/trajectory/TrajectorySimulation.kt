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

import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.box
import net.ccbluex.liquidbounce.utils.math.copy
import net.ccbluex.liquidbounce.utils.math.minus
import net.ccbluex.liquidbounce.utils.math.move
import net.ccbluex.liquidbounce.utils.math.scaleMut
import net.ccbluex.liquidbounce.utils.math.set
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryType
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.jvm.optionals.getOrNull

internal class TrajectorySimulation(
    private val simulationOwner: Entity,
    private val trajectoryInfo: TrajectoryInfo,
    private val trajectoryType: TrajectoryType,
    velocity: Vec3,
    pos: Vec3,
) {
    private val velocity = velocity.copy()
    private val pos = pos.copy()
    private val hitbox = trajectoryInfo.hitbox()
    private val mutableBlockPos = BlockPos.MutableBlockPos()

    fun run(maxTicks: Int): TrajectoryInfoRenderer.SimulationResult {
        val positions = mutableListOf<Vec3>()
        val requiresInitialTickCorrection = trajectoryType.requiresInitialTickCorrection
        if (requiresInitialTickCorrection) tickVelocity()

        val previousPosition = pos.copy()
        var currentTick = if (requiresInitialTickCorrection) 1 else 0
        while (currentTick < maxTicks) {
            if (pos.y < world.minY) break

            val hitResult = checkForHits(previousPosition.set(pos), pos.move(velocity))
            if (hitResult != null) {
                hitResult.second?.let(positions::add)
                return TrajectoryInfoRenderer.SimulationResult(hitResult.first, positions)
            }

            tickVelocity()
            positions += pos.copy()
            currentTick++
        }

        if (positions.isEmpty()) positions += pos
        return TrajectoryInfoRenderer.SimulationResult(null, positions)
    }

    private fun tickVelocity() {
        val blockState = world.getBlockState(mutableBlockPos.set(pos.x, pos.y, pos.z))
        val drag = if (!blockState.fluidState.isEmpty) trajectoryInfo.dragInWater else trajectoryInfo.drag
        velocity.scaleMut(drag).move(y = -trajectoryInfo.gravity)
    }

    private fun checkForHits(posBefore: Vec3, posAfter: Vec3): Pair<HitResult, Vec3?>? {
        val blockHitResult = world.clip(
            ClipContext(
                posBefore,
                posAfter,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                simulationOwner,
            )
        )
        if (blockHitResult.type != HitResult.Type.MISS) return blockHitResult to blockHitResult.location

        val entityHitResult = ProjectileUtil.getEntityHitResult(
            world,
            simulationOwner,
            posBefore,
            posAfter,
            hitbox.move(posBefore).expandTowards(posAfter - posBefore).inflate(1.0),
            ::canCollide,
            if (simulationOwner is Projectile) ProjectileUtil.computeMargin(simulationOwner) else 0f,
        )
        if (entityHitResult == null || entityHitResult.type == HitResult.Type.MISS) return null

        val hitPosition = entityHitResult.entity.box.inflate(trajectoryInfo.hitboxRadius).clip(posBefore, posAfter)
        return entityHitResult to hitPosition.getOrNull()
    }

    private fun canCollide(entity: Entity): Boolean {
        val canCollide = !entity.isSpectator && entity.isAlive
        val shouldCollide = entity.isPickable || simulationOwner !== player && entity === player
        return canCollide && shouldCollide && !simulationOwner.isPassengerOfSameVehicle(entity)
    }
}
