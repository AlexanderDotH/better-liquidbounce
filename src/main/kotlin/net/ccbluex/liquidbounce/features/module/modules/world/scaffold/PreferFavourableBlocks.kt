/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold

import net.minecraft.world.item.ItemStack

object PreferFavourableBlocks : Comparator<ItemStack> {
    override fun compare(first: ItemStack, second: ItemStack): Int = compareValuesBy(first, second) {
        !ScaffoldBlockItemSelection.isBlockUnfavourable(it)
    }
}
