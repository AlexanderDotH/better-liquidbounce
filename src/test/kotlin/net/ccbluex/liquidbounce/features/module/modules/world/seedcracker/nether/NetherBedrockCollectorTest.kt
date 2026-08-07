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
 */
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.ChunkCoordinate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackScope
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceId
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.GenerationProfile
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.NetherBedrockBitPlane
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.NetherBedrockChunkObservation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetherBedrockCollectorTest {

    @Test
    fun `a recorded chunk retains all positive and negative cells in both informative layers`() {
        val collector = NetherBedrockCollector()

        val change = collector.record(
            snapshot(
                chunkX = 3,
                chunkZ = -2,
                revision = 7,
                floorBedrock = setOf(0 to 0, 15 to 15),
                roofBedrock = setOf(1 to 2),
            ),
        )

        val observation = change.observation
        assertEquals(ChunkCoordinate(3, -2), observation.chunk)
        assertEquals(7, observation.revision)
        assertTrue(observation.floor.isBedrock(0, 0))
        assertTrue(observation.floor.isBedrock(15, 15))
        assertFalse(observation.floor.isBedrock(1, 0))
        assertTrue(observation.roof.isBedrock(1, 2))
        assertFalse(observation.roof.isBedrock(2, 1))
        assertEquals(2, observation.floor.bedrockCount())
        assertEquals(254, observation.floor.nonBedrockCount())
        assertEquals(1, observation.roof.bedrockCount())
        assertEquals(255, observation.roof.nonBedrockCount())
    }

    @Test
    fun `an equal revision with identical planes is deduplicated`() {
        val collector = NetherBedrockCollector()
        val snapshot = snapshot(chunkX = 0, chunkZ = 0, revision = 11, floorBedrock = setOf(0 to 0))

        val first = collector.record(snapshot)
        val duplicate = collector.record(snapshot)

        assertTrue(first.changed)
        assertFalse(duplicate.changed)
        assertEquals(first.observation, duplicate.observation)
        assertEquals(listOf(first.observation), collector.observations(TEST_SCOPE))
    }

    @Test
    fun `a newer revision with identical planes advances the ticket without changing solver evidence`() {
        val collector = NetherBedrockCollector()
        collector.record(snapshot(chunkX = 0, chunkZ = 0, revision = 11, floorBedrock = setOf(0 to 0)))

        val deduplicated = collector.record(
            snapshot(chunkX = 0, chunkZ = 0, revision = 12, floorBedrock = setOf(0 to 0)),
        )

        assertFalse(deduplicated.changed)
        assertEquals(12, deduplicated.observation.revision)
        assertTrue(deduplicated.observation.floor.isBedrock(0, 0))
    }

    @Test
    fun `a newer chunk revision replaces the previous immutable observation`() {
        val collector = NetherBedrockCollector()

        collector.record(snapshot(chunkX = -4, chunkZ = 9, revision = 3, floorBedrock = setOf(1 to 1)))
        val replacement = collector.record(
            snapshot(
                chunkX = -4,
                chunkZ = 9,
                revision = 4,
                floorBedrock = setOf(2 to 2),
                roofBedrock = setOf(15 to 0),
            ),
        )

        assertTrue(replacement.changed)
        assertEquals(4, replacement.observation.revision)
        assertFalse(replacement.observation.floor.isBedrock(1, 1))
        assertTrue(replacement.observation.floor.isBedrock(2, 2))
        assertTrue(replacement.observation.roof.isBedrock(15, 0))
        assertTrue(replacement.observation.capturedOrder > 1L)
        assertEquals(replacement.observation, collector.observation(TEST_SCOPE, ChunkCoordinate(-4, 9)))
    }

    @Test
    fun `restored evidence keeps its order and makes later chunks eligible as held-out evidence`() {
        val collector = NetherBedrockCollector()
        val restored = NetherBedrockChunkObservation(
            id = EvidenceId("nether:restored"),
            scope = TEST_SCOPE,
            chunk = ChunkCoordinate(0, 0),
            revision = 1L,
            floor = NetherBedrockBitPlane.empty(),
            roof = NetherBedrockBitPlane.empty(),
            capturedOrder = 41L,
        )

        collector.restore(listOf(restored))
        val later = collector.record(snapshot(chunkX = 1, chunkZ = 1, revision = 1)).observation

        assertEquals(41L, collector.observation(TEST_SCOPE, restored.chunk)?.capturedOrder)
        assertEquals(42L, later.capturedOrder)
    }

    @Test
    fun `stale and conflicting revisions cannot overwrite current evidence`() {
        val collector = NetherBedrockCollector()
        collector.record(snapshot(chunkX = 2, chunkZ = 4, revision = 8, floorBedrock = setOf(1 to 1)))

        val stale = collector.record(snapshot(chunkX = 2, chunkZ = 4, revision = 7, floorBedrock = setOf(2 to 2)))
        val conflict = collector.record(snapshot(chunkX = 2, chunkZ = 4, revision = 8, floorBedrock = setOf(3 to 3)))

        assertFalse(stale.changed)
        assertFalse(conflict.changed)
        assertTrue(collector.observation(TEST_SCOPE, ChunkCoordinate(2, 4))!!.floor.isBedrock(1, 1))
        assertFalse(collector.observation(TEST_SCOPE, ChunkCoordinate(2, 4))!!.floor.isBedrock(2, 2))
        assertFalse(collector.observation(TEST_SCOPE, ChunkCoordinate(2, 4))!!.floor.isBedrock(3, 3))
    }

    @Test
    fun `remove and clear discard only the requested immutable observations`() {
        val collector = NetherBedrockCollector()
        val first = collector.record(snapshot(chunkX = 1, chunkZ = 1, revision = 1)).observation
        collector.record(snapshot(chunkX = 2, chunkZ = 2, revision = 1))

        assertTrue(collector.remove(TEST_SCOPE, ChunkCoordinate(1, 1)))
        assertNull(collector.observation(TEST_SCOPE, ChunkCoordinate(1, 1)))
        assertEquals(listOf(ChunkCoordinate(2, 2)), collector.observations(TEST_SCOPE).map { it.chunk })
        assertFalse(collector.remove(TEST_SCOPE, ChunkCoordinate(1, 1)))

        collector.clear()

        assertTrue(collector.observations(TEST_SCOPE).isEmpty())
        assertFalse(collector.remove(TEST_SCOPE, first.chunk))
    }

    @Test
    fun `the same chunk coordinate in another scope is retained independently`() {
        val collector = NetherBedrockCollector()
        val first = collector.record(snapshot(chunkX = 7, chunkZ = -6, revision = 3, floorBedrock = setOf(0 to 0)))
        val second = collector.record(
            snapshot(
                chunkX = 7,
                chunkZ = -6,
                revision = 3,
                floorBedrock = setOf(15 to 15),
                scope = OTHER_SCOPE,
            ),
        )

        assertTrue(first.changed)
        assertTrue(second.changed)
        assertTrue(collector.observation(TEST_SCOPE, ChunkCoordinate(7, -6))!!.floor.isBedrock(0, 0))
        assertTrue(collector.observation(OTHER_SCOPE, ChunkCoordinate(7, -6))!!.floor.isBedrock(15, 15))
    }

    private fun snapshot(
        chunkX: Int,
        chunkZ: Int,
        revision: Long,
        floorBedrock: Set<Pair<Int, Int>> = emptySet(),
        roofBedrock: Set<Pair<Int, Int>> = emptySet(),
        scope: CrackScope = TEST_SCOPE,
    ) = NetherBedrockChunkSnapshot(
        scope = scope,
        chunk = ChunkCoordinate(chunkX, chunkZ),
        revision = revision,
        floor = plane(floorBedrock),
        roof = plane(roofBedrock),
    )

    private fun plane(bedrockCoordinates: Set<Pair<Int, Int>>) = NetherBedrockBitPlane.fromPredicate { x, z ->
        (x to z) in bedrockCoordinates
    }

    private fun NetherBedrockBitPlane.bedrockCount(): Int = toWords().sumOf(Long::countOneBits)

    private fun NetherBedrockBitPlane.nonBedrockCount(): Int = 256 - bedrockCount()

    private companion object {
        val TEST_SCOPE = CrackScope(
            serverKey = "test-server",
            dimensionKey = "minecraft:the_nether",
            generationProfile = GenerationProfile.JAVA_26_2,
        )

        val OTHER_SCOPE = CrackScope(
            serverKey = "other-server",
            dimensionKey = "minecraft:the_nether",
            generationProfile = GenerationProfile.JAVA_26_2,
        )
    }
}
