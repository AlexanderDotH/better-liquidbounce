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
@file:JvmName("LocalConfigCodecKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.autoconfig

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.config.gson.util.parseTree
import net.ccbluex.liquidbounce.utils.client.mc
import java.io.Reader

internal object LocalConfigValidator {

    fun parse(reader: Reader): JsonObject = publicGson.newJsonReader(reader).use { jsonReader ->
        val jsonElement = jsonReader.parseTree()
        require(jsonElement.isJsonObject) { "Local config root must be a JSON object" }
        jsonElement.asJsonObject.also(::validate)
    }

    private fun validate(jsonObject: JsonObject) {
        when (val configName = jsonObject.requiredString(NAME, "root")) {
            AUTO_CONFIG_NAME -> {
                jsonObject.validateOptionalValueGroup(MODULES, MODULES)
                jsonObject.validateOptionalValueGroup(SPOOFERS, SPOOFER_CONFIG_NAME)
            }
            MODULES -> validateValueGroup(
                jsonObject,
                "root",
                MODULES,
            )
            else -> error("Unknown local config type: $configName")
        }

        jsonObject.validateOptionalValueGroup(RENDER_MODULES, MODULES)
    }

    private fun JsonObject.validateOptionalValueGroup(key: String, expectedName: String) {
        if (!has(key)) {
            return
        }

        val element = get(key)
        require(element.isJsonObject) { "Local config '$key' must be a JSON object" }
        validateValueGroup(element.asJsonObject, key, expectedName)
    }

    private fun validateValueGroup(jsonObject: JsonObject, path: String, expectedName: String) {
        val actualName = jsonObject.requiredString(NAME, path)
        require(actualName == expectedName) {
            "Local config '$path' has name '$actualName', expected '$expectedName'"
        }

        val values = jsonObject.get(VALUE)
        require(values != null && values.isJsonArray) {
            "Local config '$path.$VALUE' must be a JSON array"
        }

        values.asJsonArray.forEachIndexed { index, element ->
            validateValueEntry(element, "$path.$VALUE[$index]")
        }
    }

    private fun validateValueEntry(element: JsonElement, path: String) {
        require(element.isJsonObject) { "Local config '$path' must be a JSON object" }
        element.asJsonObject.requiredString(NAME, path)
    }

    private fun JsonObject.requiredString(key: String, path: String): String {
        val element = get(key)
        require(element != null && element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            "Local config '$path.$key' must be a string"
        }
        return element.asString
    }

}
