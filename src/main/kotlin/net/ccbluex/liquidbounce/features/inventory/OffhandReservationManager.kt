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

import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.kotlin.Priority

/**
 * Coordinates short-lived, exclusive access to the player's offhand.
 *
 * Callers renew their reservation while operating. A different owner may only preempt when its inventory priority is
 * strictly higher. Owner comparisons deliberately use identity so an equal value cannot release another feature's
 * reservation.
 */
object OffhandReservationManager : EventListener {

    const val DEFAULT_EXPIRY_TICKS = 3

    private var currentTick = 0L
    private var reservation: OffhandReservation? = null

    val activeReservation: OffhandReservation?
        get() {
            expireReservation()
            return reservation
        }

    val isReserved: Boolean
        get() = activeReservation != null

    fun reserve(
        owner: Any,
        priority: Priority,
        expiryTicks: Int = DEFAULT_EXPIRY_TICKS,
    ): Boolean {
        require(expiryTicks > 0) { "expiryTicks must be positive" }

        val active = activeReservation
        if (active != null && active.owner !== owner && active.priority.priority >= priority.priority) {
            return false
        }

        reservation = OffhandReservation(owner, priority, currentTick + expiryTicks)
        return true
    }

    fun release(owner: Any): Boolean {
        if (activeReservation?.owner !== owner) {
            return false
        }

        reservation = null
        return true
    }

    fun isReservedBy(owner: Any): Boolean = activeReservation?.owner === owner

    fun isReservedByOther(owner: Any): Boolean = activeReservation?.owner?.let { it !== owner } == true

    internal fun advanceTick() {
        currentTick++
        expireReservation()
    }

    internal fun clear() {
        reservation = null
        currentTick = 0L
    }

    private fun expireReservation() {
        if (reservation?.expiresAtTick?.let { it <= currentTick } == true) {
            reservation = null
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        advanceTick()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        clear()
    }
}

data class OffhandReservation internal constructor(
    val owner: Any,
    val priority: Priority,
    val expiresAtTick: Long,
)
