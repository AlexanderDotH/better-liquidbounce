/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.misc

interface AppearanceScreenProvider {
    fun restoreOriginalScreen()
    fun updateScreen()
}

object AppearanceScreenBridge {
    private var provider: AppearanceScreenProvider? = null
    fun install(provider: AppearanceScreenProvider) { this.provider = provider }
    fun restoreOriginalScreen() = provider?.restoreOriginalScreen()
    fun updateScreen() = provider?.updateScreen()
}
