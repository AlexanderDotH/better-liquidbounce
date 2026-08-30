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
package net.ccbluex.liquidbounce.features.autoconfig.contract

import net.ccbluex.liquidbounce.config.types.group.ValueGroup

object AutoConfigModuleBridge {

    private lateinit var configuredRoot: ValueGroup
    private lateinit var configuredModules: Iterable<ValueGroup>

    val modulesConfig: ValueGroup
        get() = configuredRoot

    val modules: Iterable<ValueGroup>
        get() = configuredModules

    fun install(modulesConfig: ValueGroup, modules: Iterable<ValueGroup>) {
        configuredRoot = modulesConfig
        configuredModules = modules
    }
}

object AutoConfigUiBridge {

    private var clickGuiSync: () -> Unit = {}
    private var hudReopen: () -> Unit = {}

    fun installClickGuiSync(callback: () -> Unit) {
        clickGuiSync = callback
    }

    fun installHudReopen(callback: () -> Unit) {
        hudReopen = callback
    }

    fun syncClickGui() = clickGuiSync()

    fun reopenHud() = hudReopen()
}
