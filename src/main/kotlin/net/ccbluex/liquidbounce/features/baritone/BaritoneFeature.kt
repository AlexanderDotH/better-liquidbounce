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
package net.ccbluex.liquidbounce.features.baritone

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType

/** Runtime seam shared by Baritone's module, command, REST surface, and bootstrap adapter. */
object BaritoneFeature {

    @Volatile
    private var installedFacade: BaritoneFacade? = null

    @Volatile
    private var dashboardOpener: (CustomScreenType) -> Unit = CustomScreenType::open

    @Synchronized
    fun install(facade: BaritoneFacade) {
        installedFacade = facade
    }

    @Synchronized
    fun uninstall(facade: BaritoneFacade) {
        if (installedFacade === facade) {
            installedFacade = null
        }
    }

    fun facadeOrNull(): BaritoneFacade? = installedFacade

    fun openDashboard() = dashboardOpener(CustomScreenType.BARITONE)

    internal fun useDashboardOpener(opener: (CustomScreenType) -> Unit) {
        dashboardOpener = opener
    }

    internal fun restoreDashboardOpener() {
        dashboardOpener = CustomScreenType::open
    }
}
