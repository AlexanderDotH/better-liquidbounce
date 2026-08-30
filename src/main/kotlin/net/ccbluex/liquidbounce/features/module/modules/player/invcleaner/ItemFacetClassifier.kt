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

import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.ArmorItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.ArrowItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.BlockItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.BowItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.CrossbowItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.FoodItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.GodAxeFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.ItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.MaceItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.MiningToolItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.PotionItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.PrimitiveItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.RodItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.SharpAxeFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.ShieldItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.SpearItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.SwordItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.ThrowableItemFacet
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.WeaponItemFacet
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.item.armor.ArmorComparator
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.ccbluex.liquidbounce.utils.item.getPotionEffects
import net.ccbluex.liquidbounce.utils.item.isAxe
import net.ccbluex.liquidbounce.utils.item.isFood
import net.ccbluex.liquidbounce.utils.item.isMiningTool
import net.ccbluex.liquidbounce.utils.item.isPlayerArmor
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.item.isSword
import net.minecraft.world.item.ArrowItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.EggItem
import net.minecraft.world.item.EnderpearlItem
import net.minecraft.world.item.FishingRodItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.MaceItem
import net.minecraft.world.item.PotionItem
import net.minecraft.world.item.ShieldItem
import net.minecraft.world.item.SnowballItem
import net.minecraft.world.item.WindChargeItem
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.material.LavaFluid
import net.minecraft.world.level.material.WaterFluid

internal class ItemFacetClassifier(
    private val futureArmorToKeep: List<ItemSlot>,
    private val armorComparator: ArmorComparator,
) {
    fun classify(slot: ItemSlot): List<ItemFacet> {
        val itemStack = slot.itemStack
        if (itemStack.isEmpty) {
            return emptyList()
        }

        return buildList {
            // Everything could be a weapon, including a stick with a high knockback enchantment.
            add(WeaponItemFacet(slot))
            addAll(classifyPrimaryFacets(slot, itemStack))
        }
    }

    private fun classifyPrimaryFacets(slot: ItemSlot, itemStack: ItemStack): List<ItemFacet> {
        classifyEquipmentFacet(slot, itemStack)?.let { return listOf(it) }
        classifySpecialFacets(slot, itemStack)?.let { return it }
        return listOf(classifyGeneralFacet(slot, itemStack))
    }

    private fun classifyEquipmentFacet(slot: ItemSlot, itemStack: ItemStack): ItemFacet? =
        when (itemStack.item) {
            is BowItem -> BowItemFacet(slot)
            is CrossbowItem -> CrossbowItemFacet(slot)
            is ArrowItem -> ArrowItemFacet(slot)
            is FishingRodItem -> RodItemFacet(slot)
            is ShieldItem -> ShieldItemFacet(slot)
            else -> null
        }

    private fun classifySpecialFacets(slot: ItemSlot, itemStack: ItemStack): List<ItemFacet>? =
        when (val item = itemStack.item) {
            is BlockItem -> listOf(classifyBlockFacet(slot, itemStack))
            Items.MILK_BUCKET -> listOf(PrimitiveItemFacet(slot, ItemSortChoice.MILK.category))
            is BucketItem -> listOf(classifyBucketFacet(slot, item))
            is PotionItem -> listOf(classifyPotionFacet(slot, itemStack))
            is EnderpearlItem -> listOf(PrimitiveItemFacet(slot, ItemType.PEARL.defaultCategory))
            Items.GOLDEN_APPLE -> listOf(
                FoodItemFacet(slot),
                PrimitiveItemFacet(slot, ItemType.GAPPLE.defaultCategory),
            )
            Items.ENCHANTED_GOLDEN_APPLE -> listOf(
                FoodItemFacet(slot),
                PrimitiveItemFacet(slot, ItemType.GAPPLE.defaultCategory, 1),
            )
            is EggItem, is SnowballItem, is WindChargeItem -> listOf(ThrowableItemFacet(slot))
            else -> null
        }

    private fun classifyBlockFacet(slot: ItemSlot, itemStack: ItemStack): ItemFacet {
        val isUsefulBlock = ScaffoldBlockItemSelection.isValidBlock(itemStack) &&
            !ScaffoldBlockItemSelection.isBlockUnfavourable(itemStack)
        return if (isUsefulBlock) BlockItemFacet(slot) else ItemFacet(slot)
    }

    private fun classifyBucketFacet(slot: ItemSlot, item: BucketItem): ItemFacet {
        val category = when (item.content) {
            is WaterFluid -> ItemSortChoice.WATER.category
            is LavaFluid -> ItemSortChoice.LAVA.category
            else -> ItemCategory(ItemType.BUCKET, item.content.javaClass.hashCode())
        }
        return PrimitiveItemFacet(slot, category)
    }

    private fun classifyPotionFacet(slot: ItemSlot, itemStack: ItemStack): ItemFacet {
        val areAllEffectsGood = itemStack.getPotionEffects().all {
            it.effect in PotionItemFacet.GOOD_STATUS_EFFECTS
        }
        return if (areAllEffectsGood) PotionItemFacet(slot) else ItemFacet(slot)
    }

    private fun classifyGeneralFacet(slot: ItemSlot, itemStack: ItemStack): ItemFacet = when {
        itemStack.isAxe -> classifyAxeFacet(slot, itemStack)
        itemStack.isPlayerArmor -> ArmorItemFacet(slot, futureArmorToKeep, armorComparator)
        itemStack.isSword -> SwordItemFacet(slot)
        itemStack.isSpear -> SpearItemFacet(slot)
        itemStack.item is MaceItem -> MaceItemFacet(slot)
        itemStack.isMiningTool -> MiningToolItemFacet(slot)
        itemStack.isFood -> FoodItemFacet(slot)
        else -> ItemFacet(slot)
    }

    private fun classifyAxeFacet(slot: ItemSlot, itemStack: ItemStack): ItemFacet {
        val sharpnessLevel = itemStack.getEnchantment(Enchantments.SHARPNESS)
        return when {
            sharpnessLevel >= 100 -> GodAxeFacet(slot)
            sharpnessLevel >= 5 -> SharpAxeFacet(slot)
            else -> MiningToolItemFacet(slot)
        }
    }
}
