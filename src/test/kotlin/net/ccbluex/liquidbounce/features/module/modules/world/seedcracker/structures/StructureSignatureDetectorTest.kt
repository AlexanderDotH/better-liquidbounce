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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackScope
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceConfidence
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceStatus
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.StructureType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StructureSignatureDetectorTest {

    private val detector = StructureSignatureDetector

    @Test
    fun `all supported complete signatures are strong evidence`() {
        val expected = mapOf(
            StructureType.IGLOO to iglooBlocks(),
            StructureType.DESERT_PYRAMID to desertPyramidBlocks(),
            StructureType.JUNGLE_TEMPLE to jungleTempleBlocks(),
            StructureType.SWAMP_HUT to swampHutBlocks(),
            StructureType.SHIPWRECK to shipwreckBlocks(),
            StructureType.PILLAGER_OUTPOST to pillagerOutpostBlocks(),
            StructureType.OCEAN_MONUMENT to oceanMonumentBlocks(),
        )

        expected.forEach { (type, blocks) ->
            val matches = detector.detect(snapshot(blocks))
            val match = matches.single { it.type == type }

            assertEquals(EvidenceConfidence.STRONG, match.confidence, type.name)
            assertFalse(match.requiresPlayerConfirmation, type.name)
            assertTrue(match.matchedFeatureKeys.size >= 3, type.name)
        }
    }

    @Test
    fun `single distinctive block does not create evidence`() {
        val matches = detector.detect(snapshot(listOf(block(52, 64, 52, "minecraft:blue_terracotta"))))

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `non vanilla blocks with vanilla looking paths do not impersonate structure evidence`() {
        val copiedByAnotherMod = desertPyramidBlocks().map { block ->
            block(block.x, block.y, block.z, "othermod:${block.blockId}")
        }

        assertTrue(detector.detect(snapshot(copiedByAnotherMod)).isEmpty())
    }

    @Test
    fun `partial compact jungle temple signature requires player confirmation`() {
        val matches = detector.detect(
            snapshot(
                listOf(
                    block(52, 64, 52, "tripwire_hook"),
                    block(55, 64, 52, "tripwire_hook"),
                    block(53, 64, 52, "redstone_wire"),
                    block(54, 64, 52, "dispenser"),
                ),
            ),
        )

        val match = matches.single()
        assertEquals(StructureType.JUNGLE_TEMPLE, match.type)
        assertEquals(EvidenceConfidence.AMBIGUOUS, match.confidence)
        assertTrue(match.requiresPlayerConfirmation)
    }

    @Test
    fun `unrelated two feature groups do not create an ambiguous igloo`() {
        val matches = detector.detect(
            snapshot(
                listOf(
                    block(52, 64, 52, "snow_block"),
                    block(53, 64, 52, "chest"),
                ),
            ),
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `ambiguous signature conversion stays pending while strong signatures are accepted`() {
        val scope = CrackScope("detector-test", "minecraft:overworld")
        val ambiguous = detector.detect(
            snapshot(
                listOf(
                    block(52, 64, 52, "tripwire_hook"),
                    block(55, 64, 52, "tripwire_hook"),
                    block(53, 64, 52, "redstone_wire"),
                    block(54, 64, 52, "dispenser"),
                ),
            ),
        ).single().toObservation(scope)
        val strong = detector.detect(snapshot(desertPyramidBlocks())).single().toObservation(scope)

        assertEquals(EvidenceStatus.PENDING_CONFIRMATION, ambiguous.status)
        assertFalse(ambiguous.isAccepted)
        assertEquals(EvidenceStatus.ACCEPTED, strong.status)
        assertTrue(strong.isAccepted)
    }

    @Test
    fun `same visible snapshot has order independent hash and deduplication key`() {
        val original = snapshot(desertPyramidBlocks())
        val reordered = snapshot(desertPyramidBlocks().reversed())

        val first = detector.detect(original).single { it.type == StructureType.DESERT_PYRAMID }
        val second = detector.detect(reordered).single { it.type == StructureType.DESERT_PYRAMID }

        assertEquals(first.snapshotHash, second.snapshotHash)
        assertEquals(first.deduplicationKey, second.deduplicationKey)
        assertEquals(3, first.anchorChunkX)
        assertEquals(3, first.anchorChunkZ)
    }

    @Test
    fun `snapshot takes an immutable copy of scanner data`() {
        val mutableBlocks = desertPyramidBlocks().toMutableList()
        val immutableSnapshot = snapshot(mutableBlocks)
        mutableBlocks.clear()

        assertTrue(
            detector.detect(immutableSnapshot).any { it.type == StructureType.DESERT_PYRAMID },
        )
    }

    @Test
    fun `structure signatures outside the overworld are ignored`() {
        val matches = detector.detect(
            snapshot(
                desertPyramidBlocks(),
                dimensionKey = "minecraft:the_nether",
            ),
        )

        assertTrue(matches.isEmpty())
    }

    private fun snapshot(
        blocks: Collection<StructureBlockSnapshot>,
        dimensionKey: String = "minecraft:overworld",
    ) = StructureChunkSnapshot(
        chunkX = 3,
        chunkZ = 3,
        rawDimensionKey = dimensionKey,
        revision = 7L,
        blocks = blocks,
    )

    private fun iglooBlocks() = listOf(
        block(52, 48, 52, "snow_block"),
        block(52, 43, 52, "redstone_torch"),
        block(53, 43, 52, "chest"),
        block(52, 44, 52, "ladder"),
        block(52, 45, 52, "ladder"),
    )

    private fun desertPyramidBlocks() = listOf(
        block(52, 64, 52, "blue_terracotta"),
        block(51, 64, 52, "blue_terracotta"),
        block(53, 64, 52, "blue_terracotta"),
        block(52, 64, 51, "blue_terracotta"),
        block(52, 64, 53, "blue_terracotta"),
        block(52, 65, 52, "stone_pressure_plate"),
        block(50, 64, 50, "chest"),
        block(54, 64, 54, "chest"),
    )

    private fun jungleTempleBlocks() = listOf(
        block(52, 64, 52, "tripwire_hook"),
        block(55, 64, 52, "tripwire_hook"),
        block(53, 64, 52, "redstone_wire"),
        block(54, 64, 52, "dispenser"),
        block(52, 64, 54, "mossy_cobblestone"),
        block(53, 64, 54, "cobblestone"),
    )

    private fun swampHutBlocks() = listOf(
        block(52, 64, 52, "cauldron"),
        block(53, 64, 52, "crafting_table"),
        block(52, 64, 53, "oak_fence"),
        block(53, 64, 53, "oak_fence"),
        block(54, 64, 53, "oak_fence"),
        block(52, 65, 52, "spruce_planks"),
        block(53, 65, 52, "spruce_planks"),
        block(54, 65, 52, "spruce_planks"),
        block(55, 65, 52, "spruce_planks"),
    )

    private fun shipwreckBlocks() = listOf(
        block(52, 64, 52, "chest"),
        block(53, 64, 52, "chest"),
        block(52, 65, 52, "oak_planks"),
        block(53, 65, 52, "oak_planks"),
        block(54, 65, 52, "oak_planks"),
        block(55, 65, 52, "oak_planks"),
        block(52, 65, 53, "oak_stairs"),
        block(53, 65, 53, "oak_trapdoor"),
        block(54, 65, 53, "stripped_oak_log"),
    )

    private fun pillagerOutpostBlocks() = listOf(
        block(52, 64, 52, "dark_oak_log"),
        block(53, 64, 52, "dark_oak_log"),
        block(54, 64, 52, "dark_oak_log"),
        block(55, 64, 52, "dark_oak_log"),
        block(52, 65, 52, "dark_oak_planks"),
        block(53, 65, 52, "dark_oak_planks"),
        block(54, 65, 52, "dark_oak_planks"),
        block(55, 65, 52, "dark_oak_planks"),
        block(52, 66, 52, "cobblestone"),
        block(53, 66, 52, "cobblestone"),
        block(54, 66, 52, "cobblestone"),
        block(55, 66, 52, "cobblestone"),
        block(52, 67, 52, "oak_fence"),
        block(53, 67, 52, "oak_fence"),
        block(54, 67, 52, "oak_fence"),
        block(55, 67, 52, "oak_fence"),
    )

    private fun oceanMonumentBlocks() = listOf(
        block(52, 64, 52, "prismarine"),
        block(53, 64, 52, "prismarine"),
        block(54, 64, 52, "prismarine"),
        block(55, 64, 52, "prismarine"),
        block(52, 65, 52, "prismarine_bricks"),
        block(53, 65, 52, "prismarine_bricks"),
        block(54, 65, 52, "prismarine_bricks"),
        block(55, 65, 52, "prismarine_bricks"),
        block(52, 66, 52, "dark_prismarine"),
        block(53, 66, 52, "dark_prismarine"),
        block(54, 66, 52, "dark_prismarine"),
        block(55, 66, 52, "dark_prismarine"),
        block(52, 67, 52, "sea_lantern"),
        block(53, 67, 52, "sea_lantern"),
    )

    private fun block(x: Int, y: Int, z: Int, id: String) = StructureBlockSnapshot(x, y, z, id)
}
