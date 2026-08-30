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

package net.ccbluex.liquidbounce.config.types

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.input.inputByName
import org.graalvm.polyglot.Value as PolyglotValue

internal object ValueScriptConversion {

    fun <T : Any> assign(target: Value<T>, source: PolyglotValue) = runCatching {
        if (target is ChoiceListValue<*>) {
            target.setByString(source.asString())
            return@runCatching
        }
        target.set(convert(target.inner, source))
    }.onFailure {
        logger.error("Could not set value, old value: ${target.inner}, throwable: $it")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> convert(current: T, source: PolyglotValue): T = when (current) {
        is ClosedFloatingPointRange<*> -> floatRange(source) as T
        is InputConstants.Key -> inputByName(source.asString()) as T
        is IntRange -> intRange(source) as T
        is Float -> source.asDouble().toFloat() as T
        is Int -> source.asInt() as T
        is String -> source.asString() as T
        is MutableList<*> -> source.`as`(Array<String>::class.java).toMutableList() as T
        is LinkedHashSet<*> -> source.`as`(Array<String>::class.java).toMutableSet() as T
        is Boolean -> source.asBoolean() as T
        else -> error("Unsupported value type $current")
    }

    private fun floatRange(source: PolyglotValue): ClosedFloatingPointRange<Float> {
        val values = source.`as`(DoubleArray::class.java)
        require(values.size == 2)
        return values.first().toFloat()..values.last().toFloat()
    }

    private fun intRange(source: PolyglotValue): IntRange {
        val values = source.`as`(IntArray::class.java)
        require(values.size == 2)
        return values.first()..values.last()
    }
}
