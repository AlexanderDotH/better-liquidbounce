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

import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderBlockRegistry
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.AttachedStemBlock
import net.minecraft.world.level.block.BambooStalkBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BushBlock
import net.minecraft.world.level.block.CactusBlock
import net.minecraft.world.level.block.ChorusFlowerBlock
import net.minecraft.world.level.block.ChorusPlantBlock
import net.minecraft.world.level.block.CocoaBlock
import net.minecraft.world.level.block.GrowingPlantBlock
import net.minecraft.world.level.block.HugeMushroomBlock
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.MangroveRootsBlock
import net.minecraft.world.level.block.SugarCaneBlock
import net.minecraft.world.level.chunk.status.ChunkPyramid
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseFinderBlockRegistryTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private fun id(block: Block): Int = BuiltInRegistries.BLOCK.getId(block)
    }

    @Test
    fun `light generation waits for the neighbouring initialize light ring`() {
        val light = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.LIGHT)
        assertEquals(ChunkStatus.INITIALIZE_LIGHT, light.directDependencies().get(0))
        assertEquals(ChunkStatus.INITIALIZE_LIGHT, light.directDependencies().get(1))
    }

    @Test
    @EnabledIfSystemProperty(named = "liquidbounce.basefinder.featuresIT", matches = "true")
    fun `light generation keeps a stabilized chunk unchanged after neighbour generation`() {
        val server = BaseFinderBackgroundServer.spin(4_272L, BaseFinderBackgroundServer.GENERATION_VIEW_DISTANCE)
        try {
            assertTrue(server.awaitReady())
            val initial = requireNotNull(server.generateExpectedChunk("minecraft:overworld", 0, 0))
            val before = fingerprint(initial)
            for (chunkX in -1..1) {
                for (chunkZ in -1..1) {
                    if (chunkX != 0 || chunkZ != 0) {
                        server.generateExpectedChunk("minecraft:overworld", chunkX, chunkZ)
                    }
                }
            }
            val after = fingerprint(requireNotNull(server.generateExpectedChunk("minecraft:overworld", 0, 0)))
            assertEquals(before, after)
        } finally {
            server.shutdownAndCleanup()
        }
    }

    @Test
    fun `flags classify terrain, decoration, empty space and utilities`() {
        assertTrue(BaseFinderBlockRegistry.isEmptySpace(id(Blocks.AIR)))
        assertTrue(BaseFinderBlockRegistry.isEmptySpace(id(Blocks.CAVE_AIR)))
        assertFalse(BaseFinderBlockRegistry.isEmptySpace(id(Blocks.STONE)))

        assertTrue(BaseFinderBlockRegistry.isSolidTerrain(id(Blocks.STONE)))
        assertTrue(BaseFinderBlockRegistry.isSolidTerrain(id(Blocks.GRASS_BLOCK)))
        assertFalse(BaseFinderBlockRegistry.isSolidTerrain(id(Blocks.WATER)))
        assertFalse(BaseFinderBlockRegistry.isSolidTerrain(id(Blocks.AIR)))

        assertTrue(BaseFinderBlockRegistry.isSoftDecoration(id(Blocks.OAK_LEAVES)))
        assertTrue(BaseFinderBlockRegistry.isSoftDecoration(id(Blocks.SHORT_GRASS)))
        assertFalse(BaseFinderBlockRegistry.isSoftDecoration(id(Blocks.GRASS_BLOCK)))
        assertFalse(BaseFinderBlockRegistry.isSoftDecoration(id(Blocks.WATER)))

        assertTrue(BaseFinderBlockRegistry.isUtility(id(Blocks.CHEST)))
        assertFalse(BaseFinderBlockRegistry.isUtility(id(Blocks.STONE)))
    }

    @Test
    fun `blocks which can become falling entities are identified without flagging stable terrain`() {
        assertTrue(BaseFinderBlockRegistry.canBecomeFallingBlockEntity(id(Blocks.SAND)))
        assertTrue(BaseFinderBlockRegistry.canBecomeFallingBlockEntity(id(Blocks.GRAVEL)))
        assertTrue(BaseFinderBlockRegistry.canBecomeFallingBlockEntity(id(Blocks.CONCRETE_POWDER.red())))
        assertTrue(BaseFinderBlockRegistry.canBecomeFallingBlockEntity(id(Blocks.ANVIL)))
        assertTrue(BaseFinderBlockRegistry.canBecomeFallingBlockEntity(id(Blocks.SUSPICIOUS_SAND)))
        assertTrue(BaseFinderBlockRegistry.canBecomeFallingBlockEntity(id(Blocks.SCAFFOLDING)))
        assertTrue(BaseFinderBlockRegistry.canBecomeFallingBlockEntity(id(Blocks.POINTED_DRIPSTONE)))
        assertFalse(BaseFinderBlockRegistry.canBecomeFallingBlockEntity(id(Blocks.DRIPSTONE_BLOCK)))
        assertFalse(BaseFinderBlockRegistry.canBecomeFallingBlockEntity(id(Blocks.STONE)))
    }

    @Test
    fun `natural dripstone and falling entity candidates are unstable seed comparison inputs`() {
        assertTrue(BaseFinderBlockRegistry.isUnstableSeedComparison(id(Blocks.SAND)))
        assertTrue(BaseFinderBlockRegistry.isUnstableSeedComparison(id(Blocks.POINTED_DRIPSTONE)))
        assertTrue(BaseFinderBlockRegistry.isUnstableSeedComparison(id(Blocks.DRIPSTONE_BLOCK)))
        assertFalse(BaseFinderBlockRegistry.isUnstableSeedComparison(id(Blocks.STONE)))
    }

    @Test
    fun `natural sulfur features are unstable while crafted sulfur blocks remain comparable`() {
        assertTrue(BaseFinderBlockRegistry.isUnstableSeedComparison(id(Blocks.SULFUR)))
        assertTrue(BaseFinderBlockRegistry.isUnstableSeedComparison(id(Blocks.POTENT_SULFUR)))
        assertTrue(BaseFinderBlockRegistry.isUnstableSeedComparison(id(Blocks.SULFUR_SPIKE)))
        assertFalse(BaseFinderBlockRegistry.isUnstableSeedComparison(id(Blocks.POLISHED_SULFUR)))
        assertFalse(BaseFinderBlockRegistry.isUnstableSeedComparison(id(Blocks.SULFUR_BRICKS)))
    }

    @Test
    fun `generated cobweb is an unstable seed comparison input`() {
        assertTrue(BaseFinderBlockRegistry.isUnstableSeedComparison(id(Blocks.COBWEB)))
    }

    @Test
    fun `natural growth from every dimension and bee homes are unstable seed inputs`() {
        listOf(
            Blocks.OAK_LOG,
            Blocks.OAK_LEAVES,
            Blocks.OAK_SAPLING,
            Blocks.KELP,
            Blocks.KELP_PLANT,
            Blocks.WHEAT,
            Blocks.BAMBOO,
            Blocks.CACTUS,
            Blocks.MANGROVE_ROOTS,
            Blocks.CRIMSON_STEM,
            Blocks.CRIMSON_HYPHAE,
            Blocks.NETHER_WART_BLOCK,
            Blocks.WARPED_WART_BLOCK,
            Blocks.SHROOMLIGHT,
            Blocks.WEEPING_VINES_PLANT,
            Blocks.WARPED_FUNGUS,
            Blocks.NETHER_SPROUTS,
            Blocks.CHORUS_PLANT,
            Blocks.CHORUS_FLOWER,
            Blocks.SMALL_AMETHYST_BUD,
            Blocks.MEDIUM_AMETHYST_BUD,
            Blocks.LARGE_AMETHYST_BUD,
            Blocks.AMETHYST_CLUSTER,
            Blocks.BEE_NEST,
            Blocks.BEEHIVE,
        ).forEach { block ->
            assertTrue(
                BaseFinderBlockRegistry.isUnstableSeedComparison(id(block)),
                BuiltInRegistries.BLOCK.getKey(block).toString(),
            )
        }
    }

    @Test
    fun `processed plant and wood products remain comparable`() {
        listOf(
            Blocks.OAK_PLANKS,
            Blocks.STRIPPED_OAK_LOG,
            Blocks.CRIMSON_PLANKS,
            Blocks.STRIPPED_CRIMSON_STEM,
            Blocks.DRIED_KELP_BLOCK,
        ).forEach { block ->
            assertFalse(
                BaseFinderBlockRegistry.isUnstableSeedComparison(id(block)),
                BuiltInRegistries.BLOCK.getKey(block).toString(),
            )
        }
    }

    @Test
    fun `every vanilla growth block class is unstable seed input`() {
        val growthTypes = listOf(
            GrowingPlantBlock::class.java,
            BushBlock::class.java,
            LeavesBlock::class.java,
            BambooStalkBlock::class.java,
            CactusBlock::class.java,
            ChorusPlantBlock::class.java,
            ChorusFlowerBlock::class.java,
            CocoaBlock::class.java,
            SugarCaneBlock::class.java,
            HugeMushroomBlock::class.java,
            MangroveRootsBlock::class.java,
            AttachedStemBlock::class.java,
        )

        BuiltInRegistries.BLOCK.forEach { block ->
            if (growthTypes.none { type -> type.isInstance(block) }) return@forEach
            assertTrue(
                BaseFinderBlockRegistry.isUnstableSeedComparison(id(block)),
                BuiltInRegistries.BLOCK.getKey(block).toString(),
            )
        }
    }

    @Test
    fun `natural logs are separated from processed wood`() {
        assertTrue(BaseFinderBlockRegistry.isNaturalLog(id(Blocks.OAK_LOG)))
        assertTrue(BaseFinderBlockRegistry.isBuildMaterial(id(Blocks.OAK_LOG)))
        assertFalse(BaseFinderBlockRegistry.isNaturalLog(id(Blocks.OAK_PLANKS)))
        assertTrue(BaseFinderBlockRegistry.isBuildMaterial(id(Blocks.OAK_PLANKS)))
        assertFalse(BaseFinderBlockRegistry.isNaturalLog(id(Blocks.STRIPPED_OAK_LOG)))
    }

    @Test
    fun `ticked-world drift families share one identity class`() {
        assertTrue(BaseFinderBlockRegistry.sameMaterial(id(Blocks.GRASS_BLOCK), id(Blocks.DIRT_PATH)))
        assertTrue(BaseFinderBlockRegistry.sameMaterial(id(Blocks.DIRT), id(Blocks.FARMLAND)))
        assertTrue(BaseFinderBlockRegistry.sameMaterial(id(Blocks.WATER), id(Blocks.ICE)))
        assertTrue(BaseFinderBlockRegistry.sameMaterial(id(Blocks.SNOW), id(Blocks.SNOW_BLOCK)))
        assertEquals(
            BaseFinderBlockRegistry.identityClass(id(Blocks.GRASS_BLOCK)),
            BaseFinderBlockRegistry.identityClass(id(Blocks.PODZOL)),
        )
    }

    @Test
    fun `distinct materials stay distinct`() {
        assertFalse(BaseFinderBlockRegistry.sameMaterial(id(Blocks.STONE), id(Blocks.COBBLESTONE)))
        assertFalse(BaseFinderBlockRegistry.sameMaterial(id(Blocks.STONE), id(Blocks.DEEPSLATE)))
        assertFalse(BaseFinderBlockRegistry.sameMaterial(id(Blocks.OAK_PLANKS), id(Blocks.OAK_LOG)))
        assertTrue(BaseFinderBlockRegistry.sameMaterial(id(Blocks.STONE), id(Blocks.STONE)))
    }

    @Test
    fun `unmapped ids fall back to self identity and empty space`() {
        val unknown = Int.MAX_VALUE
        assertEquals(unknown, BaseFinderBlockRegistry.identityClass(unknown))
        assertTrue(BaseFinderBlockRegistry.sameMaterial(unknown, unknown))
        assertTrue(BaseFinderBlockRegistry.isEmptySpace(unknown))
        assertFalse(BaseFinderBlockRegistry.isSolidTerrain(unknown))
    }

    private fun fingerprint(chunk: net.minecraft.world.level.chunk.ChunkAccess): Int {
        val pos = BlockPos.MutableBlockPos()
        var hash = 1
        for (localX in 0..15) {
            for (localZ in 0..15) {
                for (y in chunk.minY until chunk.minY + chunk.height) {
                    hash = 31 * hash + BuiltInRegistries.BLOCK.getId(
                        chunk.getBlockState(
                            pos.set((chunk.pos.x shl 4) + localX, y, (chunk.pos.z shl 4) + localZ),
                        ).block,
                    )
                }
            }
        }
        return hash
    }
}
