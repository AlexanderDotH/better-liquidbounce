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
package net.ccbluex.liquidbounce.event

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap

internal class RuntimeEventRegistry<V : Any>(
    eventClasses: Array<Class<out Event>>,
    private val registerEventClass: (Class<out Event>) -> Unit,
    valueFactory: () -> V,
) {
    private val entries = eventClasses.associateWithTo(
        Reference2ObjectOpenHashMap(eventClasses.size),
    ) { valueFactory() }

    operator fun get(eventClass: Class<out Event>): V? {
        entries[eventClass]?.let { return it }
        if (RuntimeRegisteredEvent::class.java.isAssignableFrom(eventClass)) {
            registerEventClass(eventClass)
        }
        return entries[eventClass]
    }

    fun putIfAbsent(eventClass: Class<out Event>, value: V): V? = entries.putIfAbsent(eventClass, value)

    val keys: Set<Class<out Event>>
        get() = entries.keys

    val values: Collection<V>
        get() = entries.values
}
