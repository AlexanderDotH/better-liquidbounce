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

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items.MiningToolItemFacet
import net.ccbluex.liquidbounce.utils.item.foodComponent
import net.ccbluex.liquidbounce.utils.item.isAxe
import net.ccbluex.liquidbounce.utils.item.isHoe
import net.ccbluex.liquidbounce.utils.item.isPickaxe
import net.ccbluex.liquidbounce.utils.item.isShovel
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.item.isSword
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.MaceItem
import java.util.function.Predicate

@JvmRecord
data class ItemCategory(val type: ItemType, val subtype: Int) {
    fun isEmpty(): Boolean = type == ItemType.NONE
}

enum class ItemType(
    val oneIsSufficient: Boolean,
    /**
     * Higher priority means the item category is filled in first.
     *
     * This is important for specializations. If we have a weapon slot and an axe slot, an axe would fit in both,
     * but because the player specifically requested an axe, the best axe should be filled in first.
     */
    val allocationPriority: Priority = Priority.NORMAL,
    /**
     * Identifies a shared function so items of different categories can satisfy the same constraint.
     */
    val providedFunction: ItemFunction? = null
) {
    ARMOR(true, allocationPriority = Priority.IMPORTANT_FOR_PLAYER_LIFE),
    SWORD(true, allocationPriority = Priority.IMPORTANT_FOR_USAGE_3, providedFunction = ItemFunction.WEAPON_LIKE),
    WEAPON(true, allocationPriority = Priority.IMPORTANT_FOR_USAGE_2, providedFunction = ItemFunction.WEAPON_LIKE),
    SPEAR(true, allocationPriority = Priority.IMPORTANT_FOR_USAGE_3, providedFunction = ItemFunction.WEAPON_LIKE),
    MACE(true, allocationPriority = Priority.IMPORTANT_FOR_USAGE_2, providedFunction = ItemFunction.WEAPON_LIKE),
    BOW(true),
    CROSSBOW(true),
    ARROW(true),
    TOOL(true, allocationPriority = Priority.IMPORTANT_FOR_USAGE_1),
    ROD(true),
    THROWABLE(false),
    SHIELD(true),
    FOOD(false),
    BUCKET(false),
    PEARL(false, allocationPriority = Priority.IMPORTANT_FOR_USAGE_1),
    GAPPLE(false, allocationPriority = Priority.IMPORTANT_FOR_USAGE_1),
    POTION(false),
    BLOCK(false),
    NONE(false);

    val defaultCategory = ItemCategory(this, 0)
}

enum class ItemFunction {
    WEAPON_LIKE,
    FOOD,
}

enum class ItemSortChoice(
    override val tag: String,
    val category: ItemCategory,
    /**
     * This is the function that is used for the greedy check.
     *
     * IF IT WAS IMPLEMENTED
     */
    val satisfactionCheck: Predicate<ItemStack>? = null,
) : Tagged {
    SWORD("Sword", ItemType.SWORD.defaultCategory, { it.isSword }),
    WEAPON("Weapon", ItemType.WEAPON.defaultCategory),
    SPEAR("Spear", ItemType.SPEAR.defaultCategory, { it.isSpear }),
    MACE("Mace", ItemType.MACE.defaultCategory, { it.item is MaceItem }),
    BOW("Bow", ItemType.BOW.defaultCategory),
    CROSSBOW("Crossbow", ItemType.CROSSBOW.defaultCategory),
    AXE("Axe", ItemCategory(ItemType.TOOL, MiningToolItemFacet.MASK_AXE), { it.isAxe }),
    PICKAXE("Pickaxe", ItemCategory(ItemType.TOOL, MiningToolItemFacet.MASK_PICKAXE), { it.isPickaxe }),
    SHOVEL("Shovel", ItemCategory(ItemType.TOOL, MiningToolItemFacet.MASK_SHOVEL), { it.isShovel }),
    HOE("Hoe", ItemCategory(ItemType.TOOL, MiningToolItemFacet.MASK_HOE), { it.isHoe }),
    ROD("Rod", ItemType.ROD.defaultCategory),
    SHIELD("Shield", ItemType.SHIELD.defaultCategory),
    WATER("Water", ItemType.BUCKET.defaultCategory),
    LAVA("Lava", ItemCategory(ItemType.BUCKET, 1)),
    MILK("Milk", ItemCategory(ItemType.BUCKET, 2)),
    PEARL("Pearl", ItemType.PEARL.defaultCategory, { it.item == Items.ENDER_PEARL }),
    GAPPLE(
        "Gapple",
        ItemType.GAPPLE.defaultCategory,
        Predicate { it.item == Items.GOLDEN_APPLE || it.item == Items.ENCHANTED_GOLDEN_APPLE },
    ),
    FOOD("Food", ItemType.FOOD.defaultCategory, { it.foodComponent != null }),
    POTION("Potion", ItemType.POTION.defaultCategory),
    BLOCK("Block", ItemType.BLOCK.defaultCategory, { it.item is BlockItem }),
    THROWABLES("Throwables", ItemType.THROWABLE.defaultCategory),
    IGNORE("Ignore", ItemType.NONE.defaultCategory),
    NONE("None", ItemType.NONE.defaultCategory),
}
