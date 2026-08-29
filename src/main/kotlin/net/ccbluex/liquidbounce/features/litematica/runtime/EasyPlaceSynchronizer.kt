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
package net.ccbluex.liquidbounce.features.litematica.runtime

internal class EasyPlaceSynchronizer {

    var printerEnabled = false
        private set

    var easyPlaceEnabled = false
        private set

    private var active = false

    fun activate(currentPrinterToggle: Boolean, currentEasyPlace: Boolean): List<PrinterSyncCommand> {
        active = true
        easyPlaceEnabled = currentEasyPlace
        printerEnabled = currentEasyPlace
        if (currentPrinterToggle == currentEasyPlace) return emptyList()
        return listOf(PrinterSyncCommand.SetPrinterToggle(currentEasyPlace))
    }

    fun deactivate() {
        active = false
        printerEnabled = false
    }

    fun printerChanged(enabled: Boolean): List<PrinterSyncCommand> {
        printerEnabled = enabled
        if (!active || easyPlaceEnabled == enabled) return emptyList()
        easyPlaceEnabled = enabled
        return listOf(PrinterSyncCommand.SetEasyPlace(enabled))
    }

    fun easyPlaceChanged(enabled: Boolean): List<PrinterSyncCommand> {
        if (!active || easyPlaceEnabled == enabled) return emptyList()
        easyPlaceEnabled = enabled
        if (printerEnabled == enabled) return emptyList()
        printerEnabled = enabled
        return listOf(PrinterSyncCommand.SetPrinterToggle(enabled))
    }
}
