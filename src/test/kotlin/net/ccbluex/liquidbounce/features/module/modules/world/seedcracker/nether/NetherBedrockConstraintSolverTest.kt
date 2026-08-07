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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NetherBedrockConstraintSolverTest {

    @Test
    fun `reference Java 26_2 rule reproduces the published forward floor and roof seed vector`() {
        val worldSeed = 765_906_787_396_911_863L
        val expectedRoofPatternSeed = 191_924_403_737_289L
        val expectedFloorPatternSeed = 18_240_473_916_414L

        assertEquals(
            expectedRoofPatternSeed,
            NetherBedrockJava26_2Rule.patternSeedFromWorldSeed(worldSeed, NetherBedrockLayer.ROOF),
        )
        assertEquals(
            expectedFloorPatternSeed,
            NetherBedrockJava26_2Rule.patternSeedFromWorldSeed(worldSeed, NetherBedrockLayer.FLOOR),
        )
    }

    @Test
    fun `reference rule accepts the published floor-bedrock pattern cells`() {
        val patternSeed = 9_210_758_467_792_927_021L and MASK_48

        publishedFloorCells().forEach { constraint ->
            assertTrue(NetherBedrockJava26_2Rule.matches(patternSeed, constraint), constraint.toString())
        }
    }

    @Test
    fun `searching a bounded prefix range finds and held-out validates the known pattern seed`() {
        val patternSeed = 9_210_758_467_792_927_021L and MASK_48
        val cells = publishedFloorCells()
        val prefix = patternSeed ushr NetherBedrockPrefixRange.LOWER_BITS
        val request = NetherBedrockLayerSearchRequest(
            layer = NetherBedrockLayer.FLOOR,
            constraints = cells.take(20),
            heldOutConstraints = cells.drop(20),
            range = NetherBedrockPrefixRange(prefix, prefix + 1L),
        )

        val outcome = NetherBedrockConstraintSolver.search(request)

        val progress = assertIs<NetherBedrockLayerSearchOutcome.Progress>(outcome)
        assertEquals(1L, progress.checkedPrefixes)
        assertTrue(
            progress.candidates.any {
                it.patternSeed == patternSeed && it.verification == NetherBedrockVerification.HELD_OUT_VALIDATED
            },
        )
    }

    @Test
    fun `a flipped held-out cell is never reported as validated`() {
        val patternSeed = 9_210_758_467_792_927_021L and MASK_48
        val cells = publishedFloorCells()
        val prefix = patternSeed ushr NetherBedrockPrefixRange.LOWER_BITS
        val rejectedHeldOut = cells.last().copy(isBedrock = false)
        val request = NetherBedrockLayerSearchRequest(
            layer = NetherBedrockLayer.FLOOR,
            constraints = cells.dropLast(1),
            heldOutConstraints = listOf(rejectedHeldOut),
            range = NetherBedrockPrefixRange(prefix, prefix + 1L),
        )

        val outcome = NetherBedrockConstraintSolver.search(request)

        val progress = assertIs<NetherBedrockLayerSearchOutcome.Progress>(outcome)
        assertTrue(progress.candidates.none { it.patternSeed == patternSeed })
    }

    @Test
    fun `start gate requires two complete independent chunks before scheduling a solver`() {
        val oneChunk = listOf(emptyChunk(0, 0))

        val gate = NetherBedrockConstraintSolver.startGate(oneChunk)

        val needsMore = assertIs<NetherBedrockStartGate.NeedsMoreInformation>(gate)
        assertEquals(NetherBedrockNeed.NEED_SECOND_COMPLETE_CHUNK, needsMore.reason)
        assertEquals(1, needsMore.distinctChunkCount)
    }

    @Test
    fun `start gate accepts two complete chunks when either layer is below the candidate budget`() {
        val gate = NetherBedrockConstraintSolver.startGate(listOf(emptyChunk(0, 0), emptyChunk(1, 0)))

        assertIs<NetherBedrockStartGate.Ready>(gate)
    }

    @Test
    fun `candidate limit prevents an unbounded result set`() {
        val seed = 9_210_758_467_792_927_021L and MASK_48
        val prefix = seed ushr NetherBedrockPrefixRange.LOWER_BITS
        val request = NetherBedrockLayerSearchRequest(
            layer = NetherBedrockLayer.FLOOR,
            constraints = listOf(publishedFloorCells().first()),
            range = NetherBedrockPrefixRange(prefix, prefix + 1L),
            candidateLimit = 1,
        )

        val outcome = NetherBedrockConstraintSolver.search(request)

        assertIs<NetherBedrockLayerSearchOutcome.CandidateBudgetExceeded>(outcome)
    }

    @Test
    fun `cancellation is checked before a prefix is explored`() {
        val request = NetherBedrockLayerSearchRequest(
            layer = NetherBedrockLayer.FLOOR,
            constraints = publishedFloorCells(),
        )

        val outcome = NetherBedrockConstraintSolver.search(request, isCancelled = { true })

        val cancelled = assertIs<NetherBedrockLayerSearchOutcome.Cancelled>(outcome)
        assertEquals(0L, cancelled.checkedPrefixes)
        assertEquals(request.range, cancelled.nextRange)
    }

    @Test
    fun `world-seed inversion filters candidates through the forward rule before exposing them`() {
        val worldSeed = 5_354_280_283_422_356_689L
        val patternSeed = NetherBedrockJava26_2Rule.patternSeedFromWorldSeed(worldSeed, NetherBedrockLayer.ROOF)
        val inverter = NetherBedrockWorldSeedInverter { _, _, _ ->
            NetherBedrockWorldSeedInversion.Candidates(listOf(worldSeed, 0L), complete = true)
        }

        val inversion = NetherBedrockConstraintSolver.worldSeedCandidatesFromPatternSeed(
            patternSeed,
            NetherBedrockLayer.ROOF,
            inverter,
        )

        val candidates = assertIs<NetherBedrockWorldSeedInversion.Candidates>(inversion)
        assertEquals(listOf(worldSeed), candidates.seeds)
        assertTrue(!candidates.complete, "A rejected inverter result must not retain a complete assertion")
    }

    @Test
    fun `the default world-seed inverse never invents a full-world seed`() {
        val inversion = NetherBedrockConstraintSolver.worldSeedCandidatesFromPatternSeed(
            patternSeed = 1L,
            layer = NetherBedrockLayer.FLOOR,
        )

        assertEquals(NetherBedrockWorldSeedInversion.Unavailable, inversion)
    }

    @Test
    fun `two plane world search recovers and independently validates a signed world seed`() {
        val worldSeed = 765_906_787_396_911_863L
        val source = completeChunkFor(worldSeed, 0, 0)
        val heldOut = completeChunkFor(worldSeed, 1, -1)
        val prefixes = setOf(
            NetherBedrockJava26_2Rule.patternSeedFromWorldSeed(worldSeed, NetherBedrockLayer.FLOOR) ushr
                NetherBedrockPrefixRange.LOWER_BITS,
            NetherBedrockJava26_2Rule.patternSeedFromWorldSeed(worldSeed, NetherBedrockLayer.ROOF) ushr
                NetherBedrockPrefixRange.LOWER_BITS,
        )

        val candidates = prefixes.flatMap { prefix ->
            val outcome = NetherBedrockConstraintSolver.searchWorldSeeds(
                NetherBedrockWorldSeedSearchRequest(
                    sourceChunks = listOf(source),
                    heldOutChunks = listOf(heldOut),
                    range = NetherBedrockPrefixRange(prefix, prefix + 1L),
                ),
            )
            if (outcome is NetherBedrockWorldSeedSearchOutcome.Progress) outcome.candidates else emptyList()
        }

        assertTrue(
            candidates.any { candidate ->
                candidate.seed == worldSeed && candidate.verification == NetherBedrockVerification.HELD_OUT_VALIDATED
            },
        )
        assertEquals(
            listOf(worldSeed),
            candidates.map(NetherBedrockWorldSeedCandidate::seed).distinct().sorted(),
            "The known two-plane corpus must not promote an arbitrary inverse candidate",
        )
    }

    private fun completeChunkFor(worldSeed: Long, chunkX: Int, chunkZ: Int): NetherBedrockSolverChunk {
        val floorSeed = NetherBedrockJava26_2Rule.patternSeedFromWorldSeed(worldSeed, NetherBedrockLayer.FLOOR)
        val roofSeed = NetherBedrockJava26_2Rule.patternSeedFromWorldSeed(worldSeed, NetherBedrockLayer.ROOF)
        return NetherBedrockSolverChunk.fromPredicate(
            chunkX,
            chunkZ,
            floor = { x, z -> isBedrock(floorSeed, NetherBedrockLayer.FLOOR, chunkX * 16 + x, chunkZ * 16 + z) },
            roof = { x, z -> isBedrock(roofSeed, NetherBedrockLayer.ROOF, chunkX * 16 + x, chunkZ * 16 + z) },
        )
    }

    private fun isBedrock(patternSeed: Long, layer: NetherBedrockLayer, x: Int, z: Int): Boolean =
        NetherBedrockJava26_2Rule.matches(
            patternSeed,
            NetherBedrockCellConstraint(x, layer.blockY, z, isBedrock = true),
        )

    private fun emptyChunk(chunkX: Int, chunkZ: Int) = NetherBedrockSolverChunk.fromPredicate(
        chunkX,
        chunkZ,
        floor = { _, _ -> false },
        roof = { _, _ -> false },
    )

    private fun publishedFloorCells(): List<NetherBedrockCellConstraint> = listOf(
        -98 to -469,
        -101 to -465,
        -101 to -463,
        -101 to -457,
        -101 to -453,
        -100 to -456,
        -100 to -449,
        -99 to -464,
        -99 to -459,
        -99 to -455,
        -98 to -461,
        -98 to -460,
        -96 to -467,
        -96 to -465,
        -96 to -464,
        -96 to -452,
        -95 to -465,
        -95 to -458,
        -95 to -449,
        -94 to -462,
        -94 to -459,
        -94 to -454,
        -93 to -467,
        -93 to -465,
        -93 to -463,
        -93 to -455,
        -92 to -468,
        -92 to -467,
    ).map { (x, z) ->
        NetherBedrockCellConstraint(x, NetherBedrockLayer.FLOOR.blockY, z, isBedrock = true)
    }

    private companion object {
        const val MASK_48 = (1L shl 48) - 1L
    }
}
