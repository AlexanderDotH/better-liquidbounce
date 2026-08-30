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
package net.ccbluex.liquidbounce.features.module.modules.player.offhand

import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.ModuleCrystalAura
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleEagle
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.getPotionEffects
import net.ccbluex.liquidbounce.utils.item.isSword
import net.minecraft.core.component.DataComponents
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.function.Predicate

private val INVENTORY_MAIN_PRIORITY = Slots.Inventory + Slots.Hotbar
private val INVENTORY_HOTBAR_PRIORITY = Slots.Hotbar + Slots.Inventory

internal data class PreviousEquipment(val item: Item, val slot: ItemSlot)

internal enum class EquipmentMode(
    val modeName: String,
    private val item: Predicate<ItemStack>? = null,
    private val fallBackItem: Predicate<ItemStack>? = null,
) : MinecraftShortcuts {
    TOTEM("Totem", Predicate { it.has(DataComponents.DEATH_PROTECTION) }) {
        override fun shouldEquip() = Totem.shouldEquip()

        override fun getDelay() = Totem.switchDelay

        override fun getPrioritizedInventoryPart() = 1

        override fun getSlot(): ItemSlot? {
            val slot = super.getSlot()
            return if (slot == null && ModuleOffhand.Crystal.enabled && ModuleOffhand.Crystal.whenNoTotems) {
                CRYSTAL.getSlot()
            } else {
                slot
            }
        }

        override fun canCycleTo() = Totem.enabled
    },
    STRENGTH("Strength", Predicate { stack ->
        stack.`is`(Items.POTION) && stack.getPotionEffects().any { it.effect == MobEffects.STRENGTH }
    }) {
        override fun shouldEquip(): Boolean {
            val strength = ModuleOffhand.Strength
            val killAura = strength.onlyWhileKa && !ModuleKillAura.running
            if (!strength.enabled || killAura || player.hasEffect(MobEffects.STRENGTH)) {
                return false
            }

            return player.mainHandItem.isSword || !strength.onlyWhileHoldingSword
        }
    },
    GAPPLE("Gapple", Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE) {
        override fun shouldEquip(): Boolean {
            val gapple = ModuleOffhand.Gapple
            val whileHoldingSword = ModuleOffhand.Gapple.WhileHoldingSword
            if (!gapple.enabled) {
                return false
            }

            if (player.mainHandItem.isSword && whileHoldingSword.enabled) {
                return !whileHoldingSword.onlyWhileKa || ModuleKillAura.running
            }

            return false
        }

        override fun canCycleTo() = ModuleOffhand.Gapple.enabled
    },
    CRYSTAL("Crystal", Items.END_CRYSTAL) {
        override fun canCycleTo(): Boolean {
            val crystal = ModuleOffhand.Crystal
            return crystal.enabled && (!crystal.onlyWhileCa || ModuleCrystalAura.running)
        }
    },
    BLOCK("Block", ScaffoldBlockItemSelection::isValidBlock) {
        override fun shouldEquip(): Boolean {
            val block = ModuleOffhand.Block
            return block.enabled &&
                ((block.whileEagle && ModuleEagle.enabled) || (block.whileScaffold && ModuleScaffold.enabled))
        }

        override fun canCycleTo() = ModuleOffhand.Block.enabled
    },
    BACK("Back") {
        override fun getSlot(): ItemSlot? = ModuleOffhand.previousEquipment?.let {
            if (it.item == it.slot.itemStack.item) it.slot else null
        }
    },
    NONE("None");

    constructor(
        modeName: String,
        item: Item,
        fallBackItem: Item? = null,
    ) : this(modeName, { it.`is`(item) }, fallBackItem?.let { fallback -> { it.`is`(fallback) } })

    private var modeBeforeDirectSwitch: EquipmentMode? = null

    open fun shouldEquip() = false

    open fun getDelay() = ModuleOffhand.switchDelay

    open fun canCycleTo() = false

    /**
     * 0 = Main inventory, 1 = Hotbar.
     */
    open fun getPrioritizedInventoryPart() = 0

    fun onBindPress() {
        val previousMode = modeBeforeDirectSwitch
        if (ModuleOffhand.activeMode == this && previousMode != null && previousMode.canCycleTo()) {
            ModuleOffhand.staticMode = previousMode
            modeBeforeDirectSwitch = null
        } else if (canCycleTo()) {
            modeBeforeDirectSwitch = ModuleOffhand.staticMode
            ModuleOffhand.staticMode = this
        } else {
            modeBeforeDirectSwitch = null
        }
    }

    open fun getSlot(): ItemSlot? {
        val primaryItem = item ?: return null
        if (primaryItem.test(player.offhandItem)) {
            return HotbarItemSlot.OFFHAND
        }

        val slots = if (getPrioritizedInventoryPart() == 0) INVENTORY_MAIN_PRIORITY else INVENTORY_HOTBAR_PRIORITY
        var itemSlot = slots.findSlot(primaryItem)
        if (itemSlot == null && fallBackItem != null) {
            if (fallBackItem.test(player.offhandItem)) {
                return HotbarItemSlot.OFFHAND
            }
            itemSlot = slots.findSlot(fallBackItem)
        }

        return itemSlot
    }
}
