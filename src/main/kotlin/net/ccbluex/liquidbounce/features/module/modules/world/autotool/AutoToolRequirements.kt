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
package net.ccbluex.liquidbounce.features.module.modules.world.autotool

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.block.bed.BedBlockTracker
import net.ccbluex.liquidbounce.utils.block.getCenterDistanceSquaredEyes
import net.ccbluex.liquidbounce.utils.collection.Filter
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import java.util.function.BiPredicate

internal class AutoToolSilkTouchHandler(parent: EventListener) : ToggleableValueGroup(
    parent,
    "SilkTouchHandler",
    enabled = false,
), BiPredicate<ItemStack, BlockState> {
    private val filter by enumChoice("Filter", Filter.WHITELIST)
    private val blocks by blocks(
        "Blocks",
        blockSortedSetOf(Blocks.ENDER_CHEST, Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.TURTLE_EGG),
    )

    override fun test(itemStack: ItemStack, blockState: BlockState): Boolean =
        !running || blockState.block !in blocks ||
            (filter == Filter.BLACKLIST) == (itemStack.getEnchantment(Enchantments.SILK_TOUCH) == 0)
}

internal class AutoToolNearBedRequirement(parent: EventListener) : ToggleableValueGroup(
    parent,
    "RequireNearBed",
    enabled = false,
), BedBlockTracker.Subscriber {
    override val maxLayers: Int get() = 1

    private val distance by float("Distance", 10.0f, 3.0f..50.0f)

    override fun onEnabled() = BedBlockTracker.subscribe(this)

    override fun onDisabled() = BedBlockTracker.unsubscribe(this)

    fun matches(): Boolean =
        BedBlockTracker.allPositions().any { it.getCenterDistanceSquaredEyes() <= distance.sq() }
}
