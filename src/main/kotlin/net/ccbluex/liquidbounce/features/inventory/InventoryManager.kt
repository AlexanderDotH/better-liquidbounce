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

package net.ccbluex.liquidbounce.features.inventory

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.inventory.runtime.ActionScheduleExecutor
import net.ccbluex.liquidbounce.features.inventory.runtime.ContainerEventObserver
import net.ccbluex.liquidbounce.features.inventory.runtime.InventorySessionLedger
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.inventory.InventoryRuntimeHooks
import net.ccbluex.liquidbounce.utils.inventory.InventoryRuntimeProvider
import net.ccbluex.liquidbounce.utils.inventory.isInInventoryScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket

/**
 * Manages the inventory state and timings and schedules inventory actions
 *
 * TODO:
 *  - Progress Bar
 *  - Off-screen actions
 */
object InventoryManager : EventListener {

    private val state = InventorySessionLedger()

    init {
        InventoryRuntimeHooks.install(object : InventoryRuntimeProvider {
            override val isInventoryOpen get() = this@InventoryManager.isInventoryOpen
            override val isInventoryOpenServerSide get() = this@InventoryManager.isInventoryOpenServerSide
            override val lastClickedSlot get() = this@InventoryManager.lastClickedSlot
            override fun onClickOccurs() {
                this@InventoryManager.onClickOccurs()
            }
            override fun setInventoryOpenServerSide(open: Boolean) {
                this@InventoryManager.isInventoryOpenServerSide = open
            }
            override fun recordClickedSlot(slot: Int) {
                this@InventoryManager.lastClickedSlot = slot
            }
        })
    }

    override val running: Boolean
        get() = super.running && inGame

    val isInventoryOpen
        get() = isInInventoryScreen || isInventoryOpenServerSide

    val isHandledScreenOpen
        get() = mc.gui.screen() is AbstractContainerScreen<*> || isInventoryOpenServerSide

    var isInventoryOpenServerSide: Boolean
        get() = state.isServerSideOpen
        set(value) = state.setServerSideOpen(value)

    var lastClickedSlot: Int
        get() = state.lastClickedSlot
        internal set(value) = state.recordClickedSlot(value)

    @Suppress("unused")
    private val scheduler = ActionScheduleExecutor(this, state)

    @Suppress("unused")
    private val eventObserver = ContainerEventObserver(this, state)

    /**
     * Called when a click occurs. Can be tracked by listening for [ServerboundContainerClickPacket]
     *
     * @see net.ccbluex.liquidbounce.injection.mixins.viaversion.MixinPacketWrapper
     */
    @JvmStatic
    fun onClickOccurs() {
        state.markClickObserved()
    }

    /**
     * Called when the inventory was opened. Can be tracked by listening for [ClientboundOpenScreenPacket]
     */
    @JvmStatic
    fun onInventoryOpened() {
        state.markInventoryOpened()
    }

}
