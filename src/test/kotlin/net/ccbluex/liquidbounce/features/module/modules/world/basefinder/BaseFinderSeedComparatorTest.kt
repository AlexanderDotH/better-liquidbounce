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
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Blocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("LargeClass")
class BaseFinderSeedComparatorTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `unexpected solid and utility mismatches are counted`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val stone = BuiltInRegistries.BLOCK.getId(Blocks.STONE)
        val chest = BuiltInRegistries.BLOCK.getId(Blocks.CHEST)
        val height = 4
        val packed = ObservedChunkBlocks.packLocal(0, 0)
        val observed = ObservedChunkBlocks(
            chunk = ChunkCoordinate(0, 0),
            minY = 0,
            height = height,
            columns = mapOf(packed to intArrayOf(air, chest, stone, air)),
        )
        val expected = ExpectedChunkBlocks(
            chunk = ChunkCoordinate(0, 0),
            minY = 0,
            height = height,
            columns = mapOf(packed to intArrayOf(air, air, stone, stone)),
        )

        val signal = BaseFinderSeedComparator.compare(observed, expected, SeedComparePhase.SPARSE)

        assertEquals(1, signal.utilityMismatchCount)
        assertEquals(1, signal.missingSolidCount)
        assertTrue(signal.hasEvidence)
        assertTrue(signal.anchors.isNotEmpty())
        assertEquals(
            listOf(SeedMismatchKind.UTILITY, SeedMismatchKind.MISSING_SOLID),
            signal.cells.map(SeedMismatchCell::kind),
        )
        assertEquals(BaseCoordinate(0, 1, 0), signal.cells[0].position)
        assertEquals(chest, signal.cells[0].observedBlockId)
        assertEquals(air, signal.cells[0].expectedBlockId)
        assertEquals(
            "0 1 0 utility: actual=minecraft:chest expected=minecraft:air",
            signal.cells[0].debugDescription(),
        )
        assertEquals(BaseCoordinate(0, 3, 0), signal.cells[1].position)
        assertEquals(air, signal.cells[1].observedBlockId)
        assertEquals(stone, signal.cells[1].expectedBlockId)
    }

    @Test
    fun `false positives keep confirmed structures and locally certain mineshafts`() {
        val heuristic = setOf(
            BaseFalsePositive.VILLAGE,
            BaseFalsePositive.HOMOGENEOUS_SIGNAL,
            BaseFalsePositive.MINESHAFT_OR_DUNGEON,
        )
        assertEquals(
            heuristic,
            BaseFinderSeedComparator.adjustFalsePositives(
                heuristic = heuristic,
                seedConfirmedStructures = emptySet(),
                seedStructureCheckActive = false,
            ),
        )
        val adjusted = BaseFinderSeedComparator.adjustFalsePositives(
            heuristic = heuristic,
            seedConfirmedStructures = setOf(BaseFalsePositive.VILLAGE),
            seedStructureCheckActive = true,
        )

        assertEquals(
            setOf(
                BaseFalsePositive.VILLAGE,
                BaseFalsePositive.MINESHAFT_OR_DUNGEON,
                BaseFalsePositive.HOMOGENEOUS_SIGNAL,
            ),
            adjusted,
        )
    }

    @Test
    fun `material comparison reports swapped solids without changing the score`() {
        val stone = BuiltInRegistries.BLOCK.getId(Blocks.STONE)
        val cobble = BuiltInRegistries.BLOCK.getId(Blocks.COBBLESTONE)
        val grass = BuiltInRegistries.BLOCK.getId(Blocks.GRASS_BLOCK)
        val path = BuiltInRegistries.BLOCK.getId(Blocks.DIRT_PATH)
        val packed = ObservedChunkBlocks.packLocal(2, 3)
        val observed = ObservedChunkBlocks(
            chunk = ChunkCoordinate(0, 0),
            minY = 64,
            height = 2,
            columns = mapOf(packed to intArrayOf(cobble, path)),
        )
        val expected = ExpectedChunkBlocks(
            chunk = ChunkCoordinate(0, 0),
            minY = 64,
            height = 2,
            columns = mapOf(packed to intArrayOf(stone, grass)),
            fidelity = ExpectedTerrainFidelity.FEATURES,
        )

        val ignored = BaseFinderSeedComparator.compare(observed, expected, SeedComparePhase.OVERLAY)
        assertEquals(0, ignored.materialSwapCount)
        assertTrue(ignored.cells.isEmpty())

        val compared = BaseFinderSeedComparator.compare(
            observed = observed,
            expected = expected,
            phase = SeedComparePhase.OVERLAY,
            compareMaterials = true,
        )
        // Only cobble-for-stone: grass→dirt_path is ticked-world drift.
        assertEquals(1, compared.materialSwapCount)
        assertEquals(
            listOf(SeedMismatchKind.MATERIAL_SWAP),
            compared.cells.map(SeedMismatchCell::kind),
        )
        assertEquals(BaseCoordinate(2, 64, 3), compared.cells[0].position)
        assertEquals(cobble, compared.cells[0].observedBlockId)
        assertEquals(stone, compared.cells[0].expectedBlockId)
        assertEquals(
            "2 64 3 material swap: actual=minecraft:cobblestone expected=minecraft:stone",
            compared.cells[0].debugDescription(),
        )
        // Overlay-only: no ratio, no evidence, no anchors.
        assertEquals(0.0, compared.mismatchRatio)
        assertFalse(compared.hasEvidence)
        assertTrue(compared.anchors.isEmpty())
    }

    @Test
    fun `material comparison stays off for base-column expectations`() {
        val stone = BuiltInRegistries.BLOCK.getId(Blocks.STONE)
        val cobble = BuiltInRegistries.BLOCK.getId(Blocks.COBBLESTONE)
        val packed = ObservedChunkBlocks.packLocal(0, 0)

        val signal = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 1,
                columns = mapOf(packed to intArrayOf(cobble)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 1,
                columns = mapOf(packed to intArrayOf(stone)),
            ),
            phase = SeedComparePhase.OVERLAY,
            compareMaterials = true,
        )

        assertEquals(0, signal.materialSwapCount)
        assertTrue(signal.cells.isEmpty())
    }

    @Test
    fun `heuristic priority always promotes sparse comparison and overlay never escalates`() {
        val quiet = SeedMismatchSignal(phase = SeedComparePhase.SPARSE, mismatchRatio = 0.0)
        assertTrue(BaseFinderSeedComparator.shouldPromoteToFull(quiet, hasHeuristicPriority = true))
        assertFalse(BaseFinderSeedComparator.shouldPromoteToFull(quiet, hasHeuristicPriority = false))

        val heuristicHit = SeedMismatchSignal(
            unexpectedSolidCount = 4,
            sampledColumns = 12,
            mismatchRatio = 0.02,
            phase = SeedComparePhase.SPARSE,
        )
        assertTrue(BaseFinderSeedComparator.shouldPromoteToFull(heuristicHit, hasHeuristicPriority = true))

        val noisy = SeedMismatchSignal(
            unexpectedSolidCount = 10,
            sampledColumns = 16,
            mismatchRatio = 0.1,
            phase = SeedComparePhase.SPARSE,
        )
        assertTrue(BaseFinderSeedComparator.shouldPromoteToFull(noisy, hasHeuristicPriority = false))

        val overlay = SeedMismatchSignal(
            unexpectedSolidCount = 10,
            mismatchRatio = 0.2,
            phase = SeedComparePhase.OVERLAY,
        )
        assertFalse(BaseFinderSeedComparator.shouldPromoteToFull(overlay, hasHeuristicPriority = true))
    }

    @Test
    fun `tree logs in air or against falling terrain are ignored but planks count`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val sand = BuiltInRegistries.BLOCK.getId(Blocks.SAND)
        val oakLog = BuiltInRegistries.BLOCK.getId(Blocks.OAK_LOG)
        val oakPlanks = BuiltInRegistries.BLOCK.getId(Blocks.OAK_PLANKS)
        val packed = ObservedChunkBlocks.packLocal(4, 4)

        val treeTrunk = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 1,
                columns = mapOf(packed to intArrayOf(oakLog)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 1,
                columns = mapOf(packed to intArrayOf(air)),
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(0, treeTrunk.unexpectedSolidCount)
        assertTrue(treeTrunk.cells.isEmpty())

        val againstSand = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 1,
                columns = mapOf(packed to intArrayOf(oakLog)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 1,
                columns = mapOf(packed to intArrayOf(sand)),
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(0, againstSand.unexpectedSolidCount)
        assertTrue(againstSand.cells.isEmpty())

        val planks = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 1,
                columns = mapOf(packed to intArrayOf(oakPlanks)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 1,
                columns = mapOf(packed to intArrayOf(air)),
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(1, planks.unexpectedSolidCount)
    }

    @Test
    fun `features fidelity suppresses unobserved tree drift`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val oakLog = BuiltInRegistries.BLOCK.getId(Blocks.OAK_LOG)

        val observedTree = compareFeatureCell(observedId = oakLog, expectedId = air)
        assertEquals(0, observedTree.unexpectedSolidCount)
        assertTrue(observedTree.cells.isEmpty())

        val expectedTree = compareFeatureCell(observedId = air, expectedId = oakLog)
        assertEquals(0, expectedTree.missingSolidCount)
        assertTrue(expectedTree.cells.isEmpty())
    }

    @Test
    fun `client observed tree growth and breaks remain ignored`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val oakLog = BuiltInRegistries.BLOCK.getId(Blocks.OAK_LOG)

        val brokenTree = compareFeatureCell(
            observedId = air,
            expectedId = oakLog,
            clientObserved = true,
        )
        val grownTree = compareFeatureCell(
            observedId = oakLog,
            expectedId = air,
            clientObserved = true,
        )
        assertEquals(0, brokenTree.missingSolidCount)
        assertEquals(0, grownTree.unexpectedSolidCount)
        assertTrue(brokenTree.cells.isEmpty())
        assertTrue(grownTree.cells.isEmpty())
    }

    @Test
    fun `features fidelity excludes pointed dripstone even after an observed update`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val pointedDripstone = BuiltInRegistries.BLOCK.getId(Blocks.POINTED_DRIPSTONE)

        assertTrue(compareFeatureCell(observedId = pointedDripstone, expectedId = air).cells.isEmpty())
        assertTrue(compareFeatureCell(observedId = air, expectedId = pointedDripstone).cells.isEmpty())

        val brokenDripstone = compareFeatureCell(
            observedId = air,
            expectedId = pointedDripstone,
            clientObserved = true,
        )
        assertEquals(0, brokenDripstone.missingSolidCount)
        assertTrue(brokenDripstone.cells.isEmpty())
    }

    @Test
    fun `falling entity candidates are excluded from seed mismatch counts and overlay cells`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val sand = BuiltInRegistries.BLOCK.getId(Blocks.SAND)
        val gravel = BuiltInRegistries.BLOCK.getId(Blocks.GRAVEL)
        val anvil = BuiltInRegistries.BLOCK.getId(Blocks.ANVIL)

        val observedSand = compareFeatureCell(observedId = sand, expectedId = air)
        val missingGravel = compareFeatureCell(observedId = air, expectedId = gravel)
        val observedAnvil = compareFeatureCell(observedId = anvil, expectedId = air)

        assertEquals(0, observedSand.unexpectedSolidCount)
        assertEquals(0, missingGravel.missingSolidCount)
        assertEquals(0, observedAnvil.utilityMismatchCount)
        assertTrue(observedSand.cells.isEmpty())
        assertTrue(missingGravel.cells.isEmpty())
        assertTrue(observedAnvil.cells.isEmpty())
    }

    @Test
    fun `natural dripstone blocks are excluded from seed mismatch counts and overlay cells`() {
        val water = BuiltInRegistries.BLOCK.getId(Blocks.WATER)
        val dripstoneBlock = BuiltInRegistries.BLOCK.getId(Blocks.DRIPSTONE_BLOCK)

        val observedDripstone = compareFeatureCell(observedId = dripstoneBlock, expectedId = water)
        val missingDripstone = compareFeatureCell(observedId = water, expectedId = dripstoneBlock)

        assertEquals(0, observedDripstone.unexpectedSolidCount)
        assertEquals(0, missingDripstone.missingSolidCount)
        assertTrue(observedDripstone.cells.isEmpty())
        assertTrue(missingDripstone.cells.isEmpty())
    }

    @Test
    fun `natural sulfur drift is excluded while crafted sulfur remains comparable`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val sulfur = BuiltInRegistries.BLOCK.getId(Blocks.SULFUR)
        val potentSulfur = BuiltInRegistries.BLOCK.getId(Blocks.POTENT_SULFUR)
        val polishedSulfur = BuiltInRegistries.BLOCK.getId(Blocks.POLISHED_SULFUR)

        val observedNatural = compareFeatureCell(observedId = sulfur, expectedId = air)
        val missingNatural = compareFeatureCell(observedId = air, expectedId = potentSulfur)
        val observedCrafted = compareFeatureCell(observedId = polishedSulfur, expectedId = air)

        assertEquals(0, observedNatural.unexpectedSolidCount)
        assertEquals(0, missingNatural.missingSolidCount)
        assertTrue(observedNatural.cells.isEmpty())
        assertTrue(missingNatural.cells.isEmpty())
        assertEquals(1, observedCrafted.unexpectedSolidCount)
        assertEquals(SeedMismatchKind.UNEXPECTED_SOLID, observedCrafted.cells.single().kind)
    }

    @Test
    fun `generated cobweb drift is excluded from seed mismatch and overlay`() {
        val caveAir = BuiltInRegistries.BLOCK.getId(Blocks.CAVE_AIR)
        val cobweb = BuiltInRegistries.BLOCK.getId(Blocks.COBWEB)

        val missingCobweb = compareFeatureCell(observedId = caveAir, expectedId = cobweb)
        val unexpectedCobweb = compareFeatureCell(observedId = cobweb, expectedId = caveAir)

        assertEquals(0, missingCobweb.missingSolidCount)
        assertEquals(0, unexpectedCobweb.unexpectedSolidCount)
        assertTrue(missingCobweb.cells.isEmpty())
        assertTrue(unexpectedCobweb.cells.isEmpty())
    }

    @Test
    fun `dimension vegetation and bee homes never enter score or overlay`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val ignored = listOf(
            Blocks.KELP,
            Blocks.BAMBOO,
            Blocks.MANGROVE_ROOTS,
            Blocks.CRIMSON_STEM,
            Blocks.NETHER_WART_BLOCK,
            Blocks.SHROOMLIGHT,
            Blocks.TWISTING_VINES,
            Blocks.CHORUS_PLANT,
            Blocks.CHORUS_FLOWER,
            Blocks.SMALL_AMETHYST_BUD,
            Blocks.MEDIUM_AMETHYST_BUD,
            Blocks.LARGE_AMETHYST_BUD,
            Blocks.AMETHYST_CLUSTER,
            Blocks.BEE_NEST,
            Blocks.BEEHIVE,
        )

        ignored.forEach { block ->
            val blockId = BuiltInRegistries.BLOCK.getId(block)
            val unexpected = compareFeatureCell(blockId, air, clientObserved = true)
            val missing = compareFeatureCell(air, blockId, clientObserved = true)
            val path = BuiltInRegistries.BLOCK.getKey(block).toString()

            assertEquals(0, unexpected.unexpectedSolidCount, path)
            assertEquals(0, missing.missingSolidCount, path)
            assertTrue(unexpected.cells.isEmpty(), path)
            assertTrue(missing.cells.isEmpty(), path)
        }
    }

    @Test
    fun `processed vegetation products still produce seed evidence`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        listOf(
            Blocks.OAK_PLANKS,
            Blocks.STRIPPED_CRIMSON_STEM,
            Blocks.DRIED_KELP_BLOCK,
        ).forEach { block ->
            val compared = compareFeatureCell(BuiltInRegistries.BLOCK.getId(block), air)
            assertEquals(1, compared.unexpectedSolidCount, BuiltInRegistries.BLOCK.getKey(block).toString())
            assertEquals(SeedMismatchKind.UNEXPECTED_SOLID, compared.cells.single().kind)
        }
    }

    @Test
    fun `client observed ground replacement reports unexpected solid`() {
        val gold = BuiltInRegistries.BLOCK.getId(Blocks.GOLD_BLOCK)
        val stone = BuiltInRegistries.BLOCK.getId(Blocks.STONE)

        val groundReplacement = compareFeatureCell(
            observedId = gold,
            expectedId = stone,
            clientObserved = true,
        )
        assertEquals(1, groundReplacement.unexpectedSolidCount)
        assertEquals(0, groundReplacement.materialSwapCount)
        assertEquals(SeedMismatchKind.UNEXPECTED_SOLID, groundReplacement.cells.single().kind)
    }

    @Test
    fun `features natural log replacing terrain remains ignored`() {
        val oakLog = BuiltInRegistries.BLOCK.getId(Blocks.OAK_LOG)
        val stone = BuiltInRegistries.BLOCK.getId(Blocks.STONE)

        val trunkInTerrain = compareFeatureCell(
            observedId = oakLog,
            expectedId = stone,
            compareMaterials = true,
        )
        assertEquals(0, trunkInTerrain.materialSwapCount)
        assertTrue(trunkInTerrain.cells.isEmpty())
    }

    private fun compareFeatureCell(
        observedId: Int,
        expectedId: Int,
        clientObserved: Boolean = false,
        compareMaterials: Boolean = false,
    ): SeedMismatchSignal {
        val chunk = ChunkCoordinate(0, 0)
        val local = ObservedChunkBlocks.packLocal(4, 4)
        val changedPosition = BlockPos.asLong(4, 64, 4)
        return BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = chunk,
                minY = 64,
                height = 1,
                columns = mapOf(local to intArrayOf(observedId)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = chunk,
                minY = 64,
                height = 1,
                columns = mapOf(local to intArrayOf(expectedId)),
                fidelity = ExpectedTerrainFidelity.FEATURES,
            ),
            phase = SeedComparePhase.OVERLAY,
            compareMaterials = compareMaterials,
            clientObservedUpdates = if (clientObserved) setOf(changedPosition) else emptySet(),
        )
    }

    @Test
    fun `column overlay hides roofed caves while features outlines all digs`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val stone = BuiltInRegistries.BLOCK.getId(Blocks.STONE)
        val packed = ObservedChunkBlocks.packLocal(2, 2)

        // Roofed cave vs bare columns: counted, not outlined on overlay.
        val cave = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 3,
                columns = mapOf(packed to intArrayOf(air, air, stone)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 3,
                columns = mapOf(packed to intArrayOf(stone, stone, stone)),
                fidelity = ExpectedTerrainFidelity.BASE_COLUMN,
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(2, cave.missingSolidCount)
        assertTrue(cave.cells.none { it.kind == SeedMismatchKind.MISSING_SOLID })

        // Same dig with FEATURES expectations: outline every missing solid.
        val featuresDig = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 3,
                columns = mapOf(packed to intArrayOf(air, air, stone)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 3,
                columns = mapOf(packed to intArrayOf(stone, stone, stone)),
                fidelity = ExpectedTerrainFidelity.FEATURES,
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(2, featuresDig.missingSolidCount)
        assertEquals(2, featuresDig.cells.count { it.kind == SeedMismatchKind.MISSING_SOLID })

        // Surface trench vs bare columns: sky-open — outlined.
        val dig = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 3,
                columns = mapOf(packed to intArrayOf(air, air, air)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 3,
                columns = mapOf(packed to intArrayOf(stone, stone, air)),
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(2, dig.missingSolidCount)
        assertEquals(2, dig.cells.count { it.kind == SeedMismatchKind.MISSING_SOLID })
    }

    @Test
    fun `sparse sample locals stay inside the chunk and honor count`() {
        val samples = BaseFinderSeedComparator.sparseSampleLocals(16)
        assertEquals(16, samples.size)
        assertTrue(samples.all { (x, z) -> x in 0..15 && z in 0..15 })
    }

    @Test
    fun `all chunk locals cover every column once`() {
        val samples = BaseFinderSeedComparator.allChunkLocals()
        assertEquals(256, samples.size)
        assertTrue(samples.all { (x, z) -> x in 0..15 && z in 0..15 })
        assertEquals(samples.size, samples.toSet().size)
    }

    @Test
    fun `soft decoration and solid surface swaps do not outline`() {
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val dirt = BuiltInRegistries.BLOCK.getId(Blocks.DIRT)
        val grassBlock = BuiltInRegistries.BLOCK.getId(Blocks.GRASS_BLOCK)
        val shortGrass = BuiltInRegistries.BLOCK.getId(Blocks.SHORT_GRASS)
        val packed = ObservedChunkBlocks.packLocal(1, 1)

        val plant = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 1,
                columns = mapOf(packed to intArrayOf(shortGrass)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 1,
                columns = mapOf(packed to intArrayOf(air)),
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(0, plant.unexpectedSolidCount)
        assertTrue(plant.cells.isEmpty())

        val surface = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 1,
                columns = mapOf(packed to intArrayOf(grassBlock)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 64,
                height = 1,
                columns = mapOf(packed to intArrayOf(dirt)),
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(0, surface.unexpectedSolidCount)
        assertTrue(surface.cells.isEmpty())
    }

    @Test
    fun `features fidelity ignores solid identity including cobble on stone`() {
        val stone = BuiltInRegistries.BLOCK.getId(Blocks.STONE)
        val andesite = BuiltInRegistries.BLOCK.getId(Blocks.ANDESITE)
        val gravel = BuiltInRegistries.BLOCK.getId(Blocks.GRAVEL)
        val cobble = BuiltInRegistries.BLOCK.getId(Blocks.COBBLESTONE)
        val packed = ObservedChunkBlocks.packLocal(5, 5)

        val naturalSwap = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 2,
                columns = mapOf(packed to intArrayOf(andesite, gravel)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 2,
                columns = mapOf(packed to intArrayOf(stone, stone)),
                fidelity = ExpectedTerrainFidelity.FEATURES,
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(0, naturalSwap.unexpectedSolidCount)
        assertTrue(naturalSwap.cells.isEmpty())

        val cobbleOnStone = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 1,
                columns = mapOf(packed to intArrayOf(cobble)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 1,
                columns = mapOf(packed to intArrayOf(stone)),
                fidelity = ExpectedTerrainFidelity.FEATURES,
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(0, cobbleOnStone.unexpectedSolidCount)
        assertTrue(cobbleOnStone.cells.isEmpty())
    }

    @Test
    fun `features fidelity still flags solid occupancy into air`() {
        val cobble = BuiltInRegistries.BLOCK.getId(Blocks.COBBLESTONE)
        val air = BuiltInRegistries.BLOCK.getId(Blocks.AIR)
        val packed = ObservedChunkBlocks.packLocal(5, 5)

        val cobbleInAir = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 1,
                columns = mapOf(packed to intArrayOf(cobble)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = 0,
                height = 1,
                columns = mapOf(packed to intArrayOf(air)),
                fidelity = ExpectedTerrainFidelity.FEATURES,
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(1, cobbleInAir.unexpectedSolidCount)
        assertEquals(SeedMismatchKind.UNEXPECTED_SOLID, cobbleInAir.cells.single().kind)
    }

    @Test
    fun `features fidelity outlines water digs but ignores cave air cells`() {
        val stone = BuiltInRegistries.BLOCK.getId(Blocks.STONE)
        val water = BuiltInRegistries.BLOCK.getId(Blocks.WATER)
        val caveAir = BuiltInRegistries.BLOCK.getId(Blocks.CAVE_AIR)
        val packed = ObservedChunkBlocks.packLocal(3, 3)

        val waterDig = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = -20,
                height = 1,
                columns = mapOf(packed to intArrayOf(water)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = -20,
                height = 1,
                columns = mapOf(packed to intArrayOf(stone)),
                fidelity = ExpectedTerrainFidelity.FEATURES,
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(1, waterDig.missingSolidCount)
        assertEquals(SeedMismatchKind.MISSING_SOLID, waterDig.cells.single().kind)

        val caveDig = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = -20,
                height = 1,
                columns = mapOf(packed to intArrayOf(caveAir)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = -20,
                height = 1,
                columns = mapOf(packed to intArrayOf(stone)),
                fidelity = ExpectedTerrainFidelity.FEATURES,
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(0, caveDig.missingSolidCount)
        assertTrue(caveDig.cells.isEmpty())

        val caveBuild = BaseFinderSeedComparator.compare(
            observed = ObservedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = -20,
                height = 1,
                columns = mapOf(packed to intArrayOf(stone)),
            ),
            expected = ExpectedChunkBlocks(
                chunk = ChunkCoordinate(0, 0),
                minY = -20,
                height = 1,
                columns = mapOf(packed to intArrayOf(caveAir)),
                fidelity = ExpectedTerrainFidelity.FEATURES,
            ),
            phase = SeedComparePhase.OVERLAY,
        )
        assertEquals(0, caveBuild.unexpectedSolidCount)
        assertTrue(caveBuild.cells.isEmpty())
    }
}
