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

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.ccbluex.fastutil.fastIterator
import net.minecraft.core.Holder
import net.minecraft.core.TypedInstance
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/** Immutable item identity for grouping stacks with the same item and component patch. */
@JvmRecord
internal data class ItemComponentIdentity(
    val item: Item,
    val componentsPatch: DataComponentPatch = DataComponentPatch.EMPTY,
) : TypedInstance<Item> {
    constructor(itemStack: ItemStack) : this(itemStack.item, itemStack.componentsPatch)

    override fun typeHolder(): Holder<Item> = BuiltInRegistries.ITEM.wrapAsHolder(item)

    fun toItemStack(count: Int): ItemStack = ItemStack(typeHolder(), count, componentsPatch)
}

internal fun mergeItemStacksByComponents(
    stacks: Array<ItemStack>,
    comparator: Comparator<ItemStack>,
): Array<ItemStack> {
    val counts = aggregateItemComponentCounts(
        stacks.asIterable(),
        identityOf = { ItemComponentIdentity(it) },
        countOf = ItemStack::getCount,
    )

    val iterator = counts.fastIterator()
    return Array(counts.size) {
        val entry = iterator.next()
        entry.key.toItemStack(entry.intValue)
    }.apply {
        sortWith(comparator)
    }
}

internal inline fun <T> aggregateItemComponentCounts(
    values: Iterable<T>,
    identityOf: (T) -> ItemComponentIdentity,
    countOf: (T) -> Int,
): Object2IntOpenHashMap<ItemComponentIdentity> {
    val counts = Object2IntOpenHashMap<ItemComponentIdentity>()
    for (value in values) {
        counts.addTo(identityOf(value), countOf(value))
    }
    return counts
}
