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

import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.drawBoxSide
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.math.move
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

internal fun WorldRenderEnvironment.drawSplashPotionTargets(
    landingPosition: Vec3,
    trajectoryInfo: TrajectoryInfo,
    partialTicks: Float,
    entityHitColor: Color4b,
) {
    val box: AABB = trajectoryInfo.hitbox(landingPosition).inflate(4.0, 2.0, 4.0)
    val hitTargets = world.getEntitiesOfClass(LivingEntity::class.java, box) {
        it.distanceToSqr(landingPosition) <= 16.0 && it.isAffectedByPotions
    }
    drawHitEntities(entityHitColor, hitTargets, partialTicks)
}

internal fun WorldRenderEnvironment.drawHitEntities(
    entityHitColor: Color4b,
    entities: List<Entity>,
    partialTicks: Float,
) {
    for (entity in entities) {
        if (entity === player) continue
        val position = entity.interpolateCurrentPosition(partialTicks)
        withPositionRelativeToCamera(position) {
            drawBox(entity.getDimensions(entity.pose).makeBoundingBox(Vec3.ZERO), entityHitColor)
        }
    }
}

internal fun WorldRenderEnvironment.renderHitBlockFace(blockHitResult: BlockHitResult, color: Color4b) {
    val position = blockHitResult.blockPos
    val state = position.stateOrEmpty
    val bestBox = state.getShape(world, position, CollisionContext.of(player)).toAabbs()
        .filter { blockHitResult.location in it.inflate(0.01).move(position) }
        .minByOrNull { it.distanceToSqr(blockHitResult.location) }
        ?: return
    withPositionRelativeToCamera(position) {
        drawBoxSide(bestBox, side = blockHitResult.direction, faceColor = color)
    }
}
