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

package net.ccbluex.liquidbounce.config

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.util.parseTree
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.utils.client.clientLogger
import java.io.Reader
import java.io.Writer

internal object ConfigValueGroupCodec {

    private val logger = clientLogger("ConfigSystem")

    fun serialize(valueGroup: ValueGroup, writer: Writer, gson: Gson = fileGson) {
        gson.newJsonWriter(writer).use {
            gson.toJson(valueGroup, ValueGroup::class.javaObjectType, it)
        }
    }

    fun serialize(valueGroup: ValueGroup, gson: Gson = fileGson): JsonObject =
        gson.toJsonTree(valueGroup, ValueGroup::class.javaObjectType) as JsonObject

    fun deserialize(valueGroup: ValueGroup, reader: Reader, gson: Gson = fileGson) {
        gson.newJsonReader(reader).use { jsonReader ->
            deserialize(valueGroup, jsonReader.parseTree())
        }
    }

    fun deserialize(valueGroup: ValueGroup, jsonElement: JsonElement) {
        val jsonObject = jsonElement.asJsonObject
        ConfigMigrationRegistry.applyAll(ConfigMigrationTarget.named(valueGroup.name), jsonObject)
        validateName(valueGroup, jsonObject)
        valueGroup.prepareDeserialize(jsonObject)

        val valuesByName = storedValuesByName(jsonObject)
        ValueGroupDeserializationRegistry.applyAll(valueGroup, valuesByName)
        deserializeChildren(valueGroup, valuesByName)
    }

    fun deserializeValue(value: Value<*>, jsonObject: JsonObject) {
        if (value is ValueGroup) {
            deserializeNestedGroup(value, jsonObject)
            return
        }

        runCatching {
            value.deserializeFrom(fileGson, jsonObject["value"])
        }.onFailure {
            logger.error("Unable to deserialize value ${value.name}", it)
        }
    }

    private fun validateName(valueGroup: ValueGroup, jsonObject: JsonObject) {
        val name = jsonObject.getAsJsonPrimitive("name").asString
        check(name == valueGroup.name || valueGroup.aliases.contains(name)) {
            "config name does not match the name in the json object"
        }
    }

    private fun storedValuesByName(jsonObject: JsonObject): Map<String, ArrayDeque<JsonObject>> = buildMap {
        for (valueElement in jsonObject.getAsJsonArray("value")) {
            val valueObject = valueElement.asJsonObject
            val valueName = valueObject["name"].asString
            getOrPut(valueName) { ArrayDeque(1) }.addLast(valueObject)
        }
    }

    private fun deserializeChildren(
        valueGroup: ValueGroup,
        valuesByName: Map<String, ArrayDeque<JsonObject>>,
    ) {
        for (value in valueGroup.inner) {
            val queue = valuesByName[value.name]
                ?: value.aliases.firstNotNullOfOrNull { valuesByName[it] }
                ?: continue
            if (queue.isNotEmpty()) {
                deserializeValue(value, queue.removeFirst())
            }
        }
    }

    private fun deserializeNestedGroup(value: ValueGroup, jsonObject: JsonObject) {
        runCatching {
            if (value is ModeValueGroup<*>) {
                deserializeMode(value, jsonObject)
            }
            deserialize(value, jsonObject)
        }.onFailure {
            logger.error("Unable to deserialize config ${value.name}", it)
        }
    }

    private fun deserializeMode(value: ModeValueGroup<*>, jsonObject: JsonObject) {
        runCatching {
            value.setByString(jsonObject["active"].asString)
        }.onFailure {
            logger.error("Unable to deserialize active choice for ${value.name}", it)
        }

        val choices = jsonObject["choices"].asJsonObject
        for (choice in value.modes) {
            runCatching {
                val choiceElement = choices[choice.name]
                    ?: choice.aliases.firstNotNullOfOrNull { alias -> choices[alias] }
                    ?: error("Choice ${choice.name} not found")
                deserialize(choice, choiceElement)
            }.onFailure {
                logger.error("Unable to deserialize choice ${choice.name}", it)
            }
        }
    }
}
