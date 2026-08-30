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

package net.ccbluex.liquidbounce.features.simulation

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.entity.PlayerSimulationExtrapolation
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayerCache
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.CRITICAL_MODIFICATION
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.MODEL_STATE
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.world.entity.player.Player
import java.util.concurrent.ConcurrentHashMap

object PlayerSimulationCache: EventListener {
    private val otherPlayerCache = ConcurrentHashMap<Player, SimulatedPlayerCache>()
    private var localPlayerCache: SimulatedPlayerCache? = null

    init {
        PositionExtrapolation.installPlayerFactory { player ->
            PlayerSimulationExtrapolation(getSimulationForOtherPlayers(player))
        }
    }

    @Suppress("unused")
    private val gameTickHandler = handler<GameTickEvent>(priority = FIRST_PRIORITY) {
        this.otherPlayerCache.clear()
    }

    @Suppress("unused")
    private val criticalMovementHandler = handler<MovementInputEvent>(
        priority = CRITICAL_MODIFICATION
    ) { event ->
        this.localPlayerCache = null
        updatePlayerCache(event.directionalInput)
    }

    @Suppress("unused")
    private val movementHandler = handler<MovementInputEvent> { event ->
        updatePlayerCache(event.directionalInput, verify = true)
    }

    @Suppress("unused")
    private val modalMovementHandler = handler<MovementInputEvent>(
        priority = MODEL_STATE
    ) { event ->
        updatePlayerCache(event.directionalInput, verify = true)
    }

    /**
     * Updates the cache for the local player,
     * this will be called on every movement input event
     * to ensure the cache is up to date.
     *
     * @param directionalInput the input to update the cache with
     */
    private fun updatePlayerCache(directionalInput: DirectionalInput, verify: Boolean = false) {
        // Check if we even need to update the cache
        if (verify && localPlayerCache?.simulatedPlayer?.input?.directionalInput == directionalInput) {
            return
        }

        val simulatedPlayer = SimulatedPlayer.fromClientPlayer(
            SimulatedPlayer.SimulatedPlayerInput.fromClientPlayer(directionalInput)
        )

        localPlayerCache = SimulatedPlayerCache(simulatedPlayer)
    }

    fun getSimulationForOtherPlayers(player: Player): SimulatedPlayerCache {
        return otherPlayerCache.computeIfAbsent(player) {
            val simulatedPlayer = SimulatedPlayer.fromOtherPlayer(
                it,
                SimulatedPlayer.SimulatedPlayerInput.guessInput(it)
            )

            SimulatedPlayerCache(simulatedPlayer)
        }
    }

    fun getSimulationForLocalPlayer(): SimulatedPlayerCache {
        val cached = localPlayerCache

        if (cached != null) {
            return cached
        }

        val simulatedPlayer = SimulatedPlayer.fromClientPlayer(
            SimulatedPlayer.SimulatedPlayerInput.fromClientPlayer(DirectionalInput(player.input))
        )

        val simulatedPlayerCache = SimulatedPlayerCache(simulatedPlayer)

        localPlayerCache = simulatedPlayerCache

        return simulatedPlayerCache
    }
}
