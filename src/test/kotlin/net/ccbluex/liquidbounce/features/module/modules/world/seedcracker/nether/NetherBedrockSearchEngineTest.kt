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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class NetherBedrockSearchEngineTest {

    @Test
    fun `four workers receive contiguous non-overlapping prefix ranges`() {
        val ranges = NetherBedrockSearchEngine.partitionRanges(
            nextPrefix = 100L,
            workerCount = 4,
            prefixesPerWorker = 25L,
        )

        assertEquals(
            listOf(
                NetherBedrockPrefixRange(100L, 125L),
                NetherBedrockPrefixRange(125L, 150L),
                NetherBedrockPrefixRange(150L, 175L),
                NetherBedrockPrefixRange(175L, 200L),
            ),
            ranges,
        )
    }

    @Test
    fun `last parallel batch never exceeds the complete search domain`() {
        val end = NetherBedrockPrefixRange.TOTAL_PREFIXES

        val ranges = NetherBedrockSearchEngine.partitionRanges(
            nextPrefix = end - 3L,
            workerCount = 8,
            prefixesPerWorker = 2L,
        )

        assertEquals(
            listOf(
                NetherBedrockPrefixRange(end - 3L, end - 1L),
                NetherBedrockPrefixRange(end - 1L, end),
            ),
            ranges,
        )
    }

    @Test
    fun `a candidate from an early batch remains progress until the full domain is checked`() = runTest {
        val candidate = NetherBedrockWorldSeedCandidate(
            seed = 42L,
            primaryLayer = NetherBedrockLayer.FLOOR,
            primaryPatternSeed = 7L,
            verification = NetherBedrockVerification.HELD_OUT_VALIDATED,
        )
        val source = emptyChunk(0, 0)

        val outcome = NetherBedrockSearchEngine.searchBatch(
            sourceChunks = listOf(source),
            heldOutChunks = listOf(emptyChunk(1, 0)),
            cursor = NetherBedrockSearchCursor(),
            workerCount = 2,
            prefixesPerWorker = 1L,
            rangeSearch = { request, _ ->
                NetherBedrockWorldSeedSearchOutcome.Progress(
                    candidates = listOf(candidate).takeIf { request.range.startInclusive == 0L }.orEmpty(),
                    nextRange = request.range.next(1L),
                    checkedPrefixes = 1L,
                )
            },
        )

        val progress = assertIs<NetherBedrockSearchBatchOutcome.Progress>(outcome)
        assertEquals(2L, progress.cursor.nextPrefix)
        assertEquals(listOf(candidate), progress.cursor.candidates)
    }

    private fun emptyChunk(chunkX: Int, chunkZ: Int) = NetherBedrockSolverChunk.fromPredicate(
        chunkX,
        chunkZ,
        floor = { _, _ -> false },
        roof = { _, _ -> false },
    )
}
