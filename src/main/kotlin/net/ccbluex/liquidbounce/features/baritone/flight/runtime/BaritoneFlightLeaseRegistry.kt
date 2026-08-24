/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.baritone.flight.runtime

/** Process-local handshake used only to exempt the Fly module owned by the active Baritone lease. */
object BaritoneFlightLeaseRegistry {

    @Volatile
    private var activeLease: ActiveLease? = null

    fun exemptsFlyConflict(): Boolean = activeLease?.let { lease ->
        runCatching { lease.isValid() }.getOrDefault(false)
    } == true

    internal fun publish(lease: BaritoneFlyLease, isValid: () -> Boolean) {
        activeLease = ActiveLease(lease.generation, isValid)
    }

    internal fun clear(lease: BaritoneFlyLease? = null) {
        if (lease == null || activeLease?.generation == lease.generation) activeLease = null
    }

    private data class ActiveLease(
        val generation: Long,
        val isValid: () -> Boolean,
    )
}
