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



@file:JvmName("ItemExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.item

import net.ccbluex.liquidbounce.utils.client.isOlderThanOrEqual1_8
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.entity.handItems
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.kotlin.unmodifiable
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ArmorStandItem
import net.minecraft.world.item.ArrowItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.BoatItem
import net.minecraft.world.item.BottleItem
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.BrushItem
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.EggItem
import net.minecraft.world.item.EmptyMapItem
import net.minecraft.world.item.EnderEyeItem
import net.minecraft.world.item.EnderpearlItem
import net.minecraft.world.item.ExperienceBottleItem
import net.minecraft.world.item.FireChargeItem
import net.minecraft.world.item.FireworkRocketItem
import net.minecraft.world.item.FishingRodItem
import net.minecraft.world.item.FlintAndSteelItem
import net.minecraft.world.item.HangingEntityItem
import net.minecraft.world.item.InstrumentItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.KnowledgeBookItem
import net.minecraft.world.item.PlaceOnWaterBlockItem
import net.minecraft.world.item.PotionItem
import net.minecraft.world.item.SnowballItem
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.item.SpyglassItem
import net.minecraft.world.item.TridentItem
import net.minecraft.world.item.WindChargeItem
import net.minecraft.world.item.WritableBookItem
import net.minecraft.world.item.WrittenBookItem
import net.minecraft.world.item.component.UseEffects
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import kotlin.jvm.optionals.getOrNull

/**
 * @see net.minecraft.world.entity.player.Player.getDestroySpeed
 * @see net.minecraft.world.entity.ai.attributes.Attributes.MINING_EFFICIENCY
 * @see net.minecraft.world.item.enchantment.LevelBasedValue.LevelsSquared
 */
fun ItemStack.getDestroySpeedWithEnchantment(state: BlockState): Float {
    var speed = this.getDestroySpeed(state)

    val enchantmentLevel = this.getEnchantment(Enchantments.EFFICIENCY)
    if (speed > 1f && enchantmentLevel != 0) {
        val enchantmentAddition = enchantmentLevel.sq() + 1f
        speed += enchantmentAddition.coerceIn(0f, 1024f)
    }

    return speed
}

/**
 * Get [Block] of inner item if it is [BlockItem], or null if not
 */
fun ItemStack.getBlock(): Block? {
    val item = this.item
    if (item !is BlockItem) {
        return null
    }

    return item.block
}

fun ItemStack.isFullBlock(): Boolean {
    val block = this.getBlock() ?: return false
    return block.defaultBlockState().isCollisionShapeFullBlock(mc.level!!, BlockPos.ZERO)
}

fun ItemStack.isInteractable(): Boolean {
    if (this.isEmpty) {
        return false
    }

    return isEquippableInteraction()
        || hasInteractionComponent()
        || isDirectUseInteraction()
        || isUseOnInteraction()
}

private fun ItemStack.isEquippableInteraction(): Boolean {
    val equippable = get(DataComponents.EQUIPPABLE) ?: return false
    val equippedItem = player.getItemBySlot(equippable.slot)

    return equippable.swappable
        && player.canUseSlot(equippable.slot)
        && equippable.canBeEquippedBy(player.typeHolder())
        && !ItemStack.isSameItemSameComponents(this, equippedItem)
        && (!EnchantmentHelper.has(equippedItem, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
            || player.isCreative)
}

private fun ItemStack.hasInteractionComponent(): Boolean {
    val useEffects = get(DataComponents.USE_EFFECTS)
    return has(DataComponents.CONSUMABLE)
        || has(DataComponents.BLOCKS_ATTACKS) // Shield, 1.8 Sword
        || has(DataComponents.KINETIC_WEAPON) // Spear
        || useEffects != null && useEffects != UseEffects.DEFAULT
}

private fun ItemStack.isDirectUseInteraction(): Boolean = when (item) {
    is BowItem -> Slots.All.any { it.itemStack.item is ArrowItem }
    is CrossbowItem -> Slots.All.any { it.itemStack.item is ArrowItem }
        || player.handItems.any { it.item is FireworkRocketItem }
    is BoatItem,
    is BucketItem, // TODO: water/lava between an interactable block and the player (for empty buckets)
    is EggItem,
    is EmptyMapItem,
    is EnderEyeItem,
    is EnderpearlItem,
    is ExperienceBottleItem,
    is FireworkRocketItem,
    is FishingRodItem,
    is BottleItem, // TODO: water between an interactable block and the player
    is InstrumentItem, // TODO: item delay?
    is KnowledgeBookItem,
    is PlaceOnWaterBlockItem, // TODO: water between an interactable block and the player
    is SnowballItem,
    is SpawnEggItem,
    is SpyglassItem,
    is TridentItem,
    is WindChargeItem,
    is WritableBookItem,
    is WrittenBookItem -> true
    else -> false
}

private fun ItemStack.isUseOnInteraction(): Boolean =
    item is ArmorStandItem
        || item is BlockItem
        || item is BrushItem
        || item is HangingEntityItem // TODO: presence of other item frames and paintings on target blocks
        || item is FireChargeItem
        || item is FlintAndSteelItem
        || item is PotionItem
