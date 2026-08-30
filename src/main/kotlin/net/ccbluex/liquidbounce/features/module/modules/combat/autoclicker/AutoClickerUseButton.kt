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
package net.ccbluex.liquidbounce.features.module.modules.combat.autoclicker

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.clicking.Clicker
import net.ccbluex.liquidbounce.features.module.modules.combat.autoclicker.contract.AutoClickerUseParentProvider
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.collection.itemSortedSetOf
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.TrapDoorBlock

internal class AutoClickerUseButton(
    parentProvider: AutoClickerUseParentProvider,
) : ToggleableValueGroup(parentProvider.parent(), "Use", false) {
    val clicker = tree(Clicker(this, mc.options.keyUse, null))
    val holdingItemsForIgnore by items(
        "HoldingItemsForIgnore",
        default = itemSortedSetOf(
            Items.WATER_BUCKET,
            Items.LAVA_BUCKET,
            Items.ENDER_PEARL,
            Items.ENDER_EYE,
            Items.PLAYER_HEAD,
        ),
    )
    val blocksForIgnore by blocks(
        "BlocksForIgnore",
        default = BuiltInRegistries.BLOCK.filterTo(blockSortedSetOf()) {
            it is DoorBlock || it is FenceGateBlock || it is TrapDoorBlock
        },
    )
    val delayStart by boolean("DelayStart", false)
    val onlyBlock by boolean("OnlyBlock", false)
    val requiresNoInput by boolean("RequiresNoInput", false)
    var needToWait = true
}
