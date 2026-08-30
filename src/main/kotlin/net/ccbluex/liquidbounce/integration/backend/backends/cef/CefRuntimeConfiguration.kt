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
package net.ccbluex.liquidbounce.integration.backend.backends.cef

import net.ccbluex.liquidbounce.integration.backend.isBrowserAccelerationDisabled
import net.ccbluex.liquidbounce.utils.client.env
import net.minecraft.util.Util

internal fun shouldDisableCefGpuAcceleration() = CefSwitches.shouldDisableGpuAcceleration(
    isLinux = Util.getPlatform() == Util.OS.LINUX,
    disableGpuAcceleration = isBrowserAccelerationDisabled,
    disableDmabufRenderer = System.getenv("WEBKIT_DISABLE_DMABUF_RENDERER") == "1",
    forceGpuAcceleration = env(
        "LB_BROWSER_FORCE_ACCELERATION",
        "net.ccbluex.liquidbounce.browser.forceAcceleration",
    )?.toBoolean() == true,
)
