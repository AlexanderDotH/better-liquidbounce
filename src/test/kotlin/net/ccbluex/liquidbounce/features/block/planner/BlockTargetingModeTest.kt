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
package net.ccbluex.liquidbounce.features.block.planner

import net.ccbluex.liquidbounce.features.block.config.BlockTargetingMode
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BlockTargetingModeTest {

    @Test
    fun `air and fluid place at a neighboring block`() {
        assertEquals(BlockTargetingMode.PLACE_AT_NEIGHBOR, targetingModeFor(Blocks.AIR.defaultBlockState()))
        assertEquals(BlockTargetingMode.PLACE_AT_NEIGHBOR, targetingModeFor(Blocks.WATER.defaultBlockState()))
    }

    @Test
    fun `occupied dry block is replaced directly`() {
        assertEquals(BlockTargetingMode.REPLACE_EXISTING_BLOCK, targetingModeFor(Blocks.STONE.defaultBlockState()))
    }

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
