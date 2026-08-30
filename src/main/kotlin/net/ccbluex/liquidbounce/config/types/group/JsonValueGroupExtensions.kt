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

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.ValueType

private typealias JsonValueReader = ValueGroup.(String, JsonObject) -> Unit

private val JSON_VALUE_READERS: Map<ValueType, JsonValueReader> = mapOf(
    ValueType.BOOLEAN to ValueGroup::readJsonBoolean,
    ValueType.INT to ValueGroup::readJsonInt,
    ValueType.INT_RANGE to ValueGroup::readJsonIntRange,
    ValueType.FLOAT to ValueGroup::readJsonFloat,
    ValueType.FLOAT_RANGE to ValueGroup::readJsonFloatRange,
    ValueType.TEXT to ValueGroup::readJsonText,
    ValueType.FILE to ValueGroup::readJsonFile,
    ValueType.COLOR to ValueGroup::readJsonColor,
    ValueType.CONFIGURABLE to ValueGroup::readJsonConfigurable,
    ValueType.TOGGLEABLE to ValueGroup::readJsonToggleable,
    ValueType.CHOOSE to ValueGroup::readJsonChoose,
    ValueType.CHOICE to ValueGroup::readJsonChoice,
    ValueType.MULTI_CHOOSE to ValueGroup::readJsonMultiChoose,
    ValueType.REGISTRY_LIST to ValueGroup::readJsonRegistryList,
)

/**
 * Assigns the value of the settings to the component
 *
 * A component can have dynamic settings which can be assigned through the JSON file
 * These have to be interpreted and assigned to the value group
 *
 * An example:
 * {
 *     "type": "INT",
 *     "name": "Size",
 *     "value": 14,
 *     "range": {
 *         "min": 1,
 *         "max": 100
 *     },
 *     "suffix": "px"
 * }
 *
 * TODO: Replace with proper deserialization
 *
 * @param valueObject JsonObject
 */
fun ValueGroup.json(valueObject: JsonObject) {
    val type = enumValueOf<ValueType>(valueObject["type"].asString)
    val name = valueObject["name"].asString
    val reader = JSON_VALUE_READERS[type] ?: error("Unsupported type: $type")
    reader(this, name, valueObject)
}
