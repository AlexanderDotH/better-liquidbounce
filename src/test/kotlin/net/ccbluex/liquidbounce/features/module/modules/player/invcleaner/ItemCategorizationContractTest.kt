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

import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.FoodItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.PrimitiveItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.ThrowableItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.WeaponItemFacet
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.VirtualItemSlot
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.BeforeAll
import java.util.IdentityHashMap
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemCategorizationContractTest {

    private val holders = IdentityHashMap<Item, Holder<Item>>()

    @Test
    fun `default categorization initializes without registry bound item components`() {
        val emptySlot = VirtualItemSlot(ItemStack.EMPTY, ItemSlot.Type.INVENTORY, 0)

        assertEquals(emptyList(), ItemCategorization.Default.getItemFacets(emptySlot))
    }

    @Test
    fun `sort choices retain their persisted order and labels`() {
        assertEquals(
            listOf(
                "Sword", "Weapon", "Spear", "Mace", "Bow", "Crossbow", "Axe", "Pickaxe", "Shovel", "Hoe",
                "Rod", "Shield", "Water", "Lava", "Milk", "Pearl", "Gapple", "Food", "Potion", "Block",
                "Throwables", "Ignore", "None",
            ),
            ItemSortChoice.entries.map(ItemSortChoice::tag),
        )
    }

    @Test
    fun `golden apples keep weapon food and specialized facets in that order`() {
        assertFacetOrder(
            Items.GOLDEN_APPLE,
            WeaponItemFacet::class,
            FoodItemFacet::class,
            PrimitiveItemFacet::class,
        )
        assertFacetOrder(
            Items.ENCHANTED_GOLDEN_APPLE,
            WeaponItemFacet::class,
            FoodItemFacet::class,
            PrimitiveItemFacet::class,
        )
    }

    @Test
    fun `throwables keep the generic weapon facet before their specialized facet`() {
        assertFacetOrder(Items.EGG, WeaponItemFacet::class, ThrowableItemFacet::class)
    }

    private fun assertFacetOrder(item: Item, vararg expected: KClass<*>) {
        val slot = VirtualItemSlot(stack(item), ItemSlot.Type.INVENTORY, 0)
        val actual = ItemCategorization(listOf(slot)).getItemFacets(slot).map { it::class }

        assertEquals(expected.toList(), actual)
    }

    private fun stack(item: Item): ItemStack = ItemStack(
        holders.getOrPut(item) {
            Holder.direct(item, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build())
        },
        1,
    )

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() = MinecraftBootstrap.ensureInitialized()
    }
}
