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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold.tower

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup

internal interface ScaffoldTowerRuntime {
    val towerMode: ModeValueGroup<*>
    val blockCount: Int
    val isBlockBelow: Boolean
    val timerOwner: Any
}

internal object ScaffoldTowerRuntimeBridge {

    private var runtime: ScaffoldTowerRuntime? = null

    fun install(runtime: ScaffoldTowerRuntime) {
        check(this.runtime == null) { "Scaffold tower runtime is already installed" }
        this.runtime = runtime
    }

    fun requireRuntime(): ScaffoldTowerRuntime =
        checkNotNull(runtime) { "Scaffold tower runtime is not installed" }
}
