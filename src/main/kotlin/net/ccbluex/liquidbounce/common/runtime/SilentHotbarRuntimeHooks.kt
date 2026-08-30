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
package net.ccbluex.liquidbounce.common.runtime

fun interface SilentHotbarSelectionGate {
    fun allows(requester: Any?, slot: Int): Boolean
}

object SilentHotbarRuntimeHooks {

    private val ALLOW_ALL = SilentHotbarSelectionGate { _, _ -> true }

    @Volatile
    private var selectionGate = ALLOW_ALL

    @Synchronized
    fun installSelectionGate(gate: SilentHotbarSelectionGate) {
        selectionGate = gate
    }

    fun allowsSelection(requester: Any?, slot: Int): Boolean = selectionGate.allows(requester, slot)

    @Synchronized
    internal fun <T> withSelectionGateForTest(
        gate: SilentHotbarSelectionGate,
        block: () -> T,
    ): T {
        val previous = selectionGate
        selectionGate = gate
        return try {
            block()
        } finally {
            selectionGate = previous
        }
    }
}
