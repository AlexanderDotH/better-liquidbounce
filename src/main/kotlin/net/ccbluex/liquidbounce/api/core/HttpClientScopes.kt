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
@file:JvmName("HttpClientKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.api.core

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.common.coroutine.clientIoScope
import net.ccbluex.liquidbounce.utils.client.error.ErrorHandler
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import net.minecraft.ReportedException

val renderScope = CoroutineScope(
    Dispatchers.Minecraft + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
        if (throwable is ReportedException) {
            ErrorHandler.fatal(throwable, additionalMessage = "Render scope")
        }
    }
)

val ioScope = clientIoScope

fun withScope(block: suspend CoroutineScope.() -> Unit) = ioScope.launch { block() }
