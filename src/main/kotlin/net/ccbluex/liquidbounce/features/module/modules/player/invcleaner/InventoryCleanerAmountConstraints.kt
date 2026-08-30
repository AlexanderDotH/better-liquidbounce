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

import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Reference2IntMap
import net.ccbluex.fastutil.component1
import net.ccbluex.fastutil.component2
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.ItemFacet

internal class InventoryCleanerAmountConstraints(
    private val desiredItemsPerCategory: Object2IntMap<ItemCategory>,
    private val desiredValuePerFunction: Reference2IntMap<ItemFunction>,
) {
    fun constraintsFor(facet: ItemFacet): MutableList<ItemConstraintInfo> {
        if (facet.providedItemFunctions.isEmpty()) {
            return mutableListOf(categoryConstraintFor(facet))
        }

        return facet.providedItemFunctions.mapTo(mutableListOf()) { (function, amountAdded) ->
            ItemConstraintInfo(
                group = ItemFunctionCategoryConstraintGroup(
                    desiredValuePerFunction.getOrDefault(function, 1)..Int.MAX_VALUE,
                    10,
                    function,
                ),
                amountAddedByItem = amountAdded,
            )
        }
    }

    private fun categoryConstraintFor(facet: ItemFacet): ItemConstraintInfo {
        val defaultDesiredAmount = if (facet.category.type.oneIsSufficient) 1 else Int.MAX_VALUE
        val desiredAmount = desiredItemsPerCategory.getOrDefault(facet.category, defaultDesiredAmount)

        return ItemConstraintInfo(
            group = ItemCategoryConstraintGroup(
                desiredAmount..Int.MAX_VALUE,
                10,
                facet.category,
            ),
            amountAddedByItem = facet.itemStack.count,
        )
    }
}
