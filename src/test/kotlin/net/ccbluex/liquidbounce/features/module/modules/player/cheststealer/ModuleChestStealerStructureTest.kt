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
package net.ccbluex.liquidbounce.features.module.modules.player.cheststealer

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleChestStealerStructureTest {

    @Test
    fun `settings and feature trees retain their persisted order`() {
        assertOrdered(
            source,
            "tree(InventoryConstraints())",
            "boolean(\"AutoClose\", true)",
            "choices(\"SelectionMode\"",
            "enumChoice(\"MoveMode\"",
            "boolean(\"QuickSwaps\", true)",
            "enumChoice(\"OnFull\"",
            "tree(CheckScreenHandlerTypeValueGroup(this))",
            "tree(CheckScreenTitleValueGroup(this))",
            "tree(FeatureChestAura)",
            "tree(FeatureSilentScreen)",
        )
    }

    @Test
    fun `schedule lifecycle retains planning selection transfer and close order`() {
        assertOrdered(
            source,
            "getChestScreen() ?: return@handler",
            "createCleanupPlan(screen)",
            "HotbarSwapSelector.select(",
            "filterIsInstanceTo(ArrayList<ContainerItemSlot>())",
            "LootCapacityPlanner.requiredSpace(",
            "selectionMode.activeMode.process(itemsToCollect)",
            "ObjectOpenHashSet<ItemSlot>()",
            "ContainerTransferPlanner.plan(",
            "ItemCategorization.Default.getItemFacets(slot)",
            "LootCapacityPlanner.discardActions(",
            "if (autoClose && itemsToCollect.isEmpty())",
            "InventoryAction.CloseScreen(screen)",
        )
    }

    @Test
    fun `module facade keeps its public screen contract without structural suppressions`() {
        assertTrue("fun Screen.canBeStolen(): Boolean" in source)
        assertFalse("CognitiveComplexMethod" in source)
        assertFalse("LongMethod" in source)
        assertEquals(1, Regex("handler<ScheduleInventoryActionEvent>").findAll(source).count())
    }

    @Test
    fun `selection modes retain their randomization points`() {
        assertTrue("ThreadLocalRandom.current().nextInt(slots.size)" in source)
        assertTrue("randomFactor.random()" in source)
        assertTrue("slots.shuffle()" in source)
        assertOrdered(source, "MAX_SLOT(\"MaxSlot\")", "MIN_SLOT(\"MinSlot\")")
    }

    private fun assertOrdered(source: String, vararg fragments: String) {
        val positions = fragments.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing ChestStealer contract fragment")
        assertEquals(positions.sorted(), positions, "ChestStealer lifecycle order changed")
    }

    private companion object {
        val source: String = Files.readString(
            Path.of(
                "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/cheststealer/" +
                    "ModuleChestStealer.kt"
            )
        )
    }
}
