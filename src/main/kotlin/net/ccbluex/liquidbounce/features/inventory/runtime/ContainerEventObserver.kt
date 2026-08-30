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
package net.ccbluex.liquidbounce.features.inventory.runtime

import net.ccbluex.liquidbounce.common.debug.DebugParameterSink
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.isOlderThanOrEqual1_11_1
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.inventory.typeOrNull
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket

internal class ContainerEventObserver(
    private val owner: EventListener,
    private val state: InventorySessionLedger,
) {

    @Suppress("unused")
    private val packetHandler = owner.handler<PacketEvent>(
        priority = EventPriorityConvention.READ_FINAL_STATE,
    ) { event -> observePacket(event) }

    @Suppress("unused")
    private val screenHandler = owner.handler<ScreenEvent>(
        priority = EventPriorityConvention.READ_FINAL_STATE,
    ) { event -> observeScreen(event) }

    @Suppress("unused")
    private val worldChangeHandler = owner.handler<WorldChangeEvent> {
        state.setServerSideOpen(false)
    }

    private fun observePacket(event: PacketEvent) {
        if (event.isCancelled) {
            return
        }

        val packet = event.packet
        if (packet is ServerboundContainerClickPacket) {
            state.markClickObserved()
            if (packet.containerId == 0) {
                state.setServerSideOpen(true)
            }
        }

        if (!tracksInventoryTransition(packet)) {
            return
        }
        if (shouldCancelRedundantClose(packet)) {
            event.cancelEvent()
            return
        }
        state.setServerSideOpen(false)
    }

    private fun tracksInventoryTransition(packet: Packet<*>): Boolean =
        packet is ServerboundContainerClosePacket ||
            packet is ClientboundContainerClosePacket ||
            packet is ClientboundOpenScreenPacket

    private fun shouldCancelRedundantClose(packet: Packet<*>): Boolean =
        !state.isServerSideOpen &&
            packet is ServerboundContainerClosePacket &&
            packet.containerId == 0

    private fun observeScreen(event: ScreenEvent) {
        val screen = event.screen
        debugParameter("Screen") { screen }
        if (event.isCancelled || screen !is AbstractContainerScreen<*>) {
            return
        }

        publishScreenDetails(screen)
        if (screen is InventoryScreen && isOlderThanOrEqual1_11_1) {
            state.setServerSideOpen(true)
        }
        state.markInventoryOpened()
    }

    private fun publishScreenDetails(screen: AbstractContainerScreen<*>) {
        debugParameter("Screen Handler Type") {
            screen.menu.typeOrNull?.let { BuiltInRegistries.MENU.getKey(it) }
        }
        debugParameter("Screen Slot count") {
            val slots = screen.menu.slots
            "${slots.size} (${slots.count { it.container !== player.inventory }})"
        }
    }

    private fun debugParameter(name: String, value: () -> Any?) {
        DebugParameterSink.publish(owner, name, value)
    }
}
