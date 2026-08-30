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
package net.ccbluex.liquidbounce.script.bindings.features

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.config.types.list.MultiChoiceListValue
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.deeplearn.ModelManager.list
import net.ccbluex.liquidbounce.script.asArray
import net.ccbluex.liquidbounce.script.asDoubleArray
import net.ccbluex.liquidbounce.script.asIntArray
import net.ccbluex.liquidbounce.utils.input.inputByName
import org.graalvm.polyglot.Value as PolyglotValue

/**
 * Object used by the script API to provide an idiomatic way of creating module values.
 */
@Suppress("unused", "StringLiteralDuplication")
object ScriptSetting {

    @JvmName("boolean")
    fun boolean(value: PolyglotValue): Value<Boolean> {
        val name = value.getMember("name").asString()
        val default = value.getMember("default").asBoolean()

        return scriptValue(name, default, ValueType.BOOLEAN)
    }

    @JvmName("float")
    fun float(value: PolyglotValue): RangedValue<Float> {
        val name = value.getMember("name").asString()
        val default = value.getMember("default").asDouble()
        val range = value.getMember("range").asDoubleArray()
        val suffix = value.getMember("suffix")?.asString() ?: ""

        require(range.size == 2)
        return scriptRangedValue(
            name,
            default.toFloat(),
            range.first().toFloat()..range.last().toFloat(),
            suffix,
            ValueType.FLOAT
        )
    }

    @JvmName("floatRange")
    fun floatRange(value: PolyglotValue): RangedValue<ClosedFloatingPointRange<Float>> {
        val name = value.getMember("name").asString()
        val default = value.getMember("default").asDoubleArray()
        val range = value.getMember("range").asDoubleArray()
        val suffix = value.getMember("suffix")?.asString() ?: ""

        require(default.size == 2)
        require(range.size == 2)
        return scriptRangedValue(
            name,
            default.first().toFloat()..default.last().toFloat(),
            range.first().toFloat()..range.last().toFloat(),
            suffix,
            ValueType.FLOAT_RANGE
        )
    }

    @JvmName("int")
    fun int(value: PolyglotValue): RangedValue<Int> {
        val name = value.getMember("name").asString()
        val default = value.getMember("default").asInt()
        val range = value.getMember("range").asIntArray()
        val suffix = value.getMember("suffix")?.asString() ?: ""

        require(range.size == 2)
        return scriptRangedValue(name, default, range.first()..range.last(), suffix, ValueType.INT)
    }

    @JvmName("intRange")
    fun intRange(value: PolyglotValue): RangedValue<IntRange> {
        val name = value.getMember("name").asString()
        val default = value.getMember("default").asIntArray()
        val range = value.getMember("range").asIntArray()
        val suffix = value.getMember("suffix")?.asString() ?: ""

        require(default.size == 2)
        require(range.size == 2)
        return scriptRangedValue(
            name,
            default.first()..default.last(),
            range.first()..range.last(),
            suffix,
            ValueType.INT_RANGE
        )
    }

    @JvmName("key")
    fun key(value: PolyglotValue): Value<InputConstants.Key> {
        val name = value.getMember("name").asString()
        val default = inputByName(value.getMember("default").asString())

        return scriptValue(name, default, ValueType.KEY)
    }

    @JvmName("text")
    fun text(value: PolyglotValue): Value<String> {
        val name = value.getMember("name").asString()
        val default = value.getMember("default").asString()

        return scriptValue(name, default, ValueType.TEXT)
    }

    @JvmName("textArray")
    fun textArray(value: PolyglotValue): Value<MutableList<String>> {
        val name = value.getMember("name").asString()
        val default = value.getMember("default").asArray<String>()

        return list(name, default.toMutableList(), ValueType.TEXT)
    }

    @JvmName("choose")
    fun choose(value: PolyglotValue): ChoiceListValue<Tagged> {
        val name = value.getMember("name").asString()
        val choices = value.getMember("choices").asArray<String>().toScriptNamedChoices(::LinkedHashSet)
        val defaultStr = value.getMember("default").asString()

        val default = choices.find { it.tag == defaultStr }
            ?: error(
                "[ScriptAPI] Choose default value '${defaultStr}' is not part of choices '${
                    choices.joinToString(", ") { it.tag }
                }'"
            )

        return ChoiceListValue(name, defaultValue = default, choices = choices)
    }

    @JvmName("multiChoose")
    fun multiChoose(value: PolyglotValue): MultiChoiceListValue<Tagged> {
        val name = value.getMember("name").asString()
        val choices = value.getMember("choices").asArray<String>().toScriptNamedChoices(::LinkedHashSet)
        val default = value.getMember("default")?.asArray<String>().toScriptNamedChoices(::HashSet)

        val canBeNone = value.getMember("canBeNone")?.asBoolean() ?: true

        return MultiChoiceListValue(
            name,
            value = default,
            choices = choices,
            canBeNone = canBeNone
        )
    }

}
