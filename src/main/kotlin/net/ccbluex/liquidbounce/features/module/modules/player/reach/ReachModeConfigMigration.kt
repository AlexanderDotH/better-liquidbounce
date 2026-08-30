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

internal fun canonicalModeGroup(storedMode: JsonObject): JsonObject {
    val active = storedMode.stringProperty(ACTIVE_KEY)
        ?: storedMode.stringProperty(VALUE_KEY)
        ?: PACKET_MODE
    val sourceChoices = storedMode.objectOrNull(CHOICES_KEY)
    return storedMode.deepCopy().apply {
        addProperty(NAME_KEY, MODE_NAME)
        addProperty(ACTIVE_KEY, canonicalModeName(active))
        add(VALUE_KEY, arrayOrNull(VALUE_KEY)?.deepCopy() ?: JsonArray())
        add(CHOICES_KEY, canonicalChoices(sourceChoices))
    }
}

private fun canonicalChoices(source: JsonObject?): JsonObject = JsonObject().apply {
    MODE_NAMES.forEach { canonicalName ->
        val sourceChoice = source.findCanonicalChoice(canonicalName)
        add(canonicalName, (sourceChoice?.deepCopy() ?: emptyChoice(canonicalName)).apply {
            addProperty(NAME_KEY, canonicalName)
            if (arrayOrNull(VALUE_KEY) == null) add(VALUE_KEY, JsonArray())
        })
    }
}

private fun JsonObject?.findCanonicalChoice(canonicalName: String): JsonObject? = this?.let { choices ->
    choices.objectOrNull(canonicalName) ?: choices.entrySet()
        .firstOrNull { (storedName, value) ->
            value.isJsonObject && storedName.matchesModeName(canonicalName)
        }
        ?.value
        ?.asJsonObject
}

private fun String.matchesModeName(canonicalName: String): Boolean =
    equals(canonicalName, ignoreCase = true) ||
        MODE_ALIASES.getValue(canonicalName).any { equals(it, ignoreCase = true) }

internal fun migrateFlatModeSettings(valuesByName: Map<String, JsonObject>, choices: JsonObject) {
    LEGACY_MODE_SETTINGS.forEach { migration ->
        val legacySetting = valuesByName[migration.legacyName] ?: return@forEach
        val choiceValues = choices.getAsJsonObject(migration.choiceName).ensureValues()
        if (choiceValues.namedObject(migration.settingName) != null) return@forEach
        choiceValues.add(legacySetting.deepCopy().apply {
            addProperty(NAME_KEY, migration.settingName)
        })
    }
}

private fun canonicalModeName(storedName: String): String = MODE_NAMES.firstOrNull { canonicalName ->
    storedName.equals(canonicalName, ignoreCase = true) ||
        MODE_ALIASES.getValue(canonicalName).any { storedName.equals(it, ignoreCase = true) }
} ?: storedName

internal fun legacyModeDefault() = JsonObject().apply {
    addProperty(NAME_KEY, MODE_NAME)
    addProperty(VALUE_KEY, PACKET_MODE)
}

private fun emptyChoice(name: String) = JsonObject().apply {
    addProperty(NAME_KEY, name)
    add(VALUE_KEY, JsonArray())
}

private data class LegacyModeSetting(
    val legacyName: String,
    val choiceName: String,
    val settingName: String,
)

internal const val ACTIVE_KEY = "active"
internal const val CHOICES_KEY = "choices"
internal const val MODE_NAME = "Mode"
internal const val PACKET_MODE = "Packet"

private val MODE_NAMES = listOf(PACKET_MODE, "AStar", "Adaptive", "Motion", "Pulse", "Sentinel")
private val MODE_ALIASES = mapOf(
    PACKET_MODE to setOf("Direct", "SinglePacket"),
    "AStar" to emptySet(),
    "Adaptive" to emptySet(),
    "Motion" to emptySet(),
    "Pulse" to emptySet(),
    "Sentinel" to setOf("Cubecraft", "CubeCraft", "Cube Craft"),
)
private val LEGACY_MODE_SETTINGS = listOf(
    LegacyModeSetting("StepSize", PACKET_MODE, "StepSize"),
    LegacyModeSetting("StepSize", "Pulse", "StepSize"),
    LegacyModeSetting("AStarMaxCost", "AStar", "MaxCost"),
    LegacyModeSetting("AStarDiagonal", "AStar", "Diagonal"),
    LegacyModeSetting("AdaptiveInitialStep", "Adaptive", "InitialStep"),
    LegacyModeSetting("AdaptiveMinimumStep", "Adaptive", "MinimumStep"),
    LegacyModeSetting("AdaptiveRetries", "Adaptive", "Retries"),
    LegacyModeSetting("AdaptiveVerifyTicks", "Adaptive", "VerifyTicks"),
    LegacyModeSetting("PulseDelay", "Pulse", "Delay"),
    LegacyModeSetting("SentinelStayTicks", "Sentinel", "StayTicks"),
)
internal val LEGACY_MODE_SETTING_NAMES =
    LEGACY_MODE_SETTINGS.mapTo(mutableSetOf(MODE_NAME)) { it.legacyName }
