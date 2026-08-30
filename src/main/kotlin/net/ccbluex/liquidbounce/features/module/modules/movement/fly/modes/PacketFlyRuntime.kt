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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.common.Tagged
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.IdentityHashMap

internal enum class PacketFlySpeedExploit(override val tag: String) : Tagged {
    SAFE("Safe"),
    PRIMED("Primed"),
}

internal object PacketFlyRuntimePolicy {

    fun shouldPlan(collisionResolvedMovement: Vec3, remoteKillOwnsPacketRoute: Boolean) =
        !remoteKillOwnsPacketRoute && collisionResolvedMovement != Vec3.ZERO

    fun resolvePhysicalMovement(collisionResolvedMovement: Vec3, remoteKillOwnsPacketRoute: Boolean) =
        if (remoteKillOwnsPacketRoute) Vec3.ZERO else collisionResolvedMovement
}

/**
 * Finds the least packet reservation whose resulting endpoint forecasts no more auxiliaries than it reserved.
 * A null forecast means that reservation itself cannot produce a complete plan.
 */
internal fun findMinimumFeasiblePacketReservation(
    maxReservation: Int,
    forecastForReservation: (Int) -> Int?,
): Int? {
    require(maxReservation >= 0) { "Maximum reservation must not be negative" }
    val forecasts = mutableMapOf<Int, Int?>()
    fun forecast(reservation: Int): Int? = if (forecasts.containsKey(reservation)) {
        forecasts[reservation]
    } else {
        forecastForReservation(reservation).also { forecasts[reservation] = it }
    }

    if (forecast(0) == null) {
        return null
    }

    var greatestPlannable = 0
    var firstBlocked = maxReservation + 1
    while (greatestPlannable + 1 < firstBlocked) {
        val candidate = greatestPlannable + (firstBlocked - greatestPlannable) / 2
        if (forecast(candidate) == null) {
            firstBlocked = candidate
        } else {
            greatestPlannable = candidate
        }
    }
    if (requireNotNull(forecast(greatestPlannable)) > greatestPlannable) {
        return null
    }

    var firstInfeasible = -1
    var leastFeasible = greatestPlannable
    while (firstInfeasible + 1 < leastFeasible) {
        val candidate = firstInfeasible + (leastFeasible - firstInfeasible) / 2
        val required = forecast(candidate)
        if (required != null && required <= candidate) {
            leastFeasible = candidate
        } else {
            firstInfeasible = candidate
        }
    }
    return leastFeasible
}

internal enum class PacketFlyDeliveryResult {
    UNRELATED,
    AUXILIARY_DELIVERED,
    AUXILIARY_REJECTED,
    FINAL_DELIVERED,
    FINAL_REJECTED,
}

internal fun matchesPacketFlyEndpoint(packet: ServerboundMovePlayerPacket, endpoint: Vec3) =
    packet.hasPos && packet.getX(endpoint.x) == endpoint.x &&
        packet.getY(endpoint.y) == endpoint.y && packet.getZ(endpoint.z) == endpoint.z

/** Tracks only one fully calculated movement plan and deliberately uses packet identity. */
internal class PacketFlyDeliveryTracker<T : Any> {

    private val auxiliaryDelivery = Collections.synchronizedMap(IdentityHashMap<T, Boolean>())
    private var orderedAuxiliaries = emptyList<T>()
    private var finalPacket: T? = null

    var deliveredAuxiliaryCount = 0
        private set

    val active: Boolean
        get() = orderedAuxiliaries.isNotEmpty() || finalPacket != null

    val auxiliaryPackets: List<T>
        get() = orderedAuxiliaries

    val allAuxiliariesDelivered: Boolean
        get() = synchronized(auxiliaryDelivery) { auxiliaryDelivery.values.all { it } }

    fun stage(auxiliaries: List<T>) {
        clear()
        orderedAuxiliaries = auxiliaries.toList()
        auxiliaries.forEach { auxiliaryDelivery[it] = false }
    }

    fun ownsAuxiliary(packet: T) = auxiliaryDelivery.containsKey(packet)

    fun owns(packet: T) = ownsAuxiliary(packet) || packet === finalPacket

    fun wasDelivered(packet: T) = auxiliaryDelivery[packet] == true

    fun expectFinalPacket(packet: T) {
        finalPacket = packet
    }

    fun isExpectedFinalPacket(packet: T) = packet === finalPacket

    fun confirm(packet: T, delivered: Boolean): PacketFlyDeliveryResult {
        if (auxiliaryDelivery.containsKey(packet)) {
            return confirmAuxiliary(packet, delivered)
        }
        if (packet !== finalPacket) {
            return PacketFlyDeliveryResult.UNRELATED
        }

        val result = if (delivered && allAuxiliariesDelivered) {
            PacketFlyDeliveryResult.FINAL_DELIVERED
        } else {
            PacketFlyDeliveryResult.FINAL_REJECTED
        }
        clear()
        return result
    }

    private fun confirmAuxiliary(packet: T, delivered: Boolean): PacketFlyDeliveryResult {
        val alreadyDelivered = auxiliaryDelivery[packet] == true
        if (delivered && !alreadyDelivered) {
            auxiliaryDelivery[packet] = true
            deliveredAuxiliaryCount++
        }
        return if (delivered) {
            PacketFlyDeliveryResult.AUXILIARY_DELIVERED
        } else {
            PacketFlyDeliveryResult.AUXILIARY_REJECTED
        }
    }

    fun clear() {
        auxiliaryDelivery.clear()
        orderedAuxiliaries = emptyList()
        finalPacket = null
        deliveredAuxiliaryCount = 0
    }
}
