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
import net.ccbluex.liquidbounce.utils.block.canStandOn
import net.ccbluex.liquidbounce.utils.block.collisionShape
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

internal object VClipLandingPositionResolver : MinecraftShortcuts {

    fun resolve(
        entity: Entity,
        direction: VClipDirection,
        maxDistance: Int?,
        doNotClipAroundBedrock: Boolean,
    ): VClipPosition? {
        val blockPosition = entity.blockPosition()
        val position = entity.position()
        val level = entity.level()
        val bedrockFootprint = controlledBoundingBox(entity).takeIf { doNotClipAroundBedrock }
        val targetY = VClipTargetPlanner.smartTargetY(
            scan = VClipSmartScan(
                startBlockY = blockPosition.y,
                currentY = position.y,
                direction = direction,
                minBuildY = level.minY,
                maxBuildY = level.maxY,
                maxDistance = maxDistance,
            ),
            isBarrierAt = { supportY ->
                bedrockFootprint?.let { containsBedrock(entity, it, supportY) } == true
            },
            surfaceOffsetAt = { supportY ->
                surfaceOffset(
                    entity = entity,
                    supportPosition = BlockPos(blockPosition.x, supportY, blockPosition.z),
                    bedrockFootprint = bedrockFootprint,
                )
            },
        ) ?: return null

        return VClipPosition(position.x, targetY, position.z)
    }

    private fun surfaceOffset(entity: Entity, supportPosition: BlockPos, bedrockFootprint: AABB?): Double? {
        val supportShape = supportPosition.collisionShape
        if (supportShape.isEmpty) {
            return null
        }

        val surfaceOffset = supportShape.max(Direction.Axis.Y)
        val targetY = supportPosition.y + surfaceOffset
        if (!fitsInsideBuildHeight(entity, targetY) ||
            bedrockFootprint?.let { containsBedrockAtTarget(entity, it, targetY) } == true ||
            !canLandOn(entity, supportPosition, supportShape)
        ) {
            return null
        }

        return surfaceOffset
    }

    private fun controlledBoundingBox(entity: Entity): AABB =
        if (entity === player) entity.boundingBox else entity.boundingBox.minmax(player.boundingBox)

    private fun containsBedrock(entity: Entity, footprint: AABB, blockY: Int): Boolean {
        val minBlockX = Mth.floor(footprint.minX + BOUNDING_BOX_EPSILON)
        val maxBlockX = Mth.floor(footprint.maxX - BOUNDING_BOX_EPSILON)
        val minBlockZ = Mth.floor(footprint.minZ + BOUNDING_BOX_EPSILON)
        val maxBlockZ = Mth.floor(footprint.maxZ - BOUNDING_BOX_EPSILON)

        for (blockX in minBlockX..maxBlockX) {
            for (blockZ in minBlockZ..maxBlockZ) {
                if (entity.level().getBlockState(BlockPos(blockX, blockY, blockZ)).block === Blocks.BEDROCK) {
                    return true
                }
            }
        }

        return false
    }

    private fun containsBedrockAtTarget(entity: Entity, footprint: AABB, targetY: Double): Boolean {
        val movedFootprint = footprint.move(0.0, targetY - entity.y, 0.0)
        val minBlockY = Mth.floor(movedFootprint.minY + BOUNDING_BOX_EPSILON)
        val maxBlockY = Mth.floor(movedFootprint.maxY - BOUNDING_BOX_EPSILON)

        return (minBlockY..maxBlockY).any { blockY -> containsBedrock(entity, movedFootprint, blockY) }
    }

    private fun fitsInsideBuildHeight(entity: Entity, targetY: Double): Boolean {
        val level = entity.level()
        val verticalOffset = targetY - entity.y
        val minimumY = level.minY.toDouble()
        val maximumYExclusive = level.maxY + 1.0

        fun AABB.fits() =
            move(0.0, verticalOffset, 0.0).let { moved ->
                moved.minY >= minimumY - BOUNDING_BOX_EPSILON &&
                    moved.maxY <= maximumYExclusive + BOUNDING_BOX_EPSILON
            }

        return entity.boundingBox.fits() && (entity === player || player.boundingBox.fits())
    }

    private fun canLandOn(entity: Entity, position: BlockPos, supportShape: VoxelShape): Boolean {
        if (isNotEnoughSpaceAboveBlock(position, entity.boundingBox, supportShape)) {
            return false
        }

        if (entity !== player && isNotEnoughSpaceAboveBlock(position, player.boundingBox, supportShape)) {
            return false
        }

        if (position.canStandOn()) {
            return true
        }

        return intersectsAtSurface(position, entity.boundingBox, supportShape)
    }

    private fun intersectsAtSurface(position: BlockPos, boundingBox: AABB, supportShape: VoxelShape): Boolean {
        val worldShape = supportShape.move(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
        val verticalOffset = worldShape.min(Direction.Axis.Y) - boundingBox.minY
        return Shapes.joinIsNotEmpty(
            worldShape,
            Shapes.create(boundingBox.move(0.0, verticalOffset, 0.0)),
            BooleanOp.AND,
        )
    }

    private fun isNotEnoughSpaceAboveBlock(
        position: BlockPos,
        boundingBox: AABB,
        supportShape: VoxelShape,
    ): Boolean {
        val supportHeight = supportShape.max(Direction.Axis.Y)
        val requiredHeight = boundingBox.maxY - boundingBox.minY - (1.0 - supportHeight)
        var accumulatedHeight = 0.0
        var inspectedPosition = position

        while (accumulatedHeight < requiredHeight) {
            inspectedPosition = inspectedPosition.above()
            val collisionShape = inspectedPosition.collisionShape
            if (!collisionShape.isEmpty && collisionShape.min(Direction.Axis.Y) < requiredHeight - accumulatedHeight) {
                return true
            }
            accumulatedHeight += 1.0
        }

        return false
    }

    private const val BOUNDING_BOX_EPSILON = 1.0E-7
}
