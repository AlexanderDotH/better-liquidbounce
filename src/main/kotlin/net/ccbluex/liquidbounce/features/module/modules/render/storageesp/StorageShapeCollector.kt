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

package net.ccbluex.liquidbounce.features.module.modules.render.storageesp

import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.math.PositionedVoxelShape
import net.ccbluex.liquidbounce.utils.math.mergeAdjacentVoxelShapes
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

internal class StorageShapeCollector<C : StorageEspCategory>(
    private val entries: () -> Sequence<Map.Entry<BlockPos, C>>,
    private val mergeAdjacent: () -> Boolean,
) : MinecraftShortcuts {

    fun collect(
        skipWhen: (BlockState) -> Boolean = { false },
    ): List<PositionedVoxelShape<StorageEspCategory>> {
        val shapes = buildList {
            entries().forEach { (blockPos, type) ->
                val shape = shapeAt(blockPos, type, skipWhen) ?: return@forEach
                add(shape)
            }
        }

        return if (mergeAdjacent()) shapes.mergeAdjacentVoxelShapes() else shapes
    }

    private fun shapeAt(
        blockPos: BlockPos,
        type: C,
        skipWhen: (BlockState) -> Boolean,
    ): PositionedVoxelShape<StorageEspCategory>? {
        if (type.color.isTransparent || !type.shouldRender(blockPos, ignoreDistance = true)) return null

        val blockState = world.getBlockState(blockPos)
        if (blockState.isAir || skipWhen(blockState)) return null

        return PositionedVoxelShape(
            blockPos = blockPos.asLong(),
            key = type,
            shape = blockState.getShape(world, blockPos),
        )
    }
}
