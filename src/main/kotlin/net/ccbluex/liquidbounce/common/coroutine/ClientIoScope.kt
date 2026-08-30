/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.common.coroutine

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.ccbluex.liquidbounce.utils.client.error.ErrorHandler
import net.minecraft.ReportedException

val clientIoScope = CoroutineScope(
    Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
        if (throwable is ReportedException) {
            ErrorHandler.fatal(throwable, additionalMessage = "IO scope")
        }
    },
)
