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
package net.ccbluex.liquidbounce.features.module.modules.player.cheststealer

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.ccbluex.fastutil.swap
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.player.cheststealer.features.FeatureChestAura
import net.ccbluex.liquidbounce.features.module.modules.player.cheststealer.features.FeatureSilentScreen
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.CleanupPlanGenerator
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.InventoryCleanupPlan
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.ItemCategorization
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.ModuleInventoryCleaner
import net.ccbluex.liquidbounce.features.inventory.CheckScreenHandlerTypeValueGroup
import net.ccbluex.liquidbounce.features.inventory.CheckScreenTitleValueGroup
import net.ccbluex.liquidbounce.utils.inventory.ContainerItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.features.inventory.InventoryConstraints
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.findItemsInContainer
import net.ccbluex.liquidbounce.utils.inventory.findNonEmptySlotsInInventory
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import java.util.concurrent.ThreadLocalRandom

/** Automatically steals all items from a chest. */

object ModuleChestStealer : ClientModule("ChestStealer", ModuleCategories.PLAYER) {

    private val inventoryConstrains = tree(InventoryConstraints())
    private val autoClose by boolean("AutoClose", true)

    private val selectionMode = choices("SelectionMode", Distance, arrayOf(Distance, Index, Random)).apply(::tagBy)
    private val itemMoveMode by enumChoice("MoveMode", ItemMoveMode.QUICK_MOVE)
    private val quickSwaps by boolean("QuickSwaps", true)

    private val onFull by enumChoice("OnFull", OnFull.THROW)

    private enum class OnFull(override val tag: String) : Tagged {
        NONE("None"),
        THROW("Throw"),
//        PUT_BACK("PutBack"), TODO: Fix this
    }

    private val checkScreenHandlerType = tree(CheckScreenHandlerTypeValueGroup(this))
    private val checkScreenTitle = tree(CheckScreenTitleValueGroup(this))

    init {
        tree(FeatureChestAura)
        tree(FeatureSilentScreen)
    }

    @Suppress("unused")
    private val scheduleInventoryAction = handler<ScheduleInventoryActionEvent> { event ->
        // Check if we are in a chest screen
        val screen = getChestScreen() ?: return@handler

        val cleanupPlan = createCleanupPlan(screen)
        // Quick swap items in hotbar (i.e. swords), some servers hate them
        if (quickSwaps) {
            val transaction = HotbarSwapSelector.select(cleanupPlan, screen)
            if (transaction != null) {
                event.schedule(inventoryConstrains, transaction.actions, transaction.priority)
                return@handler
            }
        }

        val itemsToCollect = cleanupPlan.usefulItems.filterIsInstanceTo(ArrayList<ContainerItemSlot>())

        val stillRequiredSpace = LootCapacityPlanner.requiredSpace(cleanupPlan, itemsToCollect.size)
        selectionMode.activeMode.process(itemsToCollect)

        val targetBlacklist = ObjectOpenHashSet<ItemSlot>()

        for (slot in itemsToCollect) {
            val moveActions = ContainerTransferPlanner.plan(
                Slots.HotbarAndInventory, screen, slot, targetBlacklist, itemMoveMode == ItemMoveMode.QUICK_MOVE
            )

            if (moveActions != null) {
                event.schedule(
                    inventoryConstrains, moveActions,
                    /**
                     * we prioritize item based on how important it is
                     * for example we should prioritize armor over apples
                     */
                    ItemCategorization.Default.getItemFacets(slot).maxOf { it.category.type.allocationPriority }
                )
            } else if (stillRequiredSpace > 0) {
                // Throw useless items
                event.schedule(
                    inventoryConstrains,
                    LootCapacityPlanner.discardActions(
                        cleanupPlan, screen, targetBlacklist, onFull == OnFull.THROW
                    ) ?: break
                )
            }
        }

        // Check if stealing the chest was completed
        if (autoClose && itemsToCollect.isEmpty()) {
            event.schedule(inventoryConstrains, InventoryAction.CloseScreen(screen))
        }
    }

    /**
     * Either asks [ModuleInventoryCleaner] what to do or just takes everything.
     */
    private fun createCleanupPlan(screen: AbstractContainerScreen<*>): InventoryCleanupPlan {
        val cleanupPlan = if (!ModuleInventoryCleaner.running) {
            val usefulItems = screen.findItemsInContainer()

            InventoryCleanupPlan(ObjectOpenHashSet(usefulItems), mutableListOf(), hashMapOf())
        } else {
            val availableItems = findNonEmptySlotsInInventory() + screen.findItemsInContainer()

            CleanupPlanGenerator(ModuleInventoryCleaner.cleanupTemplateFromSettings, availableItems).generatePlan()
        }

        return cleanupPlan
    }

    private sealed class SelectionMode(name: String) : Mode(name) {
        final override val parent: ModeValueGroup<*>
            get() = selectionMode

        abstract fun process(slots: ArrayList<ContainerItemSlot>)
    }

    private object Distance : SelectionMode("Distance") {
        private val startItem by enumChoice("StartItem", StartItem.DEFAULT)
        private val randomFactor by floatRange("RandomFactor", 1.0f..1.0f, 0.25f..2f)

        override fun process(slots: ArrayList<ContainerItemSlot>) {
            val n = slots.size
            if (n <= 1) return

            val startIndex = startItem.getStartIndex(slots)
            if (startIndex != 0) {
                slots.swap(0, startIndex)
            }

            if (n <= 2) return

            for (i in 0..<n - 1) {
                var bestIdx = i + 1
                var bestDist = Float.POSITIVE_INFINITY

                val current = slots[i]

                for (j in i + 1..<n) {
                    val candidate = slots[j]
                    val distance = current.distance(candidate).toFloat()
                    val factor = if (randomFactor.isEmpty()) 1F else randomFactor.random()
                    val effectiveDistance = distance * factor

                    if (effectiveDistance < bestDist) {
                        bestDist = effectiveDistance
                        bestIdx = j
                    }
                }

                slots.swap(i + 1, bestIdx)
            }
        }

        private enum class StartItem(override val tag: String) : Tagged {
            DEFAULT("Default") {
                override fun getStartIndex(slots: List<ContainerItemSlot>): Int = 0
            },
            RANDOM("Random") {
                override fun getStartIndex(slots: List<ContainerItemSlot>): Int {
                    return ThreadLocalRandom.current().nextInt(slots.size)
                }
            },
            MAX_SLOT("MaxSlot") {
                override fun getStartIndex(slots: List<ContainerItemSlot>): Int {
                    return slots.indices.maxBy { slots[it].slotInContainer }
                }
            },
            MIN_SLOT("MinSlot") {
                override fun getStartIndex(slots: List<ContainerItemSlot>): Int {
                    return slots.indices.minBy { slots[it].slotInContainer }
                }
            };

            abstract fun getStartIndex(slots: List<ContainerItemSlot>): Int
        }
    }

    private object Index : SelectionMode("Index") {
        private val order by enumChoice("Order", Order.ASCENDING)

        override fun process(slots: ArrayList<ContainerItemSlot>) {
            slots.sortWith(order)
        }

        private enum class Order(override val tag: String) : Tagged, Comparator<ContainerItemSlot> {
            ASCENDING("Ascending") {
                override fun compare(o1: ContainerItemSlot, o2: ContainerItemSlot): Int {
                    return o1.slotInContainer.compareTo(o2.slotInContainer)
                }
            },
            DESCENDING("Descending") {
                override fun compare(o1: ContainerItemSlot, o2: ContainerItemSlot): Int {
                    return o2.slotInContainer.compareTo(o1.slotInContainer)
                }
            },
        }
    }

    private object Random : SelectionMode("Random") {
        override fun process(slots: ArrayList<ContainerItemSlot>) = slots.shuffle()
    }

    /**
     * @return the chest screen if it is open and the title matches the chest title
     */
    private fun getChestScreen(): AbstractContainerScreen<*>? {
        return mc.gui.screen()?.takeIf { it.canBeStolen() } as AbstractContainerScreen<*>?
    }

    fun Screen.canBeStolen(): Boolean {
        return running && this is AbstractContainerScreen<*> && this !is InventoryScreen &&
            checkScreenHandlerType.isValid(this) && checkScreenTitle.isValid(this)
    }

    private enum class ItemMoveMode(override val tag: String) : Tagged {
        QUICK_MOVE("QuickMove"),
        DRAG_AND_DROP("DragAndDrop"),
    }

}
