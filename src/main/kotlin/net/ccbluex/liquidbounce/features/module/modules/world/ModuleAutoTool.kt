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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.BlockBreakingProgressEvent
import net.ccbluex.liquidbounce.event.events.CancelBlockBreakingEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.autotool.AutoToolNearBedRequirement
import net.ccbluex.liquidbounce.features.module.modules.world.autotool.AutoToolSilkTouchHandler
import net.ccbluex.liquidbounce.features.module.modules.world.autotool.selectAutoToolInventorySwapTarget
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.SilentHotbarSelectionPolicy
import net.ccbluex.liquidbounce.utils.collection.Filter
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.features.combat.runtime.CombatManager
import net.ccbluex.liquidbounce.features.inventory.AnchoredHotbarSwapController
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.features.inventory.InventoryConstraints
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.findBestToolToMineBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

/**
 * AutoTool module
 *
 * Automatically chooses the best tool in your inventory to mine a block.
 */
object ModuleAutoTool : ClientModule("AutoTool", ModuleCategories.WORLD) {
    val toolSelector =
        choices(
            "ToolSelector",
            DynamicSelectMode,
            arrayOf(DynamicSelectMode, StaticSelectMode)
        )

    private val switchMode = choices(
        "SwitchMode",
        NormalSwitchMode,
        arrayOf(NormalSwitchMode, PacketSwitchMode),
    )

    private sealed class AutoToolSwitchMode(
        name: String,
        val selectionPolicy: SilentHotbarSelectionPolicy,
    ) : Mode(name) {
        final override val parent: ModeValueGroup<*>
            get() = switchMode

        fun select(slot: HotbarItemSlot) {
            SilentHotbar.selectSlotSilently(ModuleAutoTool, slot, swapPreviousDelay, selectionPolicy)
        }

        open fun resetActiveSelection() = Unit
    }

    private object NormalSwitchMode : AutoToolSwitchMode(
        "Normal",
        SilentHotbarSelectionPolicy.STANDARD,
    )

    private object PacketSwitchMode : AutoToolSwitchMode(
        "Packet",
        SilentHotbarSelectionPolicy.SERVER_ONLY,
    ) {
        override fun resetActiveSelection() {
            SilentHotbar.resetSlot(ModuleAutoTool)
        }

        override fun disable() {
            resetActiveSelection()
        }
    }

    sealed class ToolSelectorMode(name: String) : Mode(name) {
        final override val parent: ModeValueGroup<*>
            get() = toolSelector

        fun getTool(blockState: BlockState): HotbarItemSlot? =
            if (filter(blockState.block, blocks)) {
                getToolSlot(blockState)
            } else {
                null
            }

        protected open fun getToolSlot(blockState: BlockState): HotbarItemSlot? = null
    }

    private object DynamicSelectMode : ToolSelectorMode("Dynamic") {
        private val ignoreDurability by boolean("IgnoreDurability", false)

        object ConsiderInventory : ToggleableValueGroup(this, "ConsiderInventory", enabled = false) {
            private val inventoryConstraints = tree(InventoryConstraints())
            private val swapController = AnchoredHotbarSwapController(
                owner = this,
                inventoryConstraints = inventoryConstraints,
                swapDelayProvider = { swapPreviousDelay },
                anchorHotbarSlotResolver = {
                    val emptySlots = Slots.Hotbar.asSequence()
                        .filter { it.itemStack.isEmpty }
                        .mapNotNull { it.hotbarIndex }
                        .toList()
                    val targetSlot = selectAutoToolInventorySwapTarget(
                        selectionPolicy = switchMode.activeMode.selectionPolicy,
                        visibleSlot = SilentHotbar.visualSlot,
                        serverSlot = SilentHotbar.serversideSlot,
                        emptySlots = emptySlots,
                    )

                    Slots.Hotbar[targetSlot]
                },
            )

            override fun onDisabled() {
                swapController.reset()
                super.onDisabled()
            }

            fun onToolInHotbar() {
                swapController.clearRequestedSwap()
                swapController.touchActiveSwitching()
            }

            fun onToolInInventory(slot: ItemSlot) {
                swapController.requestSwapFromInventory(slot)
            }

            fun onNoTool() {
                swapController.clearRequestedSwap()
            }
        }

        init {
            tree(ConsiderInventory)
        }

        override fun getToolSlot(blockState: BlockState): HotbarItemSlot? {
            if (!ConsiderInventory.running) {
                return Slots.Hotbar.findBestToolToMineBlock(blockState, ignoreDurability, silkTouchHandler)
            } else {
                val slot = Slots.HotbarAndInventory
                    .findBestToolToMineBlock(blockState, ignoreDurability, silkTouchHandler)

                return when (slot) {
                    is HotbarItemSlot -> {
                        // We found the best tool in hotbar, don't need inventory action
                        ConsiderInventory.onToolInHotbar()
                        slot
                    }
                    is ItemSlot -> {
                        // Request inventory action and keep restore delay alive while actively switching.
                        ConsiderInventory.onToolInInventory(slot)
                        null
                    }
                    null -> {
                        ConsiderInventory.onNoTool()
                        null
                    }
                }
            }
        }
    }

    private object StaticSelectMode : ToolSelectorMode("Static") {
        private val slot by int("Slot", 0, 0..8)

        override fun getToolSlot(blockState: BlockState) = Slots.Hotbar[slot]
    }

    private val filter by enumChoice("Filter", Filter.BLACKLIST)
    private val blocks by blocks("Blocks", blockSortedSetOf())

    private val silkTouchHandler = AutoToolSilkTouchHandler(this)

    init {
        tree(silkTouchHandler)
    }

    private val swapPreviousDelay by int("SwapPreviousDelay", 20, 1..100, "ticks")

    private val requireSneaking by boolean("RequireSneaking", false)
    private val notDuringCombat by boolean("NotDuringCombat", false)

    private val nearBedRequirement = AutoToolNearBedRequirement(this)

    init {
        tree(nearBedRequirement)
    }

    val isInventoryConsidered: Boolean
        get() = DynamicSelectMode.ConsiderInventory.running

    @Suppress("unused")
    private val handleBlockBreakingProgress = handler<BlockBreakingProgressEvent> { event ->
        switchToBreakBlock(event.pos)
    }

    @Suppress("unused")
    private val handleCancelBlockBreaking = handler<CancelBlockBreakingEvent> {
        switchMode.activeMode.resetActiveSelection()

        if (isInventoryConsidered) {
            DynamicSelectMode.ConsiderInventory.onNoTool()
        }
    }

    fun switchToBreakBlock(pos: BlockPos) {
        val cancelDueToCombat = notDuringCombat && CombatManager.isInCombat
        val cancelDueToNotSneaking = requireSneaking && !player.isShiftKeyDown
        if (cancelDueToCombat
            || cancelDueToNotSneaking
            || nearBedRequirement.enabled && !nearBedRequirement.matches()
        ) {
            switchMode.activeMode.resetActiveSelection()

            if (isInventoryConsidered) {
                DynamicSelectMode.ConsiderInventory.onNoTool()
            }
            return
        }

        val blockState = pos.stateOrEmpty
        val slot = toolSelector.activeMode.getTool(blockState) ?: return
        switchMode.activeMode.select(slot)
    }

    override fun onDisabled() {
        SilentHotbar.resetSlot(this)
    }


}
