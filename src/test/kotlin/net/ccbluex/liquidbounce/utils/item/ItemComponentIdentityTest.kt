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
package net.ccbluex.liquidbounce.utils.item

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.core.registries.Registries
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ItemComponentIdentityTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `key identity retains exactly item and component patch`() {
        val namedPatch = DataComponentPatch.builder()
            .set(DataComponents.CUSTOM_NAME, Component.literal("Tagged"))
            .build()
        val firstItem = item("first")
        val secondItem = item("second")
        val key = ItemComponentIdentity(firstItem, namedPatch)

        assertEquals(key, ItemComponentIdentity(firstItem, namedPatch))
        assertEquals(key.hashCode(), ItemComponentIdentity(firstItem, namedPatch).hashCode())
        assertNotEquals(key, ItemComponentIdentity(firstItem, DataComponentPatch.EMPTY))
        assertNotEquals(key, ItemComponentIdentity(secondItem, namedPatch))
    }

    @Test
    fun `component count aggregation retains totals without constructing item stacks`() {
        val first = ItemComponentIdentity(item("count_first"))
        val second = ItemComponentIdentity(item("count_second"))
        val values = listOf(
            CountedIdentity(first, 4),
            CountedIdentity(second, 2),
            CountedIdentity(first, 5),
        )
        val counts = aggregateItemComponentCounts(
            values,
            identityOf = CountedIdentity::identity,
            countOf = CountedIdentity::count,
        )

        assertEquals(9, counts.getInt(first))
        assertEquals(2, counts.getInt(second))
        assertEquals(2, counts.size)

        val source = Files.readString(
            Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/item/ItemComponentIdentity.kt")
        )
        assertTrue(source.contains("ItemStack(typeHolder(), count, componentsPatch)"))
        assertTrue(source.contains("sortWith(comparator)"))
    }

    private fun item(path: String): Item = Item(
        Item.Properties().setId(
            ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("liquidbounce_test", path))
        )
    )

    private data class CountedIdentity(val identity: ItemComponentIdentity, val count: Int)
}
