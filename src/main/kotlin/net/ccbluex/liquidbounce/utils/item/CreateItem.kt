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

import com.mojang.brigadier.StringReader
import net.ccbluex.liquidbounce.utils.client.isOlderThanOrEqual1_8
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.entity.handItems
import net.ccbluex.liquidbounce.utils.kotlin.unmodifiable
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.commands.arguments.item.ItemInput
import net.minecraft.commands.arguments.item.ItemParser
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentGetter
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.Enchantments
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/**
 * Create item with NBT tags
 *
 * @docs https://minecraft.gamepedia.com/Commands/give
 */
fun ClientLevel.createItem(raw: String) = ItemParser(registryAccess())
    .parse(StringReader(raw))
    .createItemStack(1)

/**
 * Create item with NBT tags
 *
 * @docs https://minecraft.gamepedia.com/Commands/give
 */
fun createItem(stack: String, amount: Int = 1): ItemStack =
    ItemParser(mc.level!!.registryAccess()).parse(StringReader(stack)).let {
        ItemInput(it.item, it.components).createItemStack(amount)
    }

/**
 * Set player inventory item (Creative mode only)
 *
 * @see net.minecraft.client.multiplayer.MultiPlayerGameMode.handleCreativeModeItemAdd
 */
fun LocalPlayer.setInventoryItemCreative(
    slot: Int = this.inventory.selectedSlot,
    itemStack: ItemStack,
    animation: Boolean = true,
) {
    if (animation) itemStack.popTime = 5

    inventory.setItem(slot, itemStack)
    connection.send(
        ServerboundSetCreativeModeSlotPacket(
            if (slot < Inventory.SELECTION_SIZE) slot + Inventory.INVENTORY_SIZE else slot,
            itemStack,
        )
    )
}

fun createSplashPotion(name: String, vararg effects: MobEffectInstance): ItemStack {
    val itemStack = ItemStack(Items.SPLASH_POTION)

    itemStack.set(DataComponents.CUSTOM_NAME, regular(name))
    itemStack.set(
        DataComponents.POTION_CONTENTS,
        PotionContents(Optional.empty(), Optional.empty(), effects.unmodifiable(), Optional.empty())
    )

    return itemStack
}

fun ItemStack.getPotionEffects(): Iterable<MobEffectInstance> {
    return this[DataComponents.POTION_CONTENTS]?.allEffects ?: emptyList()
}

/**
 * @return if this item stack has same [Item] and [net.minecraft.core.component.DataComponentPatch]
 * with the other item stack
 */
inline fun ItemStack.isMergeable(other: ItemStack): Boolean = ItemStack.isSameItemSameComponents(this, other)

fun ItemStack.canMerge(other: ItemStack): Boolean {
    return this.isMergeable(other) && this.count + other.count <= this.maxStackSize
}

val ItemStack.attackDamage: Double
    get() {
        val baseDamage = getAttributeValue(
            Attributes.ATTACK_DAMAGE,
            EquipmentSlot.MAINHAND,
            player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)
        )

        /*
         * Client-side damage calculation for enchantments does not exist anymore
         * see https://bugs.mojang.com/browse/MC-196250
         *
         * We now use the following formula to calculate the damage:
         * https://minecraft.wiki/w/Sharpness
         * >= 1.9 -> 0.5 * level + 0.5
         * else -> 1.25 * level
         */
        return baseDamage + getSharpnessDamage()
    }

@JvmOverloads
fun ItemStack.getSharpnessDamage(level: Int = getEnchantment(Enchantments.SHARPNESS)): Double =
    if (!isOlderThanOrEqual1_8) {
        when (level) {
            0 -> 0.0
            else -> 0.5 * level + 0.5
        }
    } else {
        level * 1.25
    }

val ItemStack.attackSpeed: Double
    get() = getAttributeValue(
        Attributes.ATTACK_SPEED,
        EquipmentSlot.MAINHAND,
        player.getAttributeBaseValue(Attributes.ATTACK_SPEED)
    )

val ItemStack.durability
    get() = this.maxDamage - this.damageValue

/**
 * @param slot if null, all modifiers for the attribute will be applied, otherwise only modifiers for the specified slot
 *
 * @see net.minecraft.world.item.component.ItemAttributeModifiers
 * @see net.minecraft.world.entity.ai.attributes.AttributeInstance
 */
@JvmOverloads
fun DataComponentGetter.getAttributeValue(
    attribute: Holder<Attribute>,
    slot: EquipmentSlot? = null,
    baseValue: Double = attribute.value().defaultValue,
): Double {
    val attributeModifiers = this[DataComponents.ATTRIBUTE_MODIFIERS]
        ?: return attribute.value().sanitizeValue(baseValue)

    if (slot != null) {
        return attribute.value().sanitizeValue(attributeModifiers.compute(attribute, baseValue, slot))
    }

    var value = baseValue

    for (entry in attributeModifiers.modifiers) {
        if (entry.attribute == attribute) {
            val modifier = entry.modifier
            val amount = modifier.amount

            value += when (modifier.operation) {
                AttributeModifier.Operation.ADD_VALUE -> amount
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE -> amount * baseValue
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL -> amount * value
            }
        }
    }

    return attribute.value().sanitizeValue(value)
}

fun <E : Any> ResourceKey<Registry<E>>.getOrNull(): Registry<E>? =
    mc.level?.registryAccess()?.lookup(this)?.getOrNull()

fun ResourceKey<Enchantment>.toRegistryEntryOrNull(): Holder<Enchantment>? =
    Registries.ENCHANTMENT.getOrNull()?.get(this)?.getOrNull()
