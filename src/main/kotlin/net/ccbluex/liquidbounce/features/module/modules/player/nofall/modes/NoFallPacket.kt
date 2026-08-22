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
package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleMaceKill
import net.ccbluex.liquidbounce.features.module.modules.combat.RemoteKillMovementOwnership
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.ccbluex.liquidbounce.utils.network.MovePacketType

internal object NoFallPacket : NoFallMode("Packet") {
    private val packetType by enumChoice("PacketType", MovePacketType.FULL)
    private val filter = modes("Filter", FallDistance, arrayOf(FallDistance, Always))
    private val deliveryTracker = GroundPacketDeliveryTracker()

    override fun disable() {
        deliveryTracker.clear()
        super.disable()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        deliveryTracker.clear()
    }

    val repeatable = tickHandler {
        val remoteMovementOwned = RemoteKillMovementOwnership.active
        val exclusiveRoutePacketsActive = when (RemoteKillMovementOwnership.currentOwner) {
            "MaceKill" -> ModuleMaceKill.suppressesNoFallPackets
            else -> remoteMovementOwned
        }
        if (!filter.activeMode.isActive ||
            !shouldSendNoFallPacketDuringRemoteKill(remoteMovementOwned, exclusiveRoutePacketsActive)
        ) {
            return@tickHandler
        }

        val packet = packetType.generatePacket()
        deliveryTracker.protect(packet)
        network.send(packet)

        // PacketEvent dispatch is synchronous. If no event was emitted, retain no stale attempt and retry next tick.
        deliveryTracker.discard(packet)
    }

    @Suppress("unused")
    private val safetyPacketHandler = handler<PacketEvent>(priority = SAFETY_FEATURE) { event ->
        val packet = event.outgoingMovementPacket ?: return@handler
        deliveryTracker.reassertGround(packet)
    }

    @Suppress("unused")
    private val finalPacketHandler = handler<PacketEvent>(priority = READ_FINAL_STATE) { event ->
        val packet = event.outgoingMovementPacket ?: return@handler
        if (!deliveryTracker.confirmFinalState(packet, event.isCancelled)) {
            return@handler
        }

        if (filter.activeMode is FallDistance && FallDistance.resetFallDistance) {
            player.resetFallDistance()
        }
    }

    private abstract class Filter(name: String) : Mode(name) {
        override val parent: ModeValueGroup<*>
            get() = filter

        abstract val isActive: Boolean
    }

    private object FallDistance : Filter("FallDistance") {
        override val isActive: Boolean
            get() = player.fallDistance - player.deltaMovement.y > distance.activeMode.value && player.tickCount > 20

        private val distance = modes("Distance", Smart, arrayOf(Smart, Constant))
        val resetFallDistance by boolean("ResetFallDistance", true)

        private abstract class DistanceMode(name: String) : Mode(name) {
            override val parent: ModeValueGroup<*>
                get() = distance

            abstract val value: Float
        }

        private object Smart : DistanceMode("Smart") {
            override val value: Float
                get() = playerSafeFallDistance.toFloat()
        }

        private object Constant : DistanceMode("Constant") {
            override val value by float("Value", 2f, 0f..5f)
        }
    }

    private object Always : Filter("Always") {
        override val isActive: Boolean
            get() = true
    }
}

/** A remote-kill route owns its virtual movement stream, so NoFall must not inject a second one. */
internal fun shouldSendNoFallPacketDuringRemoteKill(remoteKillPacketRouteActive: Boolean): Boolean =
    !remoteKillPacketRouteActive

internal fun shouldSendNoFallPacketDuringRemoteKill(
    remoteKillMovementOwned: Boolean,
    exclusiveRoutePacketsActive: Boolean,
): Boolean = !remoteKillMovementOwned || !exclusiveRoutePacketsActive

/** Compatibility boundary for existing SpearKill-focused tests and callers. */
internal fun shouldSendNoFallPacketDuringSpearKill(spearKillPacketRouteActive: Boolean): Boolean =
    shouldSendNoFallPacketDuringRemoteKill(spearKillPacketRouteActive)
