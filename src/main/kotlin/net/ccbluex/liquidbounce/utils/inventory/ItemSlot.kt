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
@file:JvmName("ItemSlotKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.inventory

import net.ccbluex.fastutil.asObjectList
import net.ccbluex.liquidbounce.utils.client.isOlderThanOrEqual1_8
import net.ccbluex.liquidbounce.utils.client.isOlderThanOrEqual1_15_2
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.item.ItemStackHolder
import net.ccbluex.liquidbounce.utils.item.PreferStackSize
import net.ccbluex.liquidbounce.utils.item.asHolderComparator
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import kotlin.math.abs

/**
 * Represents an inventory slot (e.g. Hotbar Slot 0, OffHand, Chestslot 5, etc.)
 */
sealed interface ItemSlot : ItemStackHolder {
    override val itemStack: ItemStack
    val slotType: Type

    /**
     * Used for example for slot click packets
     */
    fun getIdForServer(screen: AbstractContainerScreen<*>?): Int?

    fun getIdForServerWithCurrentScreen() = getIdForServer(mc.gui.screen() as? AbstractContainerScreen<*>)

    override fun hashCode(): Int

    override fun equals(other: Any?): Boolean

    companion object {

        /**
         * Distance order:
         * current hand -> offhand -> other hotbar slots -> other slots
         */
        @JvmField
        val PREFER_NEARBY: Comparator<ItemSlot> = Comparator { left, right ->
            val leftIsHotbar = left is HotbarItemSlot
            val rightIsHotbar = right is HotbarItemSlot
            when {
                leftIsHotbar && rightIsHotbar -> HotbarItemSlot.PREFER_NEARBY.compare(left, right)
                leftIsHotbar -> -1
                rightIsHotbar -> 1
                else -> 0
            }
        }

        @JvmField
        val PREFER_FEWER_ITEM: Comparator<in ItemSlot> = PreferStackSize.PREFER_FEWER.asHolderComparator()

        @JvmField
        val PREFER_MORE_ITEM: Comparator<in ItemSlot> = PreferStackSize.PREFER_MORE.asHolderComparator()
    }

    enum class Type {
        HOTBAR,
        OFFHAND,
        ARMOR,
        INVENTORY,

        /**
         * e.g. chests
         */
        CONTAINER,
    }
}

/**
 * @param id the id this slot is identified by. Two virtual slots that have the same id are considered equal.
 */
class VirtualItemSlot(
    override val itemStack: ItemStack,
    override val slotType: ItemSlot.Type,
    val id: Int
) : ItemSlot {
    override fun getIdForServer(screen: AbstractContainerScreen<*>?): Nothing =
        throw UnsupportedOperationException("VirtualItemSlot does not have a server id")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VirtualItemSlot

        return id == other.id
    }

    override fun hashCode(): Int {
        return id
    }

    override fun toString(): String = "ItemSlot/Virtual(id=$id, itemStack=$itemStack, slotType=$slotType)"

}

class ContainerItemSlot(val slotInContainer: Int) : ItemSlot {

    override val itemStack: ItemStack
        get() = (mc.gui.screen() as AbstractContainerScreen<*>).menu.slots[this.slotInContainer].item

    override val slotType: ItemSlot.Type
        get() = ItemSlot.Type.CONTAINER

    override fun getIdForServer(screen: AbstractContainerScreen<*>?): Int = this.slotInContainer

    fun distance(itemSlot: ContainerItemSlot): Int {
        // TODO: only for 9xN types
        val slotId = this.slotInContainer
        val otherId = itemSlot.slotInContainer

        val rowA = slotId / 9
        val colA = slotId % 9

        val rowB = otherId / 9
        val colB = otherId % 9

        return (colA - colB) * (colA - colB) + (rowA - rowB) * (rowA - rowB)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContainerItemSlot

        return slotInContainer == other.slotInContainer
    }

    override fun hashCode(): Int {
        return this.javaClass.hashCode() * 31 + this.slotInContainer
    }

    override fun toString(): String = "ItemSlot/Container(slotInContainer=$slotInContainer)"
}

internal fun AbstractContainerScreen<*>.itemCount() = this.menu.slots.size
