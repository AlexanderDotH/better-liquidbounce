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
package net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder.portal

import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.drawPlane
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.block.immutable
import net.ccbluex.liquidbounce.utils.math.center
import net.ccbluex.liquidbounce.utils.math.toVec3d
import net.ccbluex.liquidbounce.utils.world.forEachSectionBlock
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot

internal class PortalBlockTracker {

    private val detectedPortalBlocks = hashMapOf<BlockPos, PortalBlockType>()

    fun isNotEmpty(): Boolean = detectedPortalBlocks.isNotEmpty()

    fun track(pos: BlockPos, block: Block) {
        when (block) {
            Blocks.END_PORTAL -> detectedPortalBlocks[pos.immutable] = PortalBlockType.PORTAL
            Blocks.END_PORTAL_FRAME -> detectedPortalBlocks[pos.immutable] = PortalBlockType.FRAME
            else -> detectedPortalBlocks.remove(pos)
        }
    }

    fun scan(world: ClientLevel, chunkX: Int, chunkZ: Int) {
        val chunk = world.getChunk(chunkX, chunkZ)
        removeChunk(chunk.pos)

        for (sectionIndex in 0..chunk.highestFilledSectionIndex) {
            chunk.forEachSectionBlock(sectionIndex) { pos, state ->
                when (state.block) {
                    Blocks.END_PORTAL -> detectedPortalBlocks[pos.immutable] = PortalBlockType.PORTAL
                    Blocks.END_PORTAL_FRAME -> detectedPortalBlocks[pos.immutable] = PortalBlockType.FRAME
                }
            }
        }
    }

    fun removeChunk(chunkPos: ChunkPos) {
        detectedPortalBlocks.keys.removeIf(chunkPos::contains)
    }

    fun render(environment: WorldRenderEnvironment, playerPosition: Vec3) = with(environment) {
        detectedPortalBlocks.forEach { (pos, type) ->
            withPositionRelativeToCamera(pos.toVec3d(yOffset = 0.01)) {
                drawPlane(1f, 1f, type.color, type.color.darker())
            }
        }

        val closestPortalPos = detectedPortalBlocks.keys.minByOrNull { pos ->
            pos.distToCenterSqr(playerPosition)
        } ?: return@with

        val start = playerPosition.add(0.0, 0.05, 0.0)
        val target = closestPortalPos.center
        val lineColor = Color4b(255, 80, 80, 220).argb
        withPositionRelativeToCamera {
            drawLine(start, target, lineColor)

            val deltaX = target.x - start.x
            val deltaZ = target.z - start.z
            val horizontalLength = hypot(deltaX, deltaZ)
            if (horizontalLength > 1e-6) {
                val markerEnd = Vec3(
                    start.x + deltaX / horizontalLength * 2.0,
                    start.y,
                    start.z + deltaZ / horizontalLength * 2.0
                )
                drawLine(start, markerEnd, lineColor)
            }
        }
    }

    fun clear() {
        detectedPortalBlocks.clear()
    }

    private enum class PortalBlockType(val color: Color4b) {
        PORTAL(Color4b(0, 220, 255, 170)),
        FRAME(Color4b(255, 215, 0, 170)),
    }
}
