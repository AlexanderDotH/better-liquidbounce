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

import net.ccbluex.fastutil.enumMapOf
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.ItemFacet
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.VirtualItemSlot
import net.ccbluex.liquidbounce.utils.item.armor.ArmorEvaluation
import net.ccbluex.liquidbounce.utils.item.armor.ArmorKitParameters
import net.ccbluex.liquidbounce.utils.item.armor.ArmorPiece
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

/**
 * @param expectedFullArmor what is the expected armor material when we have full armor (full iron, full dia, etc.)
 */
class ItemCategorization(
    availableItems: List<ItemSlot>,
) {
    companion object {
        @JvmStatic
        private fun constructArmorPiece(item: Item, id: Int): ArmorPiece {
            return ArmorPiece(VirtualItemSlot(item.defaultInstance, ItemSlot.Type.ARMOR, id))
        }

        /**
         * We expect to be full armor to be diamond armor.
         */
        @JvmStatic
        private val diamondArmorPieces: Map<EquipmentSlot, ArmorPiece> by lazy {
            enumMapOf(
                EquipmentSlot.HEAD, constructArmorPiece(Items.DIAMOND_HELMET, 0),
                EquipmentSlot.CHEST, constructArmorPiece(Items.DIAMOND_CHESTPLATE, 1),
                EquipmentSlot.LEGS, constructArmorPiece(Items.DIAMOND_LEGGINGS, 2),
                EquipmentSlot.FEET, constructArmorPiece(Items.DIAMOND_BOOTS, 3),
            )
        }

        @JvmField
        val Default = ItemCategorization(emptyList())
    }

    /**
     * Sometimes there are situations where armor pieces are not the best ones with the current armor, but become
     * the best ones as soon as we upgrade one of the other armor pieces.
     * In those cases, we don't want to miss out on this armor piece in the future thus we keep it.
     */
    private val facetClassifier: ItemFacetClassifier

    init {
        val findBestArmorPieces = ArmorEvaluation.findBestArmorPieces(slots = availableItems)

        val armorComparator = ArmorEvaluation.getArmorComparatorFor(findBestArmorPieces)

        val futureArmorToKeep = if (findBestArmorPieces.isEmpty()) {
            emptyList()
        } else {
            val armorParameterForSlot = ArmorKitParameters.getParametersForSlots(diamondArmorPieces)
            val armorComparatorForFullArmor = ArmorEvaluation.getArmorComparatorForParameters(armorParameterForSlot)

            ArmorEvaluation.findBestArmorPiecesWithComparator(
                availableItems,
                armorComparatorForFullArmor
            ).values.mapNotNull { it?.itemSlot }
        }

        facetClassifier = ItemFacetClassifier(futureArmorToKeep, armorComparator)
    }

    /**
     * Returns a list of facets an item represents. For example an axe is an axe, but also a sword:
     * - (SANDSTONE_BLOCK, 64) => `[Block(SANDSTONE_BLOCK, 64)]`
     * - (DIAMOND_AXE, 1) => `[Axe(DIAMOND_AXE, 1), Tool(DIAMOND_AXE, 1)]`
     */
    fun getItemFacets(slot: ItemSlot): List<ItemFacet> = facetClassifier.classify(slot)
}
