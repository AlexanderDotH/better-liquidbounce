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

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.common.Tagged.Companion.asTagged
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.collection.itemSortedSetOf
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

internal fun ValueGroup.readJsonConfigurable(name: String, json: JsonObject) {
    val child = ValueGroup(name)
    json["values"].asJsonArray.forEach { child.json(it.asJsonObject) }
    tree(child)
}

internal fun ValueGroup.readJsonToggleable(name: String, json: JsonObject) {
    val child = object : ToggleableValueGroup(null, name, json["value"].asBoolean) {}
    json["values"].asJsonArray.forEach { child.json(it.asJsonObject) }
    tree(child)
}

internal fun ValueGroup.readJsonChoose(name: String, json: JsonObject) {
    val choices = json["choices"].asJsonArray.mapTo(linkedSetOf()) { it.asString.asTagged() }
    enumChoice(name, json["value"].asString.asTagged(), choices)
}

internal fun ValueGroup.readJsonChoice(name: String, json: JsonObject) {
    val modes = json["choices"].asJsonArray.associateTo(linkedMapOf()) { element ->
        val choice = element.asJsonObject
        val settings = choice["values"]?.asJsonArray ?: emptyList()
        choice["name"].asString to ValueGroup.ModeBuilder {
            settings.forEach { setting -> json(setting.asJsonObject) }
        }
    }
    modes(name, json["value"].asString, modes)
}

internal fun ValueGroup.readJsonMultiChoose(name: String, json: JsonObject) {
    val canBeNone = json.booleanOrDefault("canBeNone", default = true)
    val orderSensitive = json.booleanOrDefault("isOrderSensitive", default = false)
    val values = json["value"].asJsonArray.mapTo(if (orderSensitive) linkedSetOf() else sortedSetOf()) {
        it.asString.asTagged()
    }
    val choices = json["choices"].asJsonArray.mapTo(linkedSetOf()) { it.asString.asTagged() }
    multiEnumChoice(name, values, choices, canBeNone, orderSensitive)
}

internal fun ValueGroup.readJsonRegistryList(name: String, json: JsonObject) {
    val innerType = enumValueOf<ValueType>(json["innerValueType"].asString)
    val values = normalizeRegistryValues(json["value"])
    when (innerType) {
        ValueType.BLOCK -> blocks(name, values.mapTo(blockSortedSetOf()) { publicGson.fromJson(it, Block::class.java) })
        ValueType.ITEM -> items(name, values.mapTo(itemSortedSetOf()) { publicGson.fromJson(it, Item::class.java) })
        else -> error("Unsupported inner value type for ${ValueType.REGISTRY_LIST}: $innerType")
    }
}

private fun JsonObject.booleanOrDefault(key: String, default: Boolean): Boolean = when (val value = get(key)) {
    null, is JsonNull -> default
    is JsonPrimitive, is JsonArray -> value.asBoolean
    else -> error("Unexpected JSON value (${value.javaClass}): $value, should be boolean")
}

private fun normalizeRegistryValues(value: com.google.gson.JsonElement?): Iterable<com.google.gson.JsonElement> =
    when (value) {
        is JsonArray -> value
        is JsonPrimitive -> listOf(value)
        null, is JsonNull -> emptyList()
        else -> error("Unexpected JSON value (${value.javaClass}): $value, should be Identifier list")
    }
