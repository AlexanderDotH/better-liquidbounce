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
package net.ccbluex.liquidbounce.features.module.modules.misc.safeactions

import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SafeDropModelsTest {

    private val holders = IdentityHashMap<Item, Holder<Item>>()

    @Test
    fun `equivalent world actions have the same context and action`() {
        val world = Any()

        val first = SafeDropAction.world(world, slot = 4, stack = stack(Items.DIAMOND, 3), dropAll = false)
        val second = SafeDropAction.world(world, slot = 4, stack = stack(Items.DIAMOND, 3), dropAll = false)

        assertEquals(SafeDropSource.WORLD, first.context.source)
        assertEquals(first.context, second.context)
        assertEquals(first, second)
    }

    @Test
    fun `world scope equality is referential even when scopes claim value equality`() {
        val firstScope = AlwaysEqualScope()
        val secondScope = AlwaysEqualScope()

        val first = SafeDropAction.world(
            firstScope,
            slot = 2,
            stack = stack(Items.DIAMOND, 1),
            dropAll = false,
        )
        val second = SafeDropAction.world(
            secondScope,
            slot = 2,
            stack = stack(Items.DIAMOND, 1),
            dropAll = false,
        )

        assertNotEquals(first.context, second.context)
    }

    @Test
    fun `container context requires the same screen and menu references`() {
        val screen = AlwaysEqualScope()
        val menu = AlwaysEqualScope()
        val same = SafeDropAction.container(
            screen,
            menu,
            slot = 7,
            stack = stack(Items.DIAMOND, 5),
            dropAll = true,
        )
        val differentScreen = SafeDropAction.container(
            AlwaysEqualScope(),
            menu,
            slot = 7,
            stack = stack(Items.DIAMOND, 5),
            dropAll = true,
        )
        val differentMenu = SafeDropAction.container(
            screen,
            AlwaysEqualScope(),
            slot = 7,
            stack = stack(Items.DIAMOND, 5),
            dropAll = true,
        )

        assertEquals(SafeDropSource.CONTAINER, same.context.source)
        assertNotEquals(same.context, differentScreen.context)
        assertNotEquals(same.context, differentMenu.context)
    }

    @Test
    fun `source or slot change creates a different context`() {
        val sharedScope = Any()
        val stack = stack(Items.DIAMOND, 2)
        val world = SafeDropAction.world(sharedScope, slot = 1, stack = stack, dropAll = false)
        val otherSlot = SafeDropAction.world(sharedScope, slot = 2, stack = stack, dropAll = false)
        val container = SafeDropAction.container(
            sharedScope,
            sharedScope,
            slot = 1,
            stack = stack,
            dropAll = false,
        )

        assertNotEquals(world.context, otherSlot.context)
        assertNotEquals(world.context, container.context)
    }

    @Test
    fun `item change creates a different context`() {
        val world = Any()
        val diamond = SafeDropAction.world(world, slot = 0, stack = stack(Items.DIAMOND, 1), dropAll = false)
        val emerald = SafeDropAction.world(world, slot = 0, stack = stack(Items.EMERALD, 1), dropAll = false)

        assertNotEquals(diamond.context, emerald.context)
    }

    @Test
    fun `component change creates a different context without mutating the captured context`() {
        val world = Any()
        val mutableStack = stack(Items.DIAMOND, 1).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Before"))
        }
        val before = SafeDropAction.world(world, slot = 0, stack = mutableStack, dropAll = false)

        mutableStack.set(DataComponents.CUSTOM_NAME, Component.literal("After"))
        val after = SafeDropAction.world(world, slot = 0, stack = mutableStack, dropAll = false)
        val recapturedBefore = SafeDropAction.world(
            world,
            slot = 0,
            stack(Items.DIAMOND, 1).apply {
                set(DataComponents.CUSTOM_NAME, Component.literal("Before"))
            },
            dropAll = false,
        )

        assertNotEquals(before.context, after.context)
        assertEquals(before.context, recapturedBefore.context)
    }

    @Test
    fun `count change creates a different context without mutating the captured context`() {
        val world = Any()
        val mutableStack = stack(Items.DIAMOND, 3)
        val threeDiamonds = SafeDropAction.world(world, slot = 0, stack = mutableStack, dropAll = false)

        mutableStack.count = 2
        val twoDiamonds = SafeDropAction.world(world, slot = 0, stack = mutableStack, dropAll = false)
        val recapturedThree = SafeDropAction.world(
            world,
            slot = 0,
            stack = stack(Items.DIAMOND, 3),
            dropAll = false,
        )

        assertNotEquals(threeDiamonds.context, twoDiamonds.context)
        assertEquals(threeDiamonds.context, recapturedThree.context)
    }

    @Test
    fun `drop all changes the action but not its context`() {
        val world = Any()
        val single = SafeDropAction.world(world, slot = 3, stack = stack(Items.DIAMOND, 12), dropAll = false)
        val all = SafeDropAction.world(world, slot = 3, stack = stack(Items.DIAMOND, 12), dropAll = true)

        assertEquals(single.context, all.context)
        assertNotEquals(single, all)
    }

    private fun stack(item: Item, count: Int) = ItemStack(holder(item), count)

    private fun holder(item: Item): Holder<Item> = holders.getOrPut(item) {
        Holder.direct(item, DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build())
    }

    private class AlwaysEqualScope {
        override fun equals(other: Any?) = other is AlwaysEqualScope
        override fun hashCode() = 1
    }
}
