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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.contract

internal object FlyState {

    private lateinit var enabledProvider: () -> Boolean
    private lateinit var runningProvider: () -> Boolean

    val enabled: Boolean
        get() = enabledProvider()
    val running: Boolean
        get() = runningProvider()

    fun bind(enabledProvider: () -> Boolean, runningProvider: () -> Boolean) {
        this.enabledProvider = enabledProvider
        this.runningProvider = runningProvider
    }

}
