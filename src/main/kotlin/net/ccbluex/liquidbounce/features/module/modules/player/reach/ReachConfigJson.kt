/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.player.reach

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal fun JsonObject.enabledState(): Boolean? {
    val element = configValue(ENABLED_NAME)?.get(VALUE_KEY) ?: return null
    return element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
}

internal fun JsonObject.configValue(name: String): JsonObject? = arrayOrNull(VALUE_KEY)?.namedObject(name)

internal fun JsonObject.ensureValues(): JsonArray =
    arrayOrNull(VALUE_KEY) ?: JsonArray().also { add(VALUE_KEY, it) }

internal fun JsonArray.putConfigValue(name: String, value: Boolean) {
    val stored = namedObject(name)
    if (stored == null) {
        add(configValue(name, value))
        return
    }
    stored.addProperty(VALUE_KEY, value)
}

internal fun JsonArray.replace(old: JsonObject, replacement: JsonObject) {
    for (index in 0 until size()) {
        if (get(index) === old) {
            set(index, replacement)
            return
        }
    }
}

internal fun JsonArray.namedObjects(name: String): List<JsonObject> =
    objectValues().filter { it.stringProperty(NAME_KEY) == name }

internal fun JsonArray.namedObject(name: String): JsonObject? = objectValues().firstNamed(name)

internal fun Iterable<JsonObject>.firstNamed(name: String): JsonObject? =
    firstOrNull { it.stringProperty(NAME_KEY) == name }

internal fun JsonArray.objectValues(): List<JsonObject> = mapNotNull { element ->
    element.takeIf { it.isJsonObject }?.asJsonObject
}

internal fun JsonObject.arrayOrNull(name: String): JsonArray? = get(name)
    ?.takeIf { it.isJsonArray }
    ?.asJsonArray

internal fun JsonObject.objectOrNull(name: String): JsonObject? = get(name)
    ?.takeIf { it.isJsonObject }
    ?.asJsonObject

internal fun JsonObject.stringProperty(name: String): String? = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString

internal fun configValue(name: String, value: Boolean) = JsonObject().apply {
    addProperty(NAME_KEY, name)
    addProperty(VALUE_KEY, value)
}

internal const val NAME_KEY = "name"
internal const val VALUE_KEY = "value"
internal const val REACH_NAME = "Reach"
internal const val SUPER_HIT_NAME = "SuperHit"
internal const val HIT_NAME = "Hit"
internal const val ENABLED_NAME = "Enabled"

internal val MODULE_METADATA = setOf(ENABLED_NAME, "Bind", "Hidden")
internal val MODULE_INHERITED_METADATA = listOf("Bind", "Hidden")
