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

@file:JvmName("InventoryValueGroupsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.inventory

import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet
import net.ccbluex.fastutil.enumSetAllOf
import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.fastutil.objectRBTreeSetOf
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.collection.Filter
import net.ccbluex.liquidbounce.utils.collection.asComparator
import net.ccbluex.liquidbounce.utils.collection.itemSortedSetOf
import net.ccbluex.liquidbounce.utils.kotlin.matchesAll
import net.ccbluex.liquidbounce.utils.math.isLikelyZero
import net.ccbluex.liquidbounce.utils.text.StringMatchMode
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.function.Predicate

class CheckScreenTitleValueGroup(
    parent: EventListener,
) : ToggleableValueGroup(parent, "CheckScreenTitle", enabled = true, aliases = listOf("CheckTitle")) {
    private val titles by multiEnumChoice(
        "Titles",
        enumSetOf(
            ContainerTitle.CHEST, ContainerTitle.LARGE_CHEST,
            ContainerTitle.SHULKER_BOX, ContainerTitle.BARREL,
            ContainerTitle.CHEST_MINECART, ContainerTitle.CHEST_BOAT,
        ),
    )
    private val customTitles by textList("Custom", ObjectRBTreeSet())
    private val filter by enumChoice("Filter", Filter.WHITELIST)

    fun isValid(screen: Screen): Boolean {
        if (!running) return true

        val titleString = screen.title.string
        val matches = titles.any { it.matches(titleString) } || titleString in customTitles

        return when (filter) {
            Filter.WHITELIST -> matches
            Filter.BLACKLIST -> !matches
        }
    }

    @Suppress("unused")
    private enum class ContainerTitle(
        override val tag: String,
        private vararg val translatableKeys: String,
    ) : Tagged {
        BARREL("Barrel", "container.barrel"),
        BEACON("Beacon", "container.beacon"),
        BLAST_FURNACE("BlastFurnace", "container.blast_furnace"),
        BREWING_STAND("BrewingStand", "container.brewing"),
        CHEST("Chest", "container.chest"),
        LARGE_CHEST("LargeChest", "container.chestDouble"),
        DISPENSER("Dispenser", "container.dispenser"),
        DROPPER("Dropper", "container.dropper"),
        ENDER_CHEST("EnderChest", "container.enderchest"),
        FURNACE("Furnace", "container.furnace"),
        HOPPER("Hopper", "container.hopper"),
        SHULKER_BOX("ShulkerBox", "container.shulkerBox"),
        SMOKER("Smoker", "container.smoker"),
        CHEST_MINECART("ChestMinecart", "entity.minecraft.chest_minecart"),
        /**
         * Chest boats use their entity display name as the container title.
         *
         * @see net.minecraft.world.entity.Entity.getDisplayName
         * @see net.minecraft.world.entity.vehicle.boat.AbstractChestBoat.createMenu
         */
        CHEST_BOAT(
            "ChestBoat",
            "entity.minecraft.chest_boat",
            "entity.minecraft.acacia_chest_boat",
            "entity.minecraft.bamboo_chest_raft",
            "entity.minecraft.birch_chest_boat",
            "entity.minecraft.cherry_chest_boat",
            "entity.minecraft.dark_oak_chest_boat",
            "entity.minecraft.jungle_chest_boat",
            "entity.minecraft.mangrove_chest_boat",
            "entity.minecraft.oak_chest_boat",
            "entity.minecraft.pale_oak_chest_boat",
            "entity.minecraft.poplar_chest_boat",
            "entity.minecraft.spruce_chest_boat",
        ),
        HOPPER_MINECART("HopperMinecart", "entity.minecraft.hopper_minecart"),
        ;

        fun matches(title: String): Boolean = translatableKeys.any {
            Component.translatable(it).string == title
        }
    }
}

sealed class SingleItemStackPickMode(
    final override val parent: ModeValueGroup<*>,
    name: String,
) : Mode(name), Predicate<ItemStack> {

    abstract override fun test(itemStack: ItemStack): Boolean

    class ByName(parent: ModeValueGroup<*>) : SingleItemStackPickMode(parent, "Name") {
        private val names by textList("Names", objectRBTreeSetOf("Paper"))
        private val mode by enumChoice("Mode", StringMatchMode.EQUALS)

        override fun test(itemStack: ItemStack): Boolean {
            val string = itemStack.hoverName.string
            return names.any { mode.test(string, it) }
        }
    }

    class ByItem(parent: ModeValueGroup<*>) : SingleItemStackPickMode(parent, "Item") {
        private val items by items("Items", itemSortedSetOf(Items.PAPER))

        override fun test(itemStack: ItemStack): Boolean = itemStack.item in items
    }

}
