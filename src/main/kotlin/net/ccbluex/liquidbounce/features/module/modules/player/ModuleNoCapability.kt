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

package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.world.entity.player.Abilities

/**
 * NoCapability module.
 *
 * Prevents servers from granting vanilla flight capability to the local player.
 */
object ModuleNoCapability : ClientModule("NoCapability", ModuleCategories.PLAYER) {

    private val flightController = NoCapabilityFlightController()

    override fun onEnabled() {
        flightController.activate(player.abilities)
    }

    override fun onDisabled() {
        flightController.deactivate(player.abilities)
    }

    @JvmStatic
    fun onServerAbilitiesApplied(packet: ClientboundPlayerAbilitiesPacket, abilities: Abilities) {
        if (!running) return

        flightController.onServerAbilitiesApplied(
            FlightCapabilityState(packet.canFly(), packet.isFlying),
            abilities,
        )
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { event ->
        if (event.world == null) {
            flightController.reset()
        }
    }
}
