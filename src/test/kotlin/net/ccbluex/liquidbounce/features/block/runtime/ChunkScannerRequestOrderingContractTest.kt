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
package net.ccbluex.liquidbounce.features.block.runtime

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ChunkScannerRequestOrderingContractTest {

    @Test
    fun `scanner keeps its public request and subscriber facade`() {
        val scanner = Files.readString(SCANNER_SOURCE)

        PUBLIC_FACADE_MARKERS.forEach { marker ->
            assertTrue(marker in scanner, "ChunkScanner must retain `$marker`")
        }
    }

    @Test
    fun `extracted processor preserves subscriber callback order`() {
        assertTrue(Files.isRegularFile(PROCESSOR_SOURCE), "$PROCESSOR_SOURCE must own request execution")
        val processor = Files.readString(PROCESSOR_SOURCE)
        val subscriberReplay = processor.substringBetween(
            "suspend fun replaySubscriber(",
            "suspend fun loadChunk(",
        )
        assertInOrder(
            subscriberReplay,
            "subscriber.chunkUpdate(it)",
            "subscriber.shouldCallRecordBlockOnChunkUpdate",
            "scanChunkSections(it)",
            "subscriber.recordBlock(pos, state, cleared = true)",
        )

        val chunkLoad = processor.substringBetween(
            "suspend fun loadChunk(",
            "fun updateSection(",
        )
        assertInOrder(
            chunkLoad,
            "it.clearChunk(chunk.pos)",
            "it.chunkUpdate(chunk)",
            ".joinAll()",
            "subscribers.filter",
            "scanChunkSections(chunk)",
            "subscribersForRecordBlock.forEach",
        )
    }

    @Test
    fun `world reset cancels work before clearing scanner state`() {
        val scanner = Files.readString(SCANNER_SOURCE)
        val worldReset = scanner.substringBetween(
            "private val worldChangeHandler",
            "private val dispatcher",
        )

        assertInOrder(
            worldReset,
            "cancelCurrentJobs()",
            "loadedChunks.clear()",
            "subscribers.forEach(BlockChangeSubscriberContract::clearAllChunks)",
        )
    }

    private fun String.substringBetween(start: String, end: String): String =
        substringAfter(start).substringBefore(end)

    private fun assertInOrder(source: String, vararg markers: String) {
        var cursor = 0
        markers.forEach { marker ->
            val index = source.indexOf(marker, cursor)
            assertTrue(index >= cursor, "Expected `$marker` after offset $cursor")
            cursor = index + marker.length
        }
    }

    private companion object {
        val RUNTIME_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/block/runtime",
        )
        val SCANNER_SOURCE: Path = RUNTIME_ROOT.resolve("ChunkScanner.kt")
        val PROCESSOR_SOURCE: Path = RUNTIME_ROOT.resolve("RegionUpdateProcessor.kt")
        val PUBLIC_FACADE_MARKERS = listOf(
            "fun subscribe(newSubscriber: BlockChangeSubscriber)",
            "fun subscribe(newSubscriber: BlockChangeSubscriberContract)",
            "fun unsubscribe(oldSubscriber: BlockChangeSubscriber)",
            "fun unsubscribe(oldSubscriber: BlockChangeSubscriberContract)",
            "val scope = CoroutineScope",
            "sealed interface UpdateRequest",
            "interface BlockChangeSubscriber : BlockChangeSubscriberContract",
        )
    }
}
