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

package net.ccbluex.liquidbounce.features.module.modules.render.blockesp

import com.mojang.blaze3d.buffers.GpuBufferSlice
import net.ccbluex.liquidbounce.render.GenericColorMode
import net.ccbluex.liquidbounce.render.utils.DistanceFadeUniformValueGroup
import net.ccbluex.liquidbounce.utils.math.PositionedVoxelShape
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import org.joml.Matrix4f

internal interface BlockEspRuntime {
    val distanceFadeSettings: DistanceFadeUniformValueGroup
    val activeBlockColorMode: GenericColorMode<Pair<BlockPos, BlockState>>

    fun trackerIsEmpty(): Boolean
    fun modeTransforms(
        useColor: Boolean,
        modelView: Matrix4f? = null,
        colorModulatorAlpha: Int = -1,
    ): GpuBufferSlice
    fun collectBlockShapes(
        colorMode: GenericColorMode<Pair<BlockPos, BlockState>>,
        useColor: Boolean,
    ): List<PositionedVoxelShape<BlockMergeKey>>
}
