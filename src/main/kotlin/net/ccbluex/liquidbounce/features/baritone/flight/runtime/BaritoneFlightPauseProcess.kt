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

import baritone.api.process.IBaritoneProcess
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType

/**
 * Temporarily takes Baritone pathing control while LiquidBounce steers an active Fly lease.
 *
 * Temporary ownership is essential: a non-temporary process would call `onLostControl` on the
 * upstream target-selection process and, for Elytra, clear its destination.
 */
class BaritoneFlightPauseProcess(
    private val flightOwnsMovement: () -> Boolean,
) : IBaritoneProcess {

    override fun isActive(): Boolean = flightOwnsMovement()

    override fun onTick(calcFailed: Boolean, isSafeToCancel: Boolean): PathingCommand =
        PathingCommand(null, PathingCommandType.REQUEST_PAUSE)

    override fun isTemporary(): Boolean = true

    override fun onLostControl() = Unit

    override fun priority(): Double = FLIGHT_PRIORITY

    override fun displayName0(): String = "LiquidBounce Fly navigation"

    private companion object {
        const val FLIGHT_PRIORITY = 1_000_000.0
    }
}
