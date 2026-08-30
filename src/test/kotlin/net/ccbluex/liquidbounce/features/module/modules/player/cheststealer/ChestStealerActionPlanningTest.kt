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

import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.InventoryCleanupPlan
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.InventorySwap
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.ItemAndComponents
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.VirtualItemSlot
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.BeforeAll
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChestStealerActionPlanningTest {

    private val holders = IdentityHashMap<Item, Holder<Item>>()
    private val stone = item("stone")
    private val dirt = item("dirt")

    @Test
    fun `quick move retains its single click and blacklist update`() {
        val source = slot(stack(stone, 8), ItemSlot.Type.CONTAINER, 10)
        val blacklist = linkedSetOf<ItemSlot>()

        val actions = ContainerTransferPlanner.plan(
            storageSlots = listOf(slot(ItemStack.EMPTY, ItemSlot.Type.INVENTORY, 1)),
            screen = null,
            from = source,
            targetBlacklist = blacklist,
            useQuickMove = true,
        )

        assertEquals(listOf(ContainerInput.QUICK_MOVE), actions?.map { it.actionType })
        assertSame(source, actions?.single()?.slot)
        assertEquals(listOf(source), blacklist.toList())
    }

    @Test
    fun `impossible transfer neither clicks nor blacklists the source`() {
        val source = slot(stack(stone, 8), ItemSlot.Type.CONTAINER, 10)
        val blacklist = linkedSetOf<ItemSlot>()

        val actions = ContainerTransferPlanner.plan(
            storageSlots = listOf(slot(stack(dirt, 12), ItemSlot.Type.INVENTORY, 1)),
            screen = null,
            from = source,
            targetBlacklist = blacklist,
            useQuickMove = false,
        )

        assertNull(actions)
        assertTrue(blacklist.isEmpty())
    }

    @Test
    fun `partial drag transfer puts the remaining source stack back`() {
        val source = slot(stack(stone, 8), ItemSlot.Type.CONTAINER, 10)
        val partialTarget = slot(stack(stone, 60), ItemSlot.Type.INVENTORY, 1)
        val blacklist = linkedSetOf<ItemSlot>()

        val actions = ContainerTransferPlanner.plan(
            storageSlots = listOf(partialTarget),
            screen = null,
            from = source,
            targetBlacklist = blacklist,
            useQuickMove = false,
        ).orEmpty()

        assertEquals(List(3) { ContainerInput.PICKUP }, actions.map { it.actionType })
        assertEquals(listOf(source, partialTarget, source), actions.map { it.slot })
        assertEquals(listOf(source, partialTarget), blacklist.toList())
    }

    @Test
    fun `quick swap skips invalid transactions without reordering later swaps`() {
        val invalidSource = slot(stack(stone), ItemSlot.Type.CONTAINER, 10)
        val validSource = slot(stack(stone), ItemSlot.Type.CONTAINER, 11)
        val invalidTarget = HotbarItemSlot.SLOT_0
        val validTarget = HotbarItemSlot.SLOT_2
        val plan = cleanupPlan(
            swaps = mutableListOf(
                InventorySwap(invalidSource, invalidTarget, Priority.NOT_IMPORTANT),
                InventorySwap(validSource, validTarget, Priority.IMPORTANT_FOR_USAGE_2),
            )
        )

        val transaction = HotbarSwapSelector.select(
            cleanupPlan = plan,
            screen = null,
            inventorySlots = emptyList(),
            stackOf = { ItemStack.EMPTY },
            canSwap = { true },
            serverIdOf = { if (it === invalidSource) null else 1 },
        )

        assertEquals(Priority.IMPORTANT_FOR_USAGE_2, transaction?.priority)
        assertSame(validSource, transaction?.actions?.single()?.slot)
        assertEquals(validTarget.inventorySlot, transaction?.actions?.single()?.button)
    }

    @Test
    fun `useful hotbar target moves aside before the container swap`() {
        val source = slot(stack(stone), ItemSlot.Type.CONTAINER, 10)
        val emptyInventory = slot(ItemStack.EMPTY, ItemSlot.Type.INVENTORY, 3)
        val target = HotbarItemSlot.SLOT_4
        val plan = cleanupPlan(
            usefulItems = mutableSetOf(target),
            swaps = mutableListOf(InventorySwap(source, target, Priority.NORMAL)),
        )

        val actions = selectSwapActions(plan, target, stack(stone), listOf(emptyInventory))

        assertEquals(listOf(emptyInventory, source), actions.map { it.slot })
        assertEquals(List(2) { ContainerInput.SWAP }, actions.map { it.actionType })
    }

    @Test
    fun `useless hotbar target is thrown before the container swap`() {
        val source = slot(stack(stone), ItemSlot.Type.CONTAINER, 10)
        val target = HotbarItemSlot.SLOT_4
        val plan = cleanupPlan(
            swaps = mutableListOf(InventorySwap(source, target, Priority.NORMAL)),
        )

        val actions = selectSwapActions(plan, target, stack(dirt), emptyList())

        assertEquals(listOf(ContainerInput.THROW, ContainerInput.SWAP), actions.map { it.actionType })
        assertEquals(listOf(target, source), actions.map { it.slot })
    }

    @Test
    fun `offhand target swaps directly without a container throw`() {
        val source = slot(stack(stone), ItemSlot.Type.CONTAINER, 10)
        val target = HotbarItemSlot.OFFHAND
        val plan = cleanupPlan(
            swaps = mutableListOf(InventorySwap(source, target, Priority.NORMAL)),
        )

        val actions = selectSwapActions(plan, target, stack(dirt), emptyList())

        assertEquals(listOf(ContainerInput.SWAP), actions.map { it.actionType })
        assertSame(source, actions.single().slot)
    }

    @Test
    fun `overflow discard keeps candidate order and blacklist accounting`() {
        val useful = slot(stack(stone), ItemSlot.Type.INVENTORY, 1)
        val firstUseless = slot(stack(dirt), ItemSlot.Type.INVENTORY, 2)
        val secondUseless = slot(stack(dirt), ItemSlot.Type.INVENTORY, 3)
        val plan = cleanupPlan(usefulItems = mutableSetOf(useful))
        val blacklist = linkedSetOf<ItemSlot>()

        val actions = LootCapacityPlanner.discardActions(
            cleanupPlan = plan,
            screen = null,
            targetBlacklist = blacklist,
            discardWhenFull = true,
            inventoryItems = listOf(useful, firstUseless, secondUseless),
            serverIdOf = { 1 },
        )

        assertSame(firstUseless, actions?.single()?.let { (it as InventoryAction.Click).slot })
        assertEquals(listOf(firstUseless), blacklist.toList())
    }

    @Test
    fun `required space subtracts empty slots and chest merge gains`() {
        val mergeItem = componentBoundItem("mergeable", maxStackSize = 64)
        assertEquals(64, mergeItem.defaultMaxStackSize)
        val chestOne = slot(stack(mergeItem, 32), ItemSlot.Type.CONTAINER, 1)
        val chestTwo = slot(stack(mergeItem, 32), ItemSlot.Type.CONTAINER, 2)
        val inventory = slot(stack(mergeItem, 32), ItemSlot.Type.INVENTORY, 3)
        val plan = cleanupPlan(
            mergeableItems = hashMapOf(
                ItemAndComponents(mergeItem) to mutableListOf(chestOne, chestTwo, inventory),
            )
        )

        val required = LootCapacityPlanner.requiredSpace(
            cleanupPlan = plan,
            slotsToCollect = 4,
            storageSlots = listOf(slot(ItemStack.EMPTY, ItemSlot.Type.INVENTORY, 4)),
        )

        assertEquals(2, required)
    }

    private fun selectSwapActions(
        plan: InventoryCleanupPlan,
        target: HotbarItemSlot,
        targetStack: ItemStack,
        inventorySlots: List<ItemSlot>,
    ): List<InventoryAction.Click> = HotbarSwapSelector.select(
        cleanupPlan = plan,
        screen = null,
        inventorySlots = inventorySlots,
        stackOf = { if (it === target) targetStack else it.itemStack },
        canSwap = { true },
        serverIdOf = { 1 },
    )!!.actions

    private fun cleanupPlan(
        usefulItems: MutableSet<ItemSlot> = mutableSetOf(),
        swaps: MutableList<InventorySwap> = mutableListOf(),
        mergeableItems: MutableMap<ItemAndComponents, MutableList<ItemSlot>> = hashMapOf(),
    ) = InventoryCleanupPlan(usefulItems, swaps, mergeableItems)

    private fun slot(stack: ItemStack, type: ItemSlot.Type, id: Int) = VirtualItemSlot(stack, type, id)

    private fun stack(item: Item, count: Int = 1): ItemStack = ItemStack(
        holders.getOrPut(item) {
            Holder.direct(item, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build())
        },
        count,
    )

    private fun item(path: String): Item = Item(
        Item.Properties().setId(
            ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("liquidbounce_test", path))
        )
    )

    private fun componentBoundItem(path: String, maxStackSize: Int): Item = item(path).also {
        it.builtInRegistryHolder().bindComponents(
            DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, maxStackSize).build()
        )
    }

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() = MinecraftBootstrap.ensureInitialized()
    }
}
