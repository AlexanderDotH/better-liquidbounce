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
package net.ccbluex.liquidbounce.features.global

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Adds the Trial target once to legacy, persisted global target settings. */
internal object TrialTargetConfigMigration {

    const val MARKER_NAME = "TrialTargetMigrationV1"

    private const val HOSTILE_TARGET = "Hostile"
    private const val TRIAL_TARGET = "Trial"
    private const val INTEROP_METADATA = "valueType"

    private val targetGroupNames = setOf("Targets", "Enemies")
    private val migratedSettings = setOf("Combat", "Visual")

    /**
     * Mutates a file-serialized Settings or Targets group before value deserialization.
     * Interop payloads are deliberately ignored because their hidden marker is omitted.
     *
     * @return whether the migration marker was added
     */
    fun migrateFileSettings(settings: JsonObject): Boolean {
        val targets = settings.findTargetsGroup() ?: return false
        if (settings.has(INTEROP_METADATA) || targets.has(INTEROP_METADATA)) return false

        val targetSettings = targets.jsonArrayOrNull("value") ?: return false
        if (targetSettings.findNamedSetting(MARKER_NAME) != null) return false

        val targetLists = migratedSettings.mapNotNull { settingName ->
            targetSettings.findNamedSetting(settingName)?.jsonArrayOrNull("value")
        }
        if (targetLists.isEmpty()) return false

        targetLists.forEach { it.enableTrialForLegacyHostile() }
        targetSettings.add(migrationMarker())
        return true
    }

    private fun JsonObject.findTargetsGroup(): JsonObject? {
        if (stringOrNull("name") in targetGroupNames) return this
        return jsonArrayOrNull("value")?.findNamedSetting(targetGroupNames)
    }

    private fun JsonArray.enableTrialForLegacyHostile() {
        if (!containsWireValue(HOSTILE_TARGET) || containsWireValue(TRIAL_TARGET)) return
        add(TRIAL_TARGET)
    }

    private fun JsonArray.containsWireValue(value: String): Boolean = any { element ->
        element.isJsonPrimitive && element.asJsonPrimitive.isString && element.asString == value
    }

    private fun migrationMarker() = JsonObject().apply {
        addProperty("name", MARKER_NAME)
        addProperty("value", true)
    }
}

private fun JsonObject.jsonArrayOrNull(name: String): JsonArray? = get(name)
    ?.takeIf(JsonElement::isJsonArray)
    ?.asJsonArray

private fun JsonObject.stringOrNull(name: String): String? = get(name)
    ?.takeIf(JsonElement::isJsonPrimitive)
    ?.asJsonPrimitive
    ?.takeIf { it.isString }
    ?.asString

private fun JsonArray.findNamedSetting(name: String): JsonObject? = findNamedSetting(setOf(name))

private fun JsonArray.findNamedSetting(names: Set<String>): JsonObject? = asSequence()
    .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
    .firstOrNull { it.stringOrNull("name") in names }
