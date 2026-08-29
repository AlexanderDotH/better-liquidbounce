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
package net.ccbluex.liquidbounce.features.litematica.integration.litematica262

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockKind
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Litematica262BlockSnapshotMapperTest {

    @Test
    fun `waterlogged solid remains a solid block`() {
        val state = Blocks.OAK_STAIRS.defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, true)

        val snapshot = Litematica262BlockSnapshotMapper.snapshot(state)

        assertEquals(LitematicaBlockKind.SOLID, snapshot.kind)
        assertEquals("true", snapshot.properties["waterlogged"])
    }

    @Test
    fun `source and flowing liquid blocks remain distinct`() {
        val source = Litematica262BlockSnapshotMapper.snapshot(Blocks.WATER.defaultBlockState())
        val flowingState = Fluids.FLOWING_WATER.defaultFluidState().createLegacyBlock()
        val flowing = Litematica262BlockSnapshotMapper.snapshot(flowingState)

        assertEquals(LitematicaBlockKind.FLUID_SOURCE, source.kind)
        assertEquals(LitematicaBlockKind.FLUID_FLOWING, flowing.kind)
        assertFalse(flowing.reproducible)
    }

    @Test
    fun `only non replaceable blocks are support candidates`() {
        assertTrue(Litematica262BlockSnapshotMapper.isSupportCandidate(Blocks.STONE.defaultBlockState()))
        assertFalse(Litematica262BlockSnapshotMapper.isSupportCandidate(Blocks.WATER.defaultBlockState()))
        assertFalse(Litematica262BlockSnapshotMapper.isSupportCandidate(Blocks.SHORT_GRASS.defaultBlockState()))
    }

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
