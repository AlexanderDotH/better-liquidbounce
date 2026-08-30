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
package net.ccbluex.liquidbounce.features.module.modules.misc.antibot.custom

import net.ccbluex.fastutil.enumMapOf
import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.MultiChoiceListValue
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.contract.AntiBotPredicate
import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.function.IntPredicate
import java.util.function.Predicate

internal object CustomAntiBotArmor : ToggleableValueGroup(null, "Armor", false) {

    private val baseArmorChoices = enumSetOf(
        ArmorPredicate.NOTHING, ArmorPredicate.LEATHER, ArmorPredicate.CHAIN, ArmorPredicate.IRON,
        ArmorPredicate.GOLD, ArmorPredicate.DIAMOND, ArmorPredicate.NETHERITE,
    )
    private val helmet = enumSetOf(
        ArmorPredicate.NOTHING, ArmorPredicate.LEATHER, ArmorPredicate.CHAIN, ArmorPredicate.IRON,
        ArmorPredicate.GOLD, ArmorPredicate.DIAMOND, ArmorPredicate.NETHERITE,
        ArmorPredicate.TURTLE_SCUTE, ArmorPredicate.PUMPKIN, ArmorPredicate.SKULL,
    )
    private val chestplate = enumSetOf(
        ArmorPredicate.NOTHING, ArmorPredicate.LEATHER, ArmorPredicate.CHAIN, ArmorPredicate.IRON,
        ArmorPredicate.GOLD, ArmorPredicate.DIAMOND, ArmorPredicate.NETHERITE, ArmorPredicate.ELYTRA,
    )
    private val values = enumMapOf<EquipmentSlot, MultiChoiceListValue<ArmorPredicate>>(
        EquipmentSlot.HEAD, multiEnumChoice("Helmet", enumSetOf(ArmorPredicate.NOTHING), helmet),
        EquipmentSlot.CHEST, multiEnumChoice("Chestplate", enumSetOf(ArmorPredicate.NOTHING), chestplate),
        EquipmentSlot.LEGS, multiEnumChoice("Leggings", enumSetOf(ArmorPredicate.NOTHING), baseArmorChoices),
        EquipmentSlot.FEET, multiEnumChoice("Boots", enumSetOf(ArmorPredicate.NOTHING), baseArmorChoices),
    )

    fun isValid(entity: Player): Boolean = values.all { (slot, value) ->
        val predicates = value.get()
        val armor = entity.getItemBySlot(slot)
        predicates.isEmpty() || predicates.any { it.predicate.test(armor) }
    }

    private enum class ArmorPredicate(override val tag: String, val predicate: Predicate<ItemStack>) : Tagged {
        NOTHING("Nothing", Predicate(ItemStack::isEmpty)),
        LEATHER("Leather", Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS),
        CHAIN("Chain", Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS),
        IRON("Iron", Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
        GOLD("Gold", Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS),
        DIAMOND("Diamond", Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS),
        NETHERITE(
            "Netherite", Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
        ),
        ELYTRA("Elytra", Items.ELYTRA),
        TURTLE_SCUTE("TurtleScute", Items.TURTLE_HELMET),
        PUMPKIN("Pumpkin", Items.CARVED_PUMPKIN),
        SKULL("Skull", ItemTags.SKULLS);

        constructor(tag: String, itemTag: TagKey<Item>) : this(tag, Predicate { it.`is`(itemTag) })
        constructor(tag: String, item: Item) : this(tag, Predicate { it.`is`(item) })
        constructor(tag: String, vararg items: Item) : this(tag, Predicate { items.contains(it.item) })
    }
}

internal object CustomAntiBotName : ToggleableValueGroup(null, "Name", true), AntiBotPredicate {
    private val lengthRange by intRange("Length", 3..16, 1..32)
    private val validateChars by multiEnumChoice("ValidateChars", enumSetOf(CharacterValidator.VANILLA))

    override fun isBot(entity: Player): Boolean {
        val name = entity.scoreboardName
        return name.length !in lengthRange || validateChars.any { !it.test(name) }
    }

    private enum class CharacterValidator(override val tag: String) : Tagged, IntPredicate {
        VANILLA("Vanilla") {
            override fun test(value: Int) = value in '0'.code..'9'.code || value in 'a'.code..'z'.code ||
                value in 'A'.code..'Z'.code || value == '_'.code
        },
        CYRILLIC("Cyrillic") {
            override fun test(value: Int) = value in 0x0400..0x052F
        },
        CJK_UNIFIED_IDEOGRAPHS("CJKUnifiedIdeographs") {
            override fun test(value: Int) = value in 0x4E00..0x9FA5
        };

        fun test(string: String): Boolean = string.codePoints().allMatch(this)
    }
}
