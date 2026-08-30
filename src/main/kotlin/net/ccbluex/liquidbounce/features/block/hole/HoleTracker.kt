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
package net.ccbluex.liquidbounce.features.block.hole

import net.ccbluex.fastutil.referenceHashSetOf
import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.utils.block.hole.Hole
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.expandToBoundingBox
import net.ccbluex.liquidbounce.utils.math.toBlockBox
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.util.concurrent.ConcurrentSkipListSet

object HoleTracker : ChunkScanner.BlockChangeSubscriber {

    val holes = ConcurrentSkipListSet<Hole>()
    private val BLAST_RESISTANT_BLOCKS: Set<Block> by lazy {
        BuiltInRegistries.BLOCK.filterTo(referenceHashSetOf()) {
            it.explosionResistance >= 600 && it.explosionResistance < 3_600_000
        }
    }

    private val INDESTRUCTIBLE_BLOCKS: Set<Block> by lazy {
        BuiltInRegistries.BLOCK.filterTo(referenceHashSetOf()) {
            it.explosionResistance >= 3_600_000
        }
    }

    override val shouldCallRecordBlockOnChunkUpdate: Boolean
        get() = false

    override fun recordBlock(pos: BlockPos, state: BlockState, cleared: Boolean) {
        // Invalidate old ones
        if (state.isAir) {
            // if one of the neighbor blocks becomes air, invalidate the hole
            holes.removeIf { it.asList().any { p -> p.distManhattan(pos) == 1 } }
        } else {
            holes.removeIf { it.isInvalidatedByFilling(pos) }
        }

        // Check new ones
        val region = pos.expandToBoundingBox(2, 3, 2)
        invalidate(region)
        region.cachedUpdate()
    }

    private fun invalidate(region: BoundingBox) {
        holes.removeIf { it.positions.intersects(region) }
    }

    fun BoundingBox.cachedUpdate() {
        val occupiedHoles = if (holes.size >= 32) {
            holes.subSet(
                Hole.OneByOne(BlockPos(minX() - 2, minY() - 2, minZ() - 2)), true,
                Hole.OneByOne(BlockPos(maxX() + 2, maxY() + 2, maxZ() + 2)), true
            )
        } else {
            holes
        }

        HoleRegionScanner(world.maxY - 2, ::classify).scan(this, occupiedHoles, holes)
    }

    private fun classify(pos: BlockPos): HoleCell? {
        val state = pos.state ?: return null
        return when {
            state.isAir -> HoleCell.AIR
            state.block in BLAST_RESISTANT_BLOCKS -> HoleCell.BLAST_RESISTANT
            state.block in INDESTRUCTIBLE_BLOCKS -> HoleCell.INDESTRUCTIBLE
            else -> HoleCell.BREAKABLE
        }
    }

    override fun chunkUpdate(chunk: LevelChunk) {
        val region = chunk.toBlockBox()
        if (region.intersects(HoleManager.movableRegionScanner.currentRegion)) {
            invalidate(region)
            region.cachedUpdate()
        }
    }

    override fun clearChunk(pos: ChunkPos) {
        invalidate(pos.toBlockBox())
    }

    override fun clearAllChunks() {
        holes.clear()
    }

}
