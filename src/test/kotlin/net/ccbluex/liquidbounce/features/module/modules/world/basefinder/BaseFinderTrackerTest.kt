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
    fun `encased compact starter base remains detectable at sixty percent`() {
        val snapshot = BaseFinderTracker.scanBlocksForTest(
            buildList {
                add(BlockPos(0, -16, 0) to Blocks.CHEST.defaultBlockState())
                listOf(
                    BlockPos(-1, -16, 0),
                    BlockPos(1, -16, 0),
                    BlockPos(0, -17, 0),
                    BlockPos(0, -16, -1),
                    BlockPos(0, -16, 1),
                    BlockPos(0, -15, 0),
                ).forEach { add(it to Blocks.DEEPSLATE.defaultBlockState()) }
                add(BlockPos(3, -16, 0) to Blocks.SHULKER_BOX.defaultBlockState())
                add(BlockPos(4, -16, 0) to Blocks.FURNACE.defaultBlockState())
                add(BlockPos(2, -16, 2) to Blocks.CRAFTING_TABLE.defaultBlockState())
                add(BlockPos(3, -16, 2) to Blocks.BED.white().defaultBlockState())
                add(BlockPos(4, -16, 2) to Blocks.BED.white().defaultBlockState())
            },
        )

        assertTrue(snapshot.storage.anchors.any { it.key == "storage.chest" })
        assertEquals(setOf("crafting", "bed", "smelting"), snapshot.utilities.categories)
        assertTrue(snapshot.structural.bedGroup)

        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(snapshot),
            minimumConfidence = 60,
            highSensitivity = true,
        )
        assertEquals(61, candidate.confidence)
        assertTrue(candidate.accepted)
    }

    @Test
    fun `active player portal with a chest is not classified as a ruined portal`() {
        val snapshot = BaseFinderTracker.scanBlocksForTest(
            buildList {
                repeat(10) { index ->
                    add(BlockPos(index, 64, 0) to Blocks.OBSIDIAN.defaultBlockState())
                }
                repeat(3) { offset ->
                    add(BlockPos(0, 65 + offset, 2) to Blocks.NETHER_PORTAL.defaultBlockState())
                    add(BlockPos(1, 65 + offset, 2) to Blocks.NETHER_PORTAL.defaultBlockState())
                }
                add(BlockPos(3, 64, 4) to Blocks.CHEST.defaultBlockState())
            },
        )

        assertFalse(BaseFalsePositive.RUINED_PORTAL in snapshot.falsePositives)

        val candidate = BaseFinderScorer.scoreCluster(
            snapshots = listOf(snapshot),
            minimumConfidence = 0,
            highSensitivity = true,
        )
        assertEquals(13, candidate.confidence)
        assertTrue(candidate.accepted)
    }

    @Test
    fun `unlit ruined portal context remains excluded`() {
        val snapshot = BaseFinderTracker.scanBlocksForTest(
            buildList {
                repeat(8) { index ->
                    add(BlockPos(index, 64, 0) to Blocks.OBSIDIAN.defaultBlockState())
                }
                add(BlockPos(0, 64, 2) to Blocks.NETHERRACK.defaultBlockState())
                add(BlockPos(1, 64, 2) to Blocks.GOLD_BLOCK.defaultBlockState())
                add(BlockPos(2, 64, 2) to Blocks.CHEST.defaultBlockState())
            },
        )

        assertTrue(BaseFalsePositive.RUINED_PORTAL in snapshot.falsePositives)
        assertFalse(
            BaseFinderScorer.scoreCluster(
                snapshots = listOf(snapshot),
                minimumConfidence = 0,
                highSensitivity = true,
            ).accepted,
        )
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
