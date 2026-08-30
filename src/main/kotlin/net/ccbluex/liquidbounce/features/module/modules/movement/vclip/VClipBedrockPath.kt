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

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB

internal object VClipBedrockPath {

    fun isBlocked(
        enabled: Boolean,
        boundingBox: AABB,
        verticalOffset: Double,
        isBedrockAt: (BlockPos) -> Boolean,
    ): Boolean {
        require(verticalOffset.isFinite()) { "VClip vertical offset must be finite" }
        if (!enabled) {
            return false
        }

        val sweptBox = boundingBox.minmax(boundingBox.move(0.0, verticalOffset, 0.0))
        val minBlockX = Mth.floor(sweptBox.minX + BOUNDING_BOX_EPSILON)
        val maxBlockX = Mth.floor(sweptBox.maxX - BOUNDING_BOX_EPSILON)
        val minBlockY = Mth.floor(sweptBox.minY + BOUNDING_BOX_EPSILON)
        val maxBlockY = Mth.floor(sweptBox.maxY - BOUNDING_BOX_EPSILON)
        val minBlockZ = Mth.floor(sweptBox.minZ + BOUNDING_BOX_EPSILON)
        val maxBlockZ = Mth.floor(sweptBox.maxZ - BOUNDING_BOX_EPSILON)

        for (blockX in minBlockX..maxBlockX) {
            for (blockY in minBlockY..maxBlockY) {
                if (hasBedrockAtY(blockX, blockY, minBlockZ..maxBlockZ, isBedrockAt)) return true
            }
        }

        return false
    }

    private fun hasBedrockAtY(
        blockX: Int,
        blockY: Int,
        blockZRange: IntRange,
        isBedrockAt: (BlockPos) -> Boolean,
    ): Boolean = blockZRange.any { blockZ -> isBedrockAt(BlockPos(blockX, blockY, blockZ)) }

    private const val BOUNDING_BOX_EPSILON = 1.0E-7
}
