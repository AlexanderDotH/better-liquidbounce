/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.runtime

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.features.module.ClientModule

internal object FlyModuleControl {

    private lateinit var activeModeProvider: () -> Mode

    lateinit var module: ClientModule
        private set

    val enabled: Boolean
        get() = module.enabled
    val running: Boolean
        get() = module.running
    val activeMode: Mode
        get() = activeModeProvider()

    fun bind(module: ClientModule, activeModeProvider: () -> Mode) {
        this.module = module
        this.activeModeProvider = activeModeProvider
    }

    fun disable() {
        module.enabled = false
    }

    fun setEnabled(enabled: Boolean) {
        module.enabled = enabled
    }

    fun message(key: String, vararg args: Any) = module.message(key, *args)

}
