/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.creativetab

object CreativeTabRegistryHook {
    @JvmStatic fun initialize() = CustomCreativeModeTabs.init()
}
