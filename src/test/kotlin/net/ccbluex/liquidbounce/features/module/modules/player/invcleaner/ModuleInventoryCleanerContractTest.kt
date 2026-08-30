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
package net.ccbluex.liquidbounce.features.module.modules.player.invcleaner

import it.unimi.dsi.fastutil.objects.ObjectIntPair
import net.ccbluex.liquidbounce.features.inventory.OffhandReservationManager
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.ItemFacet
import net.ccbluex.liquidbounce.utils.inventory.ArmorItemSlot
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.VirtualItemSlot
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModuleInventoryCleanerContractTest {

    private val holders = IdentityHashMap<Item, Holder<Item>>()

    @BeforeEach
    @AfterEach
    fun clearOffhandReservation() = OffhandReservationManager.clear()

    @Test
    fun `settings retain their persisted order`() {
        assertEquals(
            listOf(
                "Enabled", "Bind", "Hidden", "Constraints", "MaximumBlocks", "MaximumArrows",
                "MaximumThrowables", "MaximumFoodPoints", "MaximumWaterBuckets", "MaximumLavaBuckets",
                "MaximumMilkBuckets", "ItemsBlacklist", "Greedy", "OffHandItem", "SlotItem-1", "SlotItem-2",
                "SlotItem-3", "SlotItem-4", "SlotItem-5", "SlotItem-6", "SlotItem-7", "SlotItem-8", "SlotItem-9",
            ),
            ModuleInventoryCleaner.inner.map { it.name },
        )
    }

    @Test
    fun `default placement template retains slot order and armor protection`() {
        val template = ModuleInventoryCleaner.cleanupTemplateFromSettings
        val expectedTargets = buildList {
            if (HotbarItemSlot.OFFHAND.canBeSwapTarget) {
                add(HotbarItemSlot.OFFHAND to ItemSortChoice.SHIELD)
            }
            addAll(
                HotbarItemSlot.mainHandSlots.zip(
                    listOf(
                        ItemSortChoice.WEAPON,
                        ItemSortChoice.BOW,
                        ItemSortChoice.PICKAXE,
                        ItemSortChoice.AXE,
                        ItemSortChoice.NONE,
                        ItemSortChoice.POTION,
                        ItemSortChoice.FOOD,
                        ItemSortChoice.BLOCK,
                        ItemSortChoice.BLOCK,
                    )
                )
            )
        }

        assertEquals(expectedTargets, template.slotContentMap.entries.map { it.key to it.value })
        assertTrue(template.forbiddenSlots.containsAll(ArmorItemSlot.entries))
    }

    @Test
    fun `category and function constraints retain configured defaults`() {
        val provider = ModuleInventoryCleaner.cleanupTemplateFromSettings.itemAmountConstraintProvider

        val blockConstraint = provider(facet(ItemType.BLOCK.defaultCategory)).single()
        val blockGroup = assertIs<ItemCategoryConstraintGroup>(blockConstraint.group)
        assertEquals(512..Int.MAX_VALUE, blockGroup.acceptableRange)
        assertEquals(32, blockConstraint.amountAddedByItem)

        val weaponConstraint = provider(facet(ItemType.SWORD.defaultCategory)).single()
        assertEquals(1..Int.MAX_VALUE, weaponConstraint.group.acceptableRange)

        val foodConstraint = provider(
            facet(
                ItemType.FOOD.defaultCategory,
                listOf(ObjectIntPair.of(ItemFunction.FOOD, 12)),
            )
        ).single()
        val foodGroup = assertIs<ItemFunctionCategoryConstraintGroup>(foodConstraint.group)
        assertEquals(200..Int.MAX_VALUE, foodGroup.acceptableRange)
        assertEquals(12, foodConstraint.amountAddedByItem)
    }

    private fun facet(
        category: ItemCategory,
        functions: List<ObjectIntPair<ItemFunction>> = emptyList(),
    ): ItemFacet {
        val slot = VirtualItemSlot(stack(Items.STONE, 32), ItemSlot.Type.INVENTORY, 0)
        return object : ItemFacet(slot) {
            override val category = category
            override val providedItemFunctions = functions
        }
    }

    private fun stack(item: Item, count: Int): ItemStack = ItemStack(
        holders.getOrPut(item) {
            Holder.direct(item, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build())
        },
        count,
    )
}
