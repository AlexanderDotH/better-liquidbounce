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

package net.ccbluex.liquidbounce.features.module.modules.render.freecam

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.Event
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.HealthUpdateEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.newEventHook
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import java.util.function.Predicate
import kotlin.math.abs

internal class FreeCamCancelTrigger<E : Event>(
    val eventType: Class<E>,
    val predicate: Predicate<E>,
)

private inline fun <reified E : Event> cancelTrigger(predicate: Predicate<E>) =
    FreeCamCancelTrigger(E::class.java, predicate)

internal enum class FreeCamCancelOn(
    override val tag: String,
    internal val trigger: FreeCamCancelTrigger<out Event>,
) : Tagged {
    DAMAGE("Damage", cancelTrigger<HealthUpdateEvent> { it.health < it.previousHealth }),
    TELEPORT("Teleport", cancelTrigger<PacketEvent> { it.packet is ClientboundPlayerPositionPacket }),
    MOVE("Move", cancelTrigger<PlayerMoveEvent> { abs(it.movement.x) > 0 || abs(it.movement.z) > 0 }),
    LIQUID("Liquid", cancelTrigger<PlayerTickEvent> { requireNotNull(mc.player).isInLiquid }),
}

internal object FreeCamCancellation {

    fun register(owner: EventListener, selected: () -> Set<FreeCamCancelOn>, disable: () -> Unit) {
        FreeCamCancelOn.entries.forEach { reason -> register(owner, reason, selected, disable) }
    }

    private fun register(
        owner: EventListener,
        reason: FreeCamCancelOn,
        selected: () -> Set<FreeCamCancelOn>,
        disable: () -> Unit,
    ) {
        @Suppress("UNCHECKED_CAST")
        val trigger = reason.trigger as FreeCamCancelTrigger<Event>
        register(owner, reason, trigger, selected, disable)
    }

    private fun <E : Event> register(
        owner: EventListener,
        reason: FreeCamCancelOn,
        trigger: FreeCamCancelTrigger<E>,
        selected: () -> Set<FreeCamCancelOn>,
        disable: () -> Unit,
    ) {
        EventManager.registerEventHook(
            trigger.eventType,
            owner.newEventHook<E> { event ->
                if (reason in selected() && trigger.predicate.test(event)) disable()
            },
        )
    }
}
