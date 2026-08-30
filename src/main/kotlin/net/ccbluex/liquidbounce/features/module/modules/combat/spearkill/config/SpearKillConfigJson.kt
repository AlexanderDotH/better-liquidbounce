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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config


import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun spearKillRoutingValue(
    active: String,
    aStarValues: JsonArray,
    sourceChoices: JsonObject?,
) = JsonObject().apply {
    addProperty("name", "Routing")
    addProperty("active", active)
    add("value", JsonArray())
    add("choices", JsonObject().apply {
        add("Direct", spearKillChoice("Direct"))
        add("AStar", spearKillChoice("AStar", aStarValues))
        add(
            "NetworkOptimized",
            spearKillChoice("NetworkOptimized", sourceChoices.spearKillNetworkOptimizedChoiceValues()),
        )
        add("Instant", spearKillChoice("Instant", sourceChoices.spearKillMovementChoiceValues("Instant")))
    })
}

internal fun spearKillChoice(name: String, values: JsonArray = JsonArray()) = JsonObject().apply {
    addProperty("name", name)
    add("value", values)
}

internal fun spearKillScalarValue(name: String, value: Any) = JsonObject().apply {
    addProperty("name", name)
    when (value) {
        is Boolean -> addProperty("value", value)
        is Number -> addProperty("value", value)
        else -> addProperty("value", value.toString())
    }
}

internal fun canonicalSpearKillMovementMode(value: String?): String = when {
    value.equals("Packet", ignoreCase = true) || value.equals("PacketBoot", ignoreCase = true) ||
        value.equals("Packet-Boot", ignoreCase = true) -> "Packet"
    else -> "Motion"
}

internal fun canonicalSpearKillRoutingMode(value: String?): String = when {
    value.equals("Instant", ignoreCase = true) -> "Instant"
    value.equals("AStar", ignoreCase = true) || value.equals("Adaptive", ignoreCase = true) -> "AStar"
    value.equals("NetworkOptimized", ignoreCase = true) || value.equals("Network", ignoreCase = true) ||
        value.equals("LagOptimized", ignoreCase = true) ||
        value.equals("Network-Optimized", ignoreCase = true) -> "NetworkOptimized"
    else -> "Direct"
}

internal fun JsonObject.spearKillConfigValues(): JsonArray =
    get("value")?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()

internal fun JsonObject.spearKillMovementChoice(name: String, vararg aliases: String): JsonObject? {
    val acceptedNames = setOf(name, *aliases)
    return entrySet().firstOrNull { (choiceName, _) ->
        acceptedNames.any { choiceName.equals(it, ignoreCase = true) }
    }?.value?.takeIf(JsonElement::isJsonObject)?.asJsonObject
}

internal fun JsonObject?.spearKillMovementChoiceValues(
    name: String,
    vararg aliases: String,
): JsonArray = this?.spearKillMovementChoice(name, *aliases)?.spearKillConfigValues() ?: JsonArray()

internal fun JsonObject?.spearKillNetworkOptimizedChoiceValues(): JsonArray = spearKillMovementChoiceValues(
    "NetworkOptimized",
    "Network",
    "LagOptimized",
    "Network-Optimized",
)

internal fun JsonArray.spearKillConfigValue(name: String): JsonObject? = firstOrNull { element ->
    element.isJsonObject && element.asJsonObject["name"]?.takeIf(JsonElement::isJsonPrimitive)
        ?.asString?.equals(name, ignoreCase = true) == true
}?.asJsonObject

internal fun JsonObject.booleanValue(): Boolean? =
    get("value")?.takeIf(JsonElement::isJsonPrimitive)?.asBoolean

internal fun JsonObject.numberValue(): Double? =
    get("value")?.takeIf(JsonElement::isJsonPrimitive)?.asDouble

internal fun JsonArray.canonicalizingSpearKillValue(canonical: String, vararg aliases: String): JsonArray {
    val explicit = firstOrNull { element ->
        element.isJsonObject && element.asJsonObject["name"]?.asString == canonical
    }?.asJsonObject
    val acceptedNames = setOf(canonical, *aliases)
    var emitted = false
    return JsonArray().also { result ->
        for (element in this) {
            val name = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
                ?.get("name")?.takeIf(JsonElement::isJsonPrimitive)?.asString
            if (name != null && acceptedNames.any { name.equals(it, ignoreCase = true) }) {
                if (!emitted) {
                    val selected = (explicit ?: element.asJsonObject).deepCopy()
                    selected.addProperty("name", canonical)
                    result.add(selected)
                    emitted = true
                }
            } else {
                result.add(element.deepCopy())
            }
        }
    }
}

internal fun JsonArray.withoutSpearKillConfigValues(vararg names: String): JsonArray = JsonArray().also { result ->
    for (element in this) {
        val name = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?.get("name")?.takeIf(JsonElement::isJsonPrimitive)?.asString
        if (name == null || names.none { name.equals(it, ignoreCase = true) }) result.add(element.deepCopy())
    }
}

internal fun JsonArray.withSpearKillConfigValue(value: JsonObject?): JsonArray {
    if (value == null) return this
    return withoutSpearKillConfigValues(value["name"].asString).also { it.add(value.deepCopy()) }
}
