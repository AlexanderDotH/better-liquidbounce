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

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseFinderSeedRuntimeTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `detached debug channel suppresses worker messages and their formatting`() {
        val channel = BaseFinderSeedDebugChannel()
        val messages = mutableListOf<String>()
        var formattedMessages = 0

        channel.emit {
            formattedMessages++
            "before-enable"
        }
        channel.setListener(messages::add)
        channel.emit {
            formattedMessages++
            "while-enabled"
        }
        channel.setListener(null)
        channel.emit {
            formattedMessages++
            "after-disable"
        }

        assertEquals(listOf("while-enabled"), messages)
        assertEquals(1, formattedMessages)
    }

    @Test
    fun `runtime stays inactive without a configured seed`() {
        val runtime = BaseFinderSeedRuntime()
        runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "",
                enabled = true,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        assertFalse(runtime.isActive())
    }

    @Test
    fun `world change clears published signals`() {
        val runtime = BaseFinderSeedRuntime()
        runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "12345",
                enabled = true,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        val chunk = ChunkCoordinate(2, 3)
        runtime.putSignalForTest(
            chunk,
            SeedMismatchSignal(unexpectedSolidCount = 4, phase = SeedComparePhase.SPARSE),
        )
        assertTrue(runtime.signalFor(chunk)!!.hasEvidence)

        runtime.onWorldChanged(7L)
        assertNull(runtime.signalFor(chunk))
        assertEquals(0, runtime.pendingSizeForTest())
        assertEquals(0, runtime.promotionSizeForTest())
        assertEquals(0, runtime.cacheSizeForTest())
    }

    @Test
    fun `manual cache clear drops compare work and keeps the configured runtime active`() {
        val runtime = BaseFinderSeedRuntime()
        runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "12345",
                enabled = true,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        runtime.onWorldChanged(7L)
        val chunk = ChunkCoordinate(2, 3)
        val ticket = BaseFinderScanTicket(chunk, worldEpoch = 7L, revision = 1L)
        val observed = ObservedChunkBlocks(
            chunk = chunk,
            minY = 0,
            height = 1,
            columns = mapOf(0 to intArrayOf(0)),
        )
        runtime.putSignalForTest(
            chunk,
            SeedMismatchSignal(unexpectedSolidCount = 4, phase = SeedComparePhase.OVERLAY),
        )
        runtime.offer(
            BaseFinderSeedCompareOffer(
                ticket = ticket,
                dimensionKey = "minecraft:overworld",
                observed = observed,
                heuristicPriority = false,
            )
        )
        runtime.offer(
            BaseFinderSeedCompareOffer(
                ticket = ticket,
                dimensionKey = "minecraft:overworld",
                observed = observed,
                heuristicPriority = false,
                overlayLocals = listOf(0 to 0),
            )
        )

        runtime.clearCache()

        assertTrue(runtime.isActive())
        assertFalse(runtime.isContextReady())
        assertNull(runtime.signalFor(chunk))
        assertEquals(0, runtime.pendingSizeForTest())
        assertEquals(0, runtime.debugSnapshot().overlayQueued)
        assertEquals(0, runtime.promotionSizeForTest())
        assertEquals(0, runtime.cacheSizeForTest())
    }

    @Test
    fun `sparse signal does not satisfy overlay ticket readiness`() {
        val runtime = BaseFinderSeedRuntime()
        runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "12345",
                enabled = true,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        runtime.onWorldChanged(1L)
        val chunk = ChunkCoordinate(0, 0)
        val ticket = BaseFinderScanTicket(chunk, worldEpoch = 1L, revision = 4L)
        runtime.putSignalForTest(
            chunk,
            SeedMismatchSignal(
                unexpectedSolidCount = 2,
                sampledColumns = 16,
                phase = SeedComparePhase.SPARSE,
            ),
        )
        // Revision match alone is not enough for overlay skip — phase/columns must be overlay-complete.
        runtime.putSignalRevisionForTest(chunk, ticket.revision)
        assertTrue(runtime.hasSignalForTicket(ticket))
        assertFalse(runtime.hasOverlaySignalForTicket(ticket))

        runtime.putSignalForTest(
            chunk,
            SeedMismatchSignal(
                unexpectedSolidCount = 2,
                sampledColumns = 256,
                phase = SeedComparePhase.OVERLAY,
            ),
        )
        assertTrue(runtime.hasOverlaySignalForTicket(ticket))
    }

    @Test
    fun `seed text change clears signals and queued work`() {
        val runtime = BaseFinderSeedRuntime()
        runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "111",
                enabled = true,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        runtime.onWorldChanged(1L)
        val chunk = ChunkCoordinate(0, 0)
        runtime.putSignalForTest(
            chunk,
            SeedMismatchSignal(unexpectedSolidCount = 3, phase = SeedComparePhase.OVERLAY),
        )
        runtime.offer(
            BaseFinderSeedCompareOffer(
                ticket = BaseFinderScanTicket(chunk, worldEpoch = 1L, revision = 1L),
                dimensionKey = "minecraft:overworld",
                observed = ObservedChunkBlocks(
                    chunk = chunk,
                    minY = 0,
                    height = 1,
                    columns = mapOf(0 to intArrayOf(0)),
                ),
                heuristicPriority = false,
                overlayLocals = listOf(0 to 0),
            )
        )
        assertTrue(runtime.signalFor(chunk)!!.hasEvidence)
        assertEquals(1, runtime.debugSnapshot().overlayQueued)

        val changed = runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "222",
                enabled = true,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        assertTrue(changed)
        assertNull(runtime.signalFor(chunk))
        assertEquals(0, runtime.pendingSizeForTest())
        assertEquals(0, runtime.debugSnapshot().overlayQueued)
    }

    @Test
    fun `backend change clears signals and queued work`() {
        val runtime = BaseFinderSeedRuntime()
        runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "111",
                enabled = true,
                backend = BaseFinderWorldBackend.FEATURES,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        runtime.onWorldChanged(1L)
        val chunk = ChunkCoordinate(1, 1)
        runtime.putSignalForTest(
            chunk,
            SeedMismatchSignal(unexpectedSolidCount = 5, phase = SeedComparePhase.OVERLAY),
        )
        runtime.offer(
            BaseFinderSeedCompareOffer(
                ticket = BaseFinderScanTicket(chunk, worldEpoch = 1L, revision = 2L),
                dimensionKey = "minecraft:overworld",
                observed = ObservedChunkBlocks(
                    chunk = chunk,
                    minY = 0,
                    height = 1,
                    columns = mapOf(0 to intArrayOf(0)),
                ),
                heuristicPriority = false,
                overlayLocals = listOf(0 to 0),
            )
        )
        assertTrue(runtime.signalFor(chunk)!!.hasEvidence)

        val changed = runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "111",
                enabled = true,
                backend = BaseFinderWorldBackend.BASE_COLUMN,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        assertTrue(changed)
        assertNull(runtime.signalFor(chunk))
        assertEquals(0, runtime.debugSnapshot().overlayQueued)
    }

    @Test
    fun `offers are ignored while inactive`() {
        val runtime = BaseFinderSeedRuntime()
        runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "",
                enabled = true,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        runtime.offer(
            BaseFinderSeedCompareOffer(
                ticket = BaseFinderScanTicket(ChunkCoordinate(0, 0), worldEpoch = 1L, revision = 1L),
                dimensionKey = "minecraft:overworld",
                observed = ObservedChunkBlocks(
                    chunk = ChunkCoordinate(0, 0),
                    minY = 0,
                    height = 1,
                    columns = mapOf(0 to intArrayOf(0)),
                ),
                heuristicPriority = true,
            )
        )
        assertEquals(0, runtime.pendingSizeForTest())
    }

    @Test
    fun `duplicate sparse offers for one ticket are coalesced while queued`() {
        val runtime = BaseFinderSeedRuntime()
        runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "12345",
                enabled = true,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        runtime.onWorldChanged(1L)
        val chunk = ChunkCoordinate(4, -3)
        val offer = BaseFinderSeedCompareOffer(
            ticket = BaseFinderScanTicket(chunk, worldEpoch = 1L, revision = 7L),
            dimensionKey = "minecraft:overworld",
            observed = ObservedChunkBlocks(
                chunk = chunk,
                minY = 0,
                height = 1,
                columns = mapOf(0 to intArrayOf(0)),
            ),
            heuristicPriority = true,
        )

        runtime.offer(offer)
        runtime.offer(offer)

        assertEquals(1, runtime.pendingSizeForTest())
        assertTrue(runtime.hasSparseWorkForTicket(offer.ticket))
    }

    @Test
    fun `queued overlay work is visible for tick level coalescing`() {
        val runtime = BaseFinderSeedRuntime()
        runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "12345",
                enabled = true,
                workerThreads = 1,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        runtime.onWorldChanged(1L)
        val chunk = ChunkCoordinate(-8, 6)
        val ticket = BaseFinderScanTicket(chunk, worldEpoch = 1L, revision = 2L)
        runtime.offer(
            BaseFinderSeedCompareOffer(
                ticket = ticket,
                dimensionKey = "minecraft:overworld",
                observed = ObservedChunkBlocks(
                    chunk = chunk,
                    minY = 0,
                    height = 1,
                    columns = mapOf(0 to intArrayOf(0)),
                ),
                heuristicPriority = true,
                overlayLocals = listOf(0 to 0),
            )
        )

        assertTrue(runtime.hasOverlayWorkForTicket(ticket))
        runtime.clearCache()
        assertFalse(runtime.hasOverlayWorkForTicket(ticket))
    }

    @Test
    fun `prioritized overlay chunks keep the player chunk first`() {
        val player = ChunkCoordinate(0, 0)
        val targets = spiralChunksAround(player, 9)
        val ordered = prioritizedOverlayChunks(targets, ringStart = 3)
        assertEquals(player, ordered.first())
        assertEquals(targets.size, ordered.size)
        assertEquals(ordered.size, ordered.toSet().size)
    }

    @Test
    fun `overlay refresh cursor eventually visits every neighboring chunk`() {
        val targets = chunksInChebyshevRadius(ChunkCoordinate(0, 0), 6)
        val ring = targets.drop(1)
        var cursor = 0
        val visited = linkedSetOf<ChunkCoordinate>()

        repeat((ring.size + 2) / 3) {
            val refreshed = prioritizedOverlayChunks(targets, ringStart = cursor).drop(1).take(3)
            visited += refreshed
            cursor = advanceOverlayRefreshCursor(cursor, ring.size, refreshed.size)
        }

        assertEquals(ring.toSet(), visited)
    }

    @Test
    fun `spiralChunksAround includes origin and respects the chunk cap`() {
        val origin = ChunkCoordinate(10, -4)
        assertEquals(listOf(origin), spiralChunksAround(origin, 1))

        val nine = spiralChunksAround(origin, 9)
        assertEquals(9, nine.size)
        assertEquals(origin, nine.first())
        assertEquals(nine.size, nine.toSet().size)

        val thirtyTwo = spiralChunksAround(origin, 32)
        assertEquals(32, thirtyTwo.size)
        assertEquals(thirtyTwo.size, thirtyTwo.toSet().size)
        assertEquals(origin, thirtyTwo.first())
    }

    @Test
    fun `chunksInChebyshevRadius fills the square including origin first`() {
        val origin = ChunkCoordinate(0, 0)
        assertEquals(listOf(origin), chunksInChebyshevRadius(origin, 0))

        val radiusOne = chunksInChebyshevRadius(origin, 1)
        assertEquals(9, radiusOne.size)
        assertEquals(origin, radiusOne.first())
        assertEquals(radiusOne.size, radiusOne.toSet().size)
        assertTrue(radiusOne.contains(ChunkCoordinate(1, 1)))
        assertTrue(radiusOne.contains(ChunkCoordinate(-1, 0)))

        val radiusTen = chunksInChebyshevRadius(origin, 10)
        assertEquals(21 * 21, radiusTen.size)
        assertEquals(origin, radiusTen.first())
        assertEquals((10 + 1) * 16.0, seedMismatchMaxDistanceBlocks(10))
    }

    @Test
    fun `retainChunks drops signals outside the active scan set`() {
        val runtime = BaseFinderSeedRuntime()
        runtime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = "12345",
                enabled = true,
                workerThreads = 2,
                promotionsPerTick = 1,
                sparseSamplesPerChunk = 16,
                cacheChunks = 32,
            )
        )
        val keep = ChunkCoordinate(0, 0)
        val drop = ChunkCoordinate(3, 3)
        runtime.putSignalForTest(keep, SeedMismatchSignal(unexpectedSolidCount = 1))
        runtime.putSignalForTest(drop, SeedMismatchSignal(unexpectedSolidCount = 2))

        runtime.retainChunks(setOf(keep))

        assertTrue(runtime.signalFor(keep)!!.hasEvidence)
        assertNull(runtime.signalFor(drop))
    }
}
