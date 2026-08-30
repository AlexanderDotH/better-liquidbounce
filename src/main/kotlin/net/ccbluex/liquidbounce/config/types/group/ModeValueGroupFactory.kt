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

@file:JvmName("JsonValueFactoryKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.config.types.group

import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.config.types.group.ValueGroup.ModeBuilder
import net.ccbluex.liquidbounce.common.Tagged.Companion.asTagged
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.collection.itemSortedSetOf
import kotlin.collections.mapTo

internal fun ValueGroup.modes(
    name: String,
    default: String,
    modes: Map<String, ModeBuilder>,
): ModeValueGroup<Mode> {
    require(modes.isNotEmpty()) { "Mode group '$name' must contain at least one mode." }
    class SimpleMode(name: String, override val parent: ModeValueGroup<*>) : Mode(name)

    return modes(
        eventListener = null,
        name = name,
        activeCallback = { modes ->
            val idx = modes.indexOfFirst { it.name == default }

            check(idx != -1) {
                "The active choice $default is not contained within the choice array" +
                    " (${modes.joinToString { it.name }})"
            }

            idx
        },
        modesCallback = { parent ->
            modes.entries.mapToArray { (modeName, configure) ->
                val mode = SimpleMode(modeName, parent)

                with(configure) {
                    mode.build()
                }
                mode
            }
        },
    )
}
