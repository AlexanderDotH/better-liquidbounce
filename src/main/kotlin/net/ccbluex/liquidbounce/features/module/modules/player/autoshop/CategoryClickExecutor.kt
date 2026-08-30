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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import kotlinx.coroutines.delay
import net.ccbluex.fastutil.forEachInt
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode.NormalPurchaseMode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode.QuickPurchaseMode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopElement
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.inventory.ContainerInput
import kotlin.time.Duration.Companion.milliseconds

internal class CategoryClickExecutor(
    private val state: ServerShopSessionState,
    private val planner: PurchaseSimulationPlanner,
    private val isShopOpen: () -> Boolean,
    private val isNormalPurchaseMode: () -> Boolean,
    private val categorySwitchDelay: () -> Int,
    private val receiptTracker: InventoryReceiptTracker = InventoryReceiptTracker(),
) : MinecraftShortcuts {

    suspend fun execute(remainingElements: List<ShopElement>) {
        val currentElement = remainingElements.first()
        switchCategory(currentElement.categorySlot)
        if (!isShopOpen()) {
            return
        }

        if (isNormalPurchaseMode()) {
            buyItem(currentElement)
        } else {
            buyCategory(remainingElements)
        }
    }

    private suspend fun switchCategory(nextCategorySlot: Int) {
        if (state.previousCategorySlot == nextCategorySlot) {
            return
        }

        val previousShopStacks = shopScreen().stacks()
        clickSlot(nextCategorySlot, NormalPurchaseMode.action.input)
        state.previousCategorySlot = nextCategorySlot
        tickUntil { !isShopOpen() || hasItemCategoryChanged(previousShopStacks) }
        tickConditional(categorySwitchDelay()) { !isShopOpen() }
    }

    private suspend fun buyItem(element: ShopElement) {
        val previousInventory = AutoShopInventoryManager.getInventoryItems()
        clickSlot(element.itemSlot, NormalPurchaseMode.action.input)
        tickUntil {
            !isShopOpen() || receiptTracker.hasReceived(
                previousInventory,
                Object2IntArrayMap(
                    arrayOf(element.item.id, element.price.id),
                    intArrayOf(element.amountPerClick, -element.price.minAmount),
                ),
            )
        }

        if (element.item.id.isArmorItem()) {
            AutoShopInventoryManager.addPendingItems(
                Object2IntMaps.singleton(element.item.id, element.amountPerClick)
            )
        }
        tickConditional(NormalPurchaseMode.extraDelay.random()) { !isShopOpen() }
    }

    private suspend fun buyCategory(remainingElements: List<ShopElement>) {
        val simulation = planner.simulate(remainingElements, onlySameCategory = true)
        val previousInventory = AutoShopInventoryManager.getInventoryItems()
        val previousShopStacks = shopScreen().stacks()

        clickPlannedSlots(simulation)
        val nextCategorySlot = simulation.slots.getInt(simulation.slots.lastIndex)
        if (nextCategorySlot != -1) {
            state.previousCategorySlot = nextCategorySlot
        }
        AutoShopInventoryManager.addPendingItems(
            receiptTracker.pendingItems(simulation.expectedItems, QuickPurchaseMode.waitForItems)
        )

        tickUntil {
            !isShopOpen() || (receiptTracker.hasReceived(previousInventory, simulation.expectedItems)
                && (nextCategorySlot == -1 || hasItemCategoryChanged(previousShopStacks)))
        }
        tickConditional(categorySwitchDelay()) { !isShopOpen() }
    }

    private suspend fun clickPlannedSlots(simulation: PurchaseSimulationResult) {
        simulation.slots.forEachInt { slot ->
            if (slot == -1) {
                return@forEachInt
            }
            delay(QuickPurchaseMode.delayMs.random().milliseconds)
            clickSlot(slot, ContainerInput.PICKUP)
        }
    }

    private fun clickSlot(slot: Int, input: ContainerInput) {
        val screen = shopScreen()
        interaction.handleContainerInput(screen.menu.containerId, slot, 0, input, player)
        state.recordClick(slot, ModuleDebug.running)
    }

    private fun hasItemCategoryChanged(previousShopStacks: List<String>): Boolean {
        val currentShopStacks = shopScreen().stacks()
        return ((currentShopStacks - previousShopStacks).union(previousShopStacks - currentShopStacks)).size > 1
    }

    private fun shopScreen() = mc.gui.screen() as ContainerScreen
}
