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

package net.ccbluex.liquidbounce.features.module.modules.render.customambience

internal fun resolveWorldClockTime(
    running: Boolean,
    time: ModuleCustomAmbience.TimeType,
    original: Long,
): Long {
    if (!running || time == ModuleCustomAmbience.TimeType.NO_CHANGE) {
        return original
    }
    return when (time) {
        ModuleCustomAmbience.TimeType.NO_CHANGE -> original
        ModuleCustomAmbience.TimeType.DAWN -> 23041L
        ModuleCustomAmbience.TimeType.DAY -> 1000L
        ModuleCustomAmbience.TimeType.NOON -> 6000L
        ModuleCustomAmbience.TimeType.DUSK -> 12610L
        ModuleCustomAmbience.TimeType.NIGHT -> 13000L
        ModuleCustomAmbience.TimeType.MID_NIGHT -> 18000L
    }
}
