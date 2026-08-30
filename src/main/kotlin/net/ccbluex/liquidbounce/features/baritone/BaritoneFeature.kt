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

/** Runtime seam shared by Baritone's module, command, REST surface, and bootstrap adapter. */
object BaritoneFeature {

    @Volatile
    private var installedFacade: BaritoneFacade? = null

    @Volatile
    private var dashboard: BaritoneDashboardPort = UnavailableBaritoneDashboard

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

    fun openDashboard() = dashboard.open()

    internal fun isDashboardVisible() = dashboard.isVisible()

    internal fun installDashboard(dashboard: BaritoneDashboardPort) {
        this.dashboard = dashboard
    }

    internal fun restoreDashboard() {
        dashboard = UnavailableBaritoneDashboard
    }
}
