/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.integration.screen

import net.ccbluex.liquidbounce.features.misc.AppearanceScreenBridge
import net.ccbluex.liquidbounce.features.misc.AppearanceScreenProvider

object AppearanceScreenAdapter {
    fun install() = AppearanceScreenBridge.install(object : AppearanceScreenProvider {
        override fun restoreOriginalScreen() = ScreenManager.restoreOriginalScreen()
        override fun updateScreen() = ScreenManager.update()
    })
}
