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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed.contract

internal object SpeedState {

    private lateinit var enabledProvider: () -> Boolean
    private lateinit var enabledUpdater: (Boolean) -> Unit

    val enabled: Boolean
        get() = enabledProvider()

    fun bind(enabledProvider: () -> Boolean, enabledUpdater: (Boolean) -> Unit) {
        this.enabledProvider = enabledProvider
        this.enabledUpdater = enabledUpdater
    }

    fun disable() {
        enabledUpdater(false)
    }

}
