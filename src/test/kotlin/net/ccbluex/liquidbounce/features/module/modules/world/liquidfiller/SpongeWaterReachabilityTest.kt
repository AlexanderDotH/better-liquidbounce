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
package net.ccbluex.liquidbounce.features.module.modules.world.liquidfiller

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SpongeBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpongeWaterReachabilityTest {

    @Test
    fun `sponge on the target succeeds without reading the world`() {
        var reads = 0
        val target = BlockPos(4, 7, 11)
        val reachability = SpongeWaterReachability {
            reads++
            null
        }

        assertTrue(reachability.canAbsorbFrom(target, target))
        assertEquals(0, reads)
    }

    @Test
    fun `water and underwater plants form the same absorption route`() {
        val sponge = BlockPos.ZERO
        val kelp = sponge.east()
        val target = kelp.east()
        val states = mapOf(
            kelp to Blocks.KELP.defaultBlockState(),
            target to Blocks.WATER.defaultBlockState(),
        )

        assertTrue(reachabilityFor(states).canAbsorbFrom(sponge, target))
    }

    @Test
    fun `waterlogged bucket pickup blocks remain traversable`() {
        val sponge = BlockPos.ZERO
        val barrier = sponge.east()
        val target = barrier.east()
        val states = mapOf(
            barrier to Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
            target to Blocks.WATER.defaultBlockState(),
        )

        assertTrue(reachabilityFor(states).canAbsorbFrom(sponge, target))
    }

    @Test
    fun `water target is reached before traversal classification`() {
        val sponge = BlockPos.ZERO
        val target = sponge.east()
        val states = mapOf(
            target to Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
        )

        assertTrue(reachabilityFor(states).canAbsorbFrom(sponge, target))
    }

    @Test
    fun `route keeps the vanilla sponge depth boundary`() {
        val sponge = BlockPos.ZERO
        val states = mutableMapOf<BlockPos, BlockState>()
        for (distance in 1..SpongeBlock.MAX_DEPTH + 1) {
            states[BlockPos(distance, 0, 0)] = Blocks.WATER.defaultBlockState()
        }

        val reachability = reachabilityFor(states)

        assertTrue(reachability.canAbsorbFrom(sponge, BlockPos(SpongeBlock.MAX_DEPTH, 0, 0)))
        assertFalse(reachability.canAbsorbFrom(sponge, BlockPos(SpongeBlock.MAX_DEPTH + 1, 0, 0)))
    }

    private fun reachabilityFor(states: Map<BlockPos, BlockState>) = SpongeWaterReachability(
        stateAt = states::get,
        isWater = { state ->
            val fluid = state.fluidState.type
            fluid === Fluids.WATER || fluid === Fluids.FLOWING_WATER
        },
    )

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
