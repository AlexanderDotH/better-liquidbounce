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

package net.ccbluex.liquidbounce.features.module.modules.player.nocapability

import net.minecraft.world.entity.player.Abilities

internal data class FlightCapabilityState(
    val mayFly: Boolean,
    val flying: Boolean,
) {
    fun applyTo(abilities: Abilities) {
        abilities.mayfly = mayFly
        abilities.flying = flying
    }
}

internal class NoCapabilityFlightController {

    private var restoreState: FlightCapabilityState? = null

    fun activate(abilities: Abilities) {
        restoreState = abilities.flightCapabilityState()
        suppressFlight(abilities)
    }

    fun onServerAbilitiesApplied(serverState: FlightCapabilityState, abilities: Abilities) {
        restoreState = serverState
        suppressFlight(abilities)
    }

    fun deactivate(abilities: Abilities) {
        restoreState?.applyTo(abilities)
        restoreState = null
    }

    fun reset() {
        restoreState = null
    }

    private fun suppressFlight(abilities: Abilities) {
        abilities.flying = false
        abilities.mayfly = false
    }

    private fun Abilities.flightCapabilityState() = FlightCapabilityState(mayfly, flying)
}
