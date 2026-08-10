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
package net.ccbluex.liquidbounce.features.module.modules.world.nuker

import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NukerBlockRuleTest {

    private fun bootstrap() = MinecraftBootstrap.ensureInitialized()

    @Test
    fun `all accepts blocks without a manual selection`() {
        bootstrap()
        assertTrue(NukerBlockRule.ALL.accepts(Blocks.STONE, selectedBlock = null))
        assertTrue(NukerBlockRule.ALL.accepts(Blocks.DIRT, selectedBlock = Blocks.STONE))
    }

    @Test
    fun `same block waits for a manual selection`() {
        bootstrap()
        assertFalse(NukerBlockRule.SAME_BLOCK.accepts(Blocks.STONE, selectedBlock = null))
    }

    @Test
    fun `same block accepts only the selected block type`() {
        bootstrap()
        assertTrue(NukerBlockRule.SAME_BLOCK.accepts(Blocks.STONE, selectedBlock = Blocks.STONE))
        assertFalse(NukerBlockRule.SAME_BLOCK.accepts(Blocks.DIRT, selectedBlock = Blocks.STONE))
    }

    @Test
    fun `fast break keeps nuker on the vanilla progress path`() {
        assertFalse(shouldNukerBreakImmediately(forceImmediateBreak = true, fastBreakRunning = true))
        assertTrue(shouldNukerBreakImmediately(forceImmediateBreak = true, fastBreakRunning = false))
        assertFalse(shouldNukerBreakImmediately(forceImmediateBreak = false, fastBreakRunning = false))
    }

    @Test
    fun `only a player mining the crosshair block updates same block`() {
        val minedPos = BlockPos(1, 2, 3)

        assertTrue(isManualNukerSelection(true, minedPos, minedPos))
        assertFalse(isManualNukerSelection(false, minedPos, minedPos))
        assertFalse(isManualNukerSelection(true, BlockPos.ZERO, minedPos))
        assertFalse(isManualNukerSelection(true, null, minedPos))
    }

    @Test
    fun `player input override stays active for its full grace period and can be refreshed`() {
        val override = NukerPlayerInputOverride(durationTicks = 3)

        assertFalse(override.active)
        override.activate()
        assertTrue(override.active)

        override.tick()
        override.tick()
        override.activate()
        override.tick()
        override.tick()
        assertTrue(override.active)

        override.tick()
        assertFalse(override.active)
    }

    @Test
    fun `nuker exposes block rule instead of filter and block list`() {
        bootstrap()

        val settings = ModuleNuker.inner.associateBy { it.name }
        val rule = settings.getValue("BlockRule") as ChoiceListValue<*>

        assertEquals(NukerBlockRule.SAME_BLOCK, rule.get())
        assertEquals(setOf("All", "SameBlock"), rule.choices.map { it.tag }.toSet())
        assertFalse("Filter" in settings)
        assertFalse("Blocks" in settings)
    }
}
