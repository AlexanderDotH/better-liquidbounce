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

package net.ccbluex.liquidbounce.features.module.modules.combat.remotekill

import net.ccbluex.liquidbounce.utils.client.clientLogger
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Player
import java.util.concurrent.CopyOnWriteArraySet

/** Receives both sides of the vanilla correction application without cancelling that correction. */
internal interface RemoteKillSetbackListener {
    fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player)
    fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player)
}

/** Shared fan-out behind the established SpearKill Java mixin hook. */
internal object RemoteKillSetbackRegistry {

    private val listeners = CopyOnWriteArraySet<RemoteKillSetbackListener>()
    private val logger = clientLogger("RemoteKill/Setback")

    fun register(listener: RemoteKillSetbackListener) {
        listeners += listener
    }

    fun unregister(listener: RemoteKillSetbackListener) {
        listeners -= listener
    }

    fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
        notifyListeners("before") { it.beforeCorrection(packet, player) }
    }

    fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
        notifyListeners("after") { it.afterCorrection(packet, player) }
    }

    private inline fun notifyListeners(
        phase: String,
        action: (RemoteKillSetbackListener) -> Unit,
    ) {
        listeners.forEach { listener ->
            try {
                action(listener)
            } catch (exception: Exception) {
                logger.error("Remote-kill setback listener failed $phase correction", exception)
            }
        }
    }
}
