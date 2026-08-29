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
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.InputHandleEvent
import net.ccbluex.liquidbounce.event.events.KeybindChangeEvent
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.misc.safeactions.SafeActionConfirmationDecision
import net.ccbluex.liquidbounce.features.module.modules.misc.safeactions.SafeActionConfirmationGate
import net.ccbluex.liquidbounce.features.module.modules.misc.safeactions.SafeDropAction
import net.ccbluex.liquidbounce.features.module.modules.misc.safeactions.SafeDropContext
import net.ccbluex.liquidbounce.features.module.modules.misc.safeactions.SafeDropPressTracker
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.client.notification
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * Requires a second matching physical input before selected manual actions are executed.
 */
object ModuleSafeActions : ClientModule("SafeActions", ModuleCategories.MISC) {

    init {
        tree(Drop)
    }

    override fun onEnabled() = Drop.reset()

    override fun onDisabled() = Drop.reset()

    @JvmStatic
    fun shouldAllowWorldDrop(player: LocalPlayer, dropAll: Boolean): Boolean =
        Drop.shouldAllowWorldDrop(player, dropAll)

    @JvmStatic
    fun shouldAllowContainerDrop(
        screen: Any,
        menu: AbstractContainerMenu,
        slot: Slot,
        dropAll: Boolean,
    ): Boolean = Drop.shouldAllowContainerDrop(screen, menu, slot, dropAll)

    @JvmStatic
    fun observeContainerContext(screen: Any, menu: AbstractContainerMenu, slot: Slot?) {
        Drop.observeContainerContext(screen, menu, slot)
    }

    private fun dropKeyName(): String =
        mc.options.keyDrop.translatedKeyMessage?.string ?: mc.options.keyDrop.saveString()

    object Drop : ToggleableValueGroup(ModuleSafeActions, "Drop", true) {

        private val gate = SafeActionConfirmationGate<SafeDropAction>()
        private val pressTracker = SafeDropPressTracker()
        private var observedContext: SafeDropContext? = null

        override fun onEnabled() = reset()

        override fun onDisabled() = reset()

        @Suppress("unused")
        private val keyboardHandler = handler<KeyboardKeyEvent> { event ->
            if (event.key == mc.options.keyDrop.key) {
                if (event.screen == null) {
                    pressTracker.record(event.action)
                } else {
                    pressTracker.recordImmediate(event.action)
                }
            }
        }

        @Suppress("unused")
        private val mouseHandler = handler<MouseButtonEvent> { event ->
            if (event.key == mc.options.keyDrop.key) {
                if (event.screen == null) {
                    pressTracker.record(event.action)
                } else {
                    pressTracker.recordImmediate(event.action)
                }
            }
        }

        @Suppress("unused")
        private val inputHandleHandler = handler<InputHandleEvent> {
            val currentPlayer = mc.player
            if (mc.gui.screen() == null && currentPlayer != null) {
                observeContext(worldAction(currentPlayer, dropAll = false).context)
            }
            pressTracker.clear()
        }

        @Suppress("unused")
        private val screenHandler = handler<ScreenEvent> { reset() }

        @Suppress("unused")
        private val keybindHandler = handler<KeybindChangeEvent> { reset() }

        @Suppress("unused")
        private val worldHandler = handler<WorldChangeEvent> { reset() }

        fun shouldAllowWorldDrop(player: LocalPlayer, dropAll: Boolean): Boolean {
            val stack = player.mainHandItem
            if (shouldBypass(stack, player.isSpectator)) return true

            return decide(worldAction(player, dropAll), stack)
        }

        fun shouldAllowContainerDrop(
            screen: Any,
            menu: AbstractContainerMenu,
            slot: Slot,
            dropAll: Boolean,
        ): Boolean {
            val stack = slot.item
            if (shouldBypass(stack, mc.player?.isSpectator == true)) return true

            val action = SafeDropAction.container(screen, menu, slot.index, stack, dropAll)
            return decide(action, stack)
        }

        fun observeContainerContext(screen: Any, menu: AbstractContainerMenu, slot: Slot?) {
            if (!running || slot == null || slot.item.isEmpty) {
                reset()
                return
            }

            observeContext(
                SafeDropAction.container(screen, menu, slot.index, slot.item, dropAll = false).context,
            )
        }

        fun reset() {
            gate.reset()
            pressTracker.clear()
            observedContext = null
        }

        private fun shouldBypass(stack: ItemStack, spectator: Boolean): Boolean {
            if (running && !stack.isEmpty && !spectator) return false

            reset()
            return true
        }

        private fun decide(action: SafeDropAction, stack: ItemStack): Boolean {
            observeContext(action.context)
            return when (gate.request(action, pressTracker.consumeFreshPress())) {
                SafeActionConfirmationDecision.ALLOW -> true
                SafeActionConfirmationDecision.BLOCK -> false
                SafeActionConfirmationDecision.BLOCK_AND_NOTIFY -> {
                    notifyConfirmation(action, stack)
                    false
                }
            }
        }

        private fun observeContext(context: SafeDropContext) {
            if (observedContext != null && observedContext != context) {
                gate.reset()
            }
            gate.invalidateWhen { it.context != context }
            observedContext = context
        }

        private fun worldAction(player: LocalPlayer, dropAll: Boolean) = SafeDropAction.world(
            world = player.level(),
            slot = player.inventory.selectedSlot,
            stack = player.mainHandItem,
            dropAll = dropAll,
        )

        private fun notifyConfirmation(action: SafeDropAction, stack: ItemStack) {
            val messageKey = if (action.dropAll) STACK_CONFIRMATION else SINGLE_CONFIRMATION
            val count = if (action.dropAll) stack.count else 1
            notification(
                ModuleSafeActions.name,
                translation(messageKey, dropKeyName(), count, stack.hoverName.string),
                NotificationEvent.Severity.INFO,
            )
        }

        private const val TRANSLATION_PREFIX = "liquidbounce.module.safeActions.drop"
        private const val SINGLE_CONFIRMATION = "$TRANSLATION_PREFIX.singleConfirmation"
        private const val STACK_CONFIRMATION = "$TRANSLATION_PREFIX.stackConfirmation"
    }
}
