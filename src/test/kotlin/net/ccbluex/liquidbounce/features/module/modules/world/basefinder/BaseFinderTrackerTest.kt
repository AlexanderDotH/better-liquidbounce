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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.minecraft.core.BlockPos
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaseFinderTrackerTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @AfterEach
    fun tearDown() {
        BaseFinderTracker.resetVolatile()
    }

    @Test
    fun `chunk updates are scanned authoritatively rather than replayed as block records`() {
        assertFalse(BaseFinderTracker.shouldCallRecordBlockOnChunkUpdate)
    }

    @Test
    fun `mutable block positions are reduced to immutable chunk keys immediately`() {
        val mutable = BlockPos.MutableBlockPos(17, 64, 33)

        BaseFinderTracker.recordBlock(mutable, Blocks.CHEST.defaultBlockState(), cleared = false)
        mutable.set(160, 64, 160)

        assertEquals(
            (-1..1).flatMap { dx -> (-1..1).map { dz -> ChunkCoordinate(1 + dx, 2 + dz) } }.toSet(),
            BaseFinderTracker.dirtyChunksForTest().toSet(),
        )
    }

    @Test
    fun `repeated changes in one chunk coalesce into one neighborhood`() {
        repeat(20) { offset ->
            BaseFinderTracker.recordBlock(
                BlockPos(offset and 15, 64, offset and 15),
                Blocks.REDSTONE_WIRE.defaultBlockState(),
                cleared = false,
            )
        }

        assertEquals(9, BaseFinderTracker.dirtyChunksForTest().size)
    }

    @Test
    fun `dirty work drain honors its per tick budget`() {
        repeat(5) { chunkX ->
            BaseFinderTracker.recordBlock(
                BlockPos(chunkX * 64, 64, 0),
                Blocks.CHEST.defaultBlockState(),
                cleared = false,
            )
        }

        val first = BaseFinderTracker.drainDirtyChunksForTest(2)
        val second = BaseFinderTracker.drainDirtyChunksForTest(2)

        assertEquals(2, first.size)
        assertEquals(2, second.size)
        assertEquals(41, BaseFinderTracker.dirtyChunksForTest().size)
    }

    @Test
    fun `world reset advances epoch and clears dirty work`() {
        BaseFinderTracker.recordBlock(BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), cleared = false)
        val before = BaseFinderTracker.worldEpoch

        val after = BaseFinderTracker.onWorldChanged()

        assertTrue(after > before)
        assertTrue(BaseFinderTracker.dirtyChunksForTest().isEmpty())
        assertTrue(BaseFinderTracker.currentSnapshots().isEmpty())
    }

    @Test
    fun `chunk clear invalidates an in flight revision`() {
        val chunk = ChunkCoordinate(4, -3)
        val ticket = BaseFinderTracker.scanTicketForTest(chunk)

        BaseFinderTracker.clearChunk(ChunkPos(chunk.x, chunk.z))

        assertFalse(BaseFinderTracker.isCurrentForTest(ticket))
    }

    @Test
    fun `world reset invalidates every in flight ticket`() {
        val ticket = BaseFinderTracker.scanTicketForTest(ChunkCoordinate(0, 0))

        BaseFinderTracker.onWorldChanged()

        assertFalse(BaseFinderTracker.isCurrentForTest(ticket))
    }

    @Test
    fun `geometry alignment counts distinct coordinates only`() {
        val duplicate = BaseCoordinate(4, 64, 4)

        assertFalse(BaseFinderTracker.hasAlignedRunForTest(List(12) { duplicate }, minimum = 4))
        assertTrue(
            BaseFinderTracker.hasAlignedRunForTest(
                listOf(
                    BaseCoordinate(1, 64, 4),
                    BaseCoordinate(2, 64, 4),
                    BaseCoordinate(3, 64, 4),
                    BaseCoordinate(4, 64, 4),
                ),
                minimum = 4,
            )
        )
    }

    @Test
    fun `container entities add storage evidence at weight three`() {
        val evidence = BaseFinderTracker.entityEvidenceForTest(
            listOf(
                BaseFinderEntityCategory.CONTAINER_VEHICLE to BaseCoordinate(2, 64, 3),
                BaseFinderEntityCategory.CHESTED_MOUNT to BaseCoordinate(4, 64, 5),
                BaseFinderEntityCategory.ARMOR_STAND to BaseCoordinate(6, 64, 7),
            )
        )

        assertEquals(6, evidence.storage.weightedPoints)
        assertEquals(2, evidence.storage.anchors.size)
        assertTrue(evidence.entities.hasContainerVehicleOrChestedMount)
    }

    @Test
    fun `expired activity records are removed from live snapshots`() {
        BaseFinderTracker.recordActivity(
            BaseFinderActivitySample(
                soundPath = "minecraft:block.piston.extend",
                position = BaseCoordinate(0, 64, 0),
                timestampMillis = 1L,
            )
        )

        BaseFinderTracker.currentSnapshots()

        assertTrue(BaseFinderTracker.currentSnapshots().isEmpty())
    }
}
