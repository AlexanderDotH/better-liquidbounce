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
package net.ccbluex.liquidbounce.features.baritone.adapter

import baritone.api.process.IBaritoneProcess
import baritone.api.process.PathingCommand
import baritone.api.process.PathingCommandType
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseController
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseReason

/** Temporary upstream process that pauses without stealing or cancelling the active navigation process. */
class LiquidBouncePauseProcess(
    private val controller: BaritonePauseController,
    private val pathingRelevant: () -> Boolean,
) : IBaritoneProcess {

    override fun isActive(): Boolean = pathingRelevant() && controller.current().paused

    override fun onTick(calcFailed: Boolean, isSafeToCancel: Boolean): PathingCommand =
        PathingCommand(null, PathingCommandType.REQUEST_PAUSE)

    override fun isTemporary(): Boolean = true

    override fun onLostControl() = Unit

    override fun priority(): Double = Double.MAX_VALUE

    override fun displayName0(): String {
        val cause = controller.current().cause ?: return "LiquidBounce pause"
        if (cause.reason == BaritonePauseReason.MANUAL) return "LiquidBounce manual pause"
        return cause.owner?.let { "LiquidBounce: $it" } ?: "LiquidBounce: ${cause.reason.name.lowercase()}"
    }
}
