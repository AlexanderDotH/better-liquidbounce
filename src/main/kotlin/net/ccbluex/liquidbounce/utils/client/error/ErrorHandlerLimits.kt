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
@file:Suppress("NOTHING_TO_INLINE")

@file:JvmName("ErrorHandlerKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.client.error

import net.ccbluex.liquidbounce.utils.client.browseUrl
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.util.Util
import net.minecraft.util.Util.OS.WINDOWS
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.math.min
import kotlin.system.exitProcess

internal val MAX_STACKTRACE_LINES = when (Util.getPlatform()) {
    WINDOWS -> 3
    else -> 1
}
