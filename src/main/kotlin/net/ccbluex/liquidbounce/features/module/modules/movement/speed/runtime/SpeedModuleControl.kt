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

package net.ccbluex.liquidbounce.features.module.modules.movement.speed.runtime

import net.ccbluex.liquidbounce.features.module.modules.movement.speed.contract.SpeedModulePort

internal object SpeedModuleControl {

    private lateinit var modulePort: SpeedModulePort

    val module: Any
        get() = modulePort.timerOwner

    val enabled: Boolean
        get() = modulePort.enabled

    fun bind(modulePort: SpeedModulePort) {
        this.modulePort = modulePort
    }

    fun disable() {
        modulePort.disable()
    }

    fun doOptimizationsPreventJump(): Boolean = modulePort.doOptimizationsPreventJump()

}
