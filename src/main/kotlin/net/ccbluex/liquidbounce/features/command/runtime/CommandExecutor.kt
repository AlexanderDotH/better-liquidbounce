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

package net.ccbluex.liquidbounce.features.command.runtime

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandHandlerBuilder
import net.ccbluex.liquidbounce.features.command.CommandRuntime

/**
 * Compatibility entry point for integrations using the historical package.
 */
object CommandExecutor : EventListener {

    init {
        CommandRuntime
    }

    fun <B : CommandHandlerBuilder<B>> B.suspendHandler(
        allowParallel: Boolean = false,
        handler: Command.Handler.Suspend,
    ): B = CommandRuntime.installSuspendHandler(this, allowParallel, handler)

    internal fun handleExceptions(e: Throwable) = CommandRuntime.handleExceptions(e)
}
