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
package net.ccbluex.liquidbounce.features.module.modules.misc.safeactions

import org.lwjgl.glfw.GLFW.GLFW_PRESS

internal class SafeDropPressTracker {

    private val freshPresses = ArrayDeque<Unit>()

    fun record(action: Int) {
        if (action != GLFW_PRESS) {
            return
        }

        freshPresses.addLast(Unit)
    }

    /**
     * Screen key handling runs synchronously inside the raw input callback. Replace any unconsumed
     * token so a later GLFW repeat can never consume an older physical press.
     */
    fun recordImmediate(action: Int) {
        clear()
        record(action)
    }

    fun consumeFreshPress(): Boolean {
        if (freshPresses.isEmpty()) {
            return false
        }

        freshPresses.removeFirst()
        return true
    }

    fun clear() {
        freshPresses.clear()
    }
}
