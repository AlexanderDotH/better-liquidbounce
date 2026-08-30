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
package net.ccbluex.liquidbounce.config.types.group

import net.ccbluex.liquidbounce.config.OptionalInclusion
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.event.EventListener
import java.util.function.ToIntFunction

open class ValueGroup(
    name: String,
    value: MutableCollection<Value<*>> = mutableListOf(),
    valueType: ValueType = ValueType.CONFIGURABLE,

    /**
     * Signalizes that the [ValueGroup]'s translation key
     * should not depend on another [ValueGroup].
     * This means the [baseKey] will be directly used.
     *
     * The options should be used in common options, so that
     * descriptions don't have to be written twice.
     */
    independentDescription: Boolean = false,
    /**
     * Used for backwards compatibility when renaming.
     */
    aliases: List<String> = emptyList(),
) : RegistryFactory(name, value, valueType, independentDescription, aliases) {

    final override val group: ValueGroup
        get() = this

    override fun inclusionGroup(group: OptionalInclusion): ValueGroup = apply {
        super.inclusionGroup(group)
        inner.forEach { value -> value.inclusionGroup(group) }
    }

    fun interface ModeBuilder {
        fun ValueGroup.build()
    }

    protected fun <T : Mode> modes(
        eventListener: EventListener?,
        name: String,
        active: T,
        modes: Array<T>,
    ): ModeValueGroup<T> {
        return modes(eventListener, name, { availableModes ->
            val index = availableModes.indexOf(active)

            check(index != -1) {
                "The active choice $active is not contained within the choice array" +
                    " (${availableModes.joinToString { it.name }})"
            }

            index
        }) { modes }
    }

    fun <T : Mode> modes(
        eventListener: EventListener?,
        name: String,
        activeCallback: ToIntFunction<List<T>>,
        modesCallback: (ModeValueGroup<T>) -> Array<T>,
    ): ModeValueGroup<T> {
        return value(ModeValueGroup(eventListener, name, activeCallback, modesCallback).apply {
            base = this@ValueGroup
        })
    }

    protected fun <T : Mode> modes(
        eventListener: EventListener,
        name: String,
        activeIndex: Int = 0,
        choicesCallback: (ModeValueGroup<T>) -> Array<T>,
    ) = modes(eventListener, name, { activeIndex }, choicesCallback)
}
