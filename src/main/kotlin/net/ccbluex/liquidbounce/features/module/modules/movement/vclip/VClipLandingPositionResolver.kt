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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB

internal object VClipLandingPositionResolver : MinecraftShortcuts {

    fun isBedrockPathBlocked(entity: Entity, targetY: Double, enabled: Boolean): Boolean =
        VClipBedrockPath.isBlocked(
            enabled = enabled,
            boundingBox = controlledBoundingBox(entity),
            verticalOffset = targetY - entity.y,
        ) { position ->
            entity.level().getBlockState(position).block === Blocks.BEDROCK
        }

    fun resolve(
        entity: Entity,
        direction: VClipDirection,
        maxDistance: Int?,
        doNotClipAroundBedrock: Boolean,
    ): VClipPosition? {
        val position = entity.position()
        val level = entity.level()
        val targetY = VClipTargetPlanner.smartTargetY(
            scan = VClipSmartScan(
                currentY = position.y,
                direction = direction,
                minBuildY = level.minY,
                maxBuildY = level.maxY,
                maxDistance = maxDistance,
                scanStep = SMART_SCAN_STEP,
                collisionRefinementStep = SMART_COLLISION_REFINEMENT_STEP,
            ),
            hasBlockCollisionBetween = { fromY, toY ->
                hasBlockCollisionBetween(entity, fromY, toY)
            },
            hasAnyCollisionAt = { candidateY ->
                hasAnyCollisionAt(entity, candidateY)
            },
        ) ?: return null
        if (isBedrockPathBlocked(entity, targetY, doNotClipAroundBedrock)) {
            return null
        }

        return VClipPosition(position.x, targetY, position.z)
    }

    private fun controlledBoundingBox(entity: Entity): AABB =
        if (entity === player) entity.boundingBox else entity.boundingBox.minmax(player.boundingBox)

    private fun hasBlockCollisionBetween(entity: Entity, fromY: Double, toY: Double): Boolean {
        val level = entity.level()
        val referenceY = entity.y

        fun Entity.collidesBetween(box: AABB): Boolean {
            val fromBox = box.move(0.0, fromY - referenceY, 0.0)
            val toBox = box.move(0.0, toY - referenceY, 0.0)
            return level.getBlockCollisions(this, fromBox.minmax(toBox)).anyNotEmpty()
        }

        return entity.collidesBetween(entity.boundingBox) ||
            (entity !== player && player.collidesBetween(player.boundingBox))
    }

    private fun hasAnyCollisionAt(entity: Entity, targetY: Double): Boolean {
        val level = entity.level()
        val verticalOffset = targetY - entity.y
        if (!fitsInsideBuildHeight(entity, verticalOffset)) {
            return true
        }
        if (!level.noCollision(entity, entity.boundingBox.move(0.0, verticalOffset, 0.0))) {
            return true
        }
        return entity !== player && !level.noCollision(player, player.boundingBox.move(0.0, verticalOffset, 0.0))
    }

    private fun fitsInsideBuildHeight(entity: Entity, verticalOffset: Double): Boolean {
        val level = entity.level()
        val minimumY = level.minY.toDouble()
        val maximumYExclusive = level.maxY + 1.0

        fun AABB.fits() = move(0.0, verticalOffset, 0.0).let { moved ->
            moved.minY >= minimumY - BOUNDING_BOX_EPSILON &&
                moved.maxY <= maximumYExclusive + BOUNDING_BOX_EPSILON
        }

        return entity.boundingBox.fits() && (entity === player || player.boundingBox.fits())
    }

    private const val SMART_SCAN_STEP = 0.25
    private const val SMART_COLLISION_REFINEMENT_STEP = 0.05
    private const val BOUNDING_BOX_EPSILON = 1.0E-7
}
