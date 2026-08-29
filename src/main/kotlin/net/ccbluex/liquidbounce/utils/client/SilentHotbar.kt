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
package net.ccbluex.liquidbounce.utils.client

import net.ccbluex.liquidbounce.additions.realSelectedSlot
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.SelectHotbarSlotSilentlyEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.minecraft.world.entity.player.Inventory
import org.jetbrains.annotations.Range

enum class SilentHotbarSelectionPolicy(
    val shouldKeepClientSlotVisible: Boolean,
    val shouldSynchronizeCarriedItemImmediately: Boolean,
) {
    STANDARD(
        shouldKeepClientSlotVisible = false,
        shouldSynchronizeCarriedItemImmediately = false,
    ),
    SERVER_ONLY(
        shouldKeepClientSlotVisible = true,
        shouldSynchronizeCarriedItemImmediately = true,
    ),
}

/**
 * Manages things like [ModuleScaffold]'s silent mode.
 * Not thread safe, please only use this on the main-thread of minecraft
 */
object SilentHotbar : EventListener {

    private val state = SilentHotbarStateMachine {
        mc.gameMode?.ensureHasSentCarriedItem()
    }

    private val realSelectedSlot: Int
        get() = mc.player?.inventory?.realSelectedSlot ?: 0

    /**
     * Returns the slot that interactions would take place with
     */
    val serversideSlot: Int
        get() = state.serverSlot(realSelectedSlot)

    val clientsideSlot: Int
        get() = state.clientsideSlot(realSelectedSlot)

    /**
     * Whether local renderers should keep following the player's real selected slot.
     */
    val shouldKeepClientSlotVisible: Boolean
        get() = state.shouldKeepClientSlotVisible

    /**
     * Slot local renderers should display. This follows manual scrolling while [SERVER_ONLY] is active.
     */
    val visualSlot: Int
        get() = state.visualSlot(realSelectedSlot)

    /**
     * Silently selects a main-hand hotbar slot for duration of [ticksUntilReset].
     * Offhand is ignored because it is not selected through held-item changes.
     *
     * @return `true` when the slot is selected or no selection is required, `false` when the request is cancelled
     */
    @JvmOverloads
    fun selectSlotSilently(
        requester: Any?,
        slot: HotbarItemSlot,
        ticksUntilReset: Int,
        policy: SilentHotbarSelectionPolicy = SilentHotbarSelectionPolicy.STANDARD,
    ): Boolean = slot.hotbarIndex?.let { selectSlotSilently(requester, it, ticksUntilReset, policy) } ?: true

    /**
     * @see net.minecraft.world.entity.player.Inventory.isHotbarSlot
     */
    @JvmOverloads
    fun selectSlotSilently(
        requester: Any?,
        slot: @Range(from = 0, to = Inventory.SELECTION_SIZE - 1L) Int,
        ticksUntilReset: Int,
        policy: SilentHotbarSelectionPolicy = SilentHotbarSelectionPolicy.STANDARD,
    ): Boolean {
        require(Inventory.isHotbarSlot(slot)) { "Invalid hotbar slot: $slot" }

        val event = EventManager.callEvent(SelectHotbarSlotSilentlyEvent(requester, slot))
        if (event.isCancelled) {
            return false
        }

        state.select(slot, requester, ticksUntilReset, clientsideSlot, policy)
        return true
    }

    fun resetSlot(requester: Any?) {
        state.reset(requester)
    }

    fun isSlotModified() = state.isModified

    /**
     * Returns if the slot is currently getting modified by a given requester
     */
    fun isSlotModifiedBy(requester: Any?) = state.isModifiedBy(requester)

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        state.clearForWorldChange()
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent>(priority = 1001) {
        state.advanceTick()
    }
}

internal class SilentHotbarStateMachine(
    private val synchronizeCarriedItem: () -> Unit = {},
) {

    private var selection: SilentHotbarState? = null
    private var ticksSinceLastUpdate = 0

    val requester: Any?
        get() = selection?.requester

    val isModified: Boolean
        get() = selection != null

    val shouldKeepClientSlotVisible: Boolean
        get() = selection?.policy?.shouldKeepClientSlotVisible == true

    fun serverSlot(realSelectedSlot: Int): Int = selection?.enforcedHotbarSlot ?: realSelectedSlot

    fun clientsideSlot(realSelectedSlot: Int): Int = selection?.clientsideSlot ?: realSelectedSlot

    fun visualSlot(realSelectedSlot: Int): Int = if (shouldKeepClientSlotVisible) {
        realSelectedSlot
    } else {
        clientsideSlot(realSelectedSlot)
    }

    fun select(
        enforcedHotbarSlot: Int,
        requester: Any?,
        ticksUntilReset: Int,
        clientsideSlot: Int,
        policy: SilentHotbarSelectionPolicy = SilentHotbarSelectionPolicy.STANDARD,
    ) {
        selection = SilentHotbarState(enforcedHotbarSlot, requester, ticksUntilReset, clientsideSlot, policy)
        ticksSinceLastUpdate = 0
        synchronizeIfRequired(policy)
    }

    fun reset(requester: Any?) {
        val activeSelection = selection ?: return
        if (activeSelection.requester !== requester) return

        selection = null
        ticksSinceLastUpdate = 0
        synchronizeIfRequired(activeSelection.policy)
    }

    fun isModifiedBy(requester: Any?): Boolean = selection?.requester === requester

    fun advanceTick() {
        val activeSelection = selection ?: return
        if (ticksSinceLastUpdate < activeSelection.ticksUntilReset) {
            ticksSinceLastUpdate++
            return
        }

        selection = null
        ticksSinceLastUpdate = 0
        synchronizeIfRequired(activeSelection.policy)
    }

    fun clearForWorldChange() {
        selection = null
        ticksSinceLastUpdate = 0
    }

    private fun synchronizeIfRequired(policy: SilentHotbarSelectionPolicy) {
        if (policy.shouldSynchronizeCarriedItemImmediately) {
            synchronizeCarriedItem()
        }
    }
}

private data class SilentHotbarState(
    val enforcedHotbarSlot: Int,
    val requester: Any?,
    val ticksUntilReset: Int,
    val clientsideSlot: Int,
    val policy: SilentHotbarSelectionPolicy,
)
