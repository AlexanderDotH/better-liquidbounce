/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.misc.proxy

object ProxyRuntimeHook {
    @JvmStatic fun currentProxy() = ProxyManager.currentProxy
    @JvmStatic fun hasCurrentProxy() = ProxyManager.currentProxy != null
}
