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
@file:Suppress("TooManyFunctions")

package net.ccbluex.liquidbounce.features.module.modules.player.reach

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Moves the retired SuperHit module into Reach without overwriting canonical Reach settings. */
internal fun migrateLegacyReachConfig(root: JsonObject) {
    val modules = root.arrayOrNull(VALUE_KEY) ?: return
    val legacyModules = modules.namedObjects(SUPER_HIT_NAME)
    if (legacyModules.isEmpty()) return

    val legacy = legacyModules.first()
    val existingReach = modules.namedObject(REACH_NAME)
    val reach = existingReach ?: createReachFrom(legacy)
    mergeLegacyHit(reach, legacy)
    mergeReachEnabled(reach, legacy, existingReach != null)

    if (existingReach == null) {
        modules.replace(legacy, reach)
    }
    legacyModules
        .filterNot { it === legacy && existingReach == null }
        .forEach { modules.remove(it) }
}

private fun createReachFrom(legacy: JsonObject): JsonObject = JsonObject().apply {
    addProperty(NAME_KEY, REACH_NAME)
    add(VALUE_KEY, JsonArray().apply {
        add(configValue(ENABLED_NAME, legacy.enabledState() ?: false))
        MODULE_INHERITED_METADATA
            .mapNotNull { name -> legacy.configValue(name) }
            .forEach { add(it.deepCopy()) }
    })
}

private fun mergeLegacyHit(reach: JsonObject, legacy: JsonObject) {
    val reachValues = reach.ensureValues()
    val legacyEnabled = legacy.enabledState()
    val migratedValues = migrateLegacyHitValues(legacy)
    val hit = reachValues.namedObject(HIT_NAME)

    if (hit == null) {
        reachValues.add(JsonObject().apply {
            addProperty(NAME_KEY, HIT_NAME)
            add(VALUE_KEY, JsonArray().apply {
                add(configValue(ENABLED_NAME, legacyEnabled ?: false))
                migratedValues.forEach { add(it) }
            })
        })
        return
    }

    val hitValues = hit.ensureValues()
    legacyEnabled?.let { hitValues.putConfigValue(ENABLED_NAME, it) }
    migratedValues.forEach { legacyValue ->
        val name = legacyValue.stringProperty(NAME_KEY) ?: return@forEach
        if (hitValues.namedObject(name) == null) {
            hitValues.add(legacyValue)
        }
    }
}

private fun mergeReachEnabled(reach: JsonObject, legacy: JsonObject, reachExisted: Boolean) {
    val reachValues = reach.ensureValues()
    val reachEnabled = reach.enabledState()
    val legacyEnabled = legacy.enabledState()

    if (!reachExisted) {
        reachValues.putConfigValue(ENABLED_NAME, legacyEnabled ?: false)
        return
    }

    when {
        reachEnabled != null && legacyEnabled != null ->
            reachValues.putConfigValue(ENABLED_NAME, reachEnabled || legacyEnabled)
        reachEnabled == null && legacyEnabled == true ->
            reachValues.putConfigValue(ENABLED_NAME, true)
    }
}

private fun migrateLegacyHitValues(legacy: JsonObject): List<JsonObject> {
    val legacyValues = legacy.arrayOrNull(VALUE_KEY) ?: return emptyList()
    val copiedValues = legacyValues.objectValues()
        .filterNot { value -> value.stringProperty(NAME_KEY) in MODULE_METADATA }
        .map(JsonObject::deepCopy)
    val mode = copiedValues.firstNamed(MODE_NAME)
    val hasFlatModeSetting = copiedValues.any { value ->
        value.stringProperty(NAME_KEY) in LEGACY_MODE_SETTING_NAMES
    }
    if (mode == null && !hasFlatModeSetting) return copiedValues

    val valuesByName = copiedValues.associateBy { it.stringProperty(NAME_KEY).orEmpty() }
    val canonicalMode = canonicalModeGroup(mode ?: legacyModeDefault())
    migrateFlatModeSettings(valuesByName, canonicalMode.getAsJsonObject(CHOICES_KEY))

    return copiedValues
        .filterNot { value -> value.stringProperty(NAME_KEY) in LEGACY_MODE_SETTING_NAMES }
        .plus(canonicalMode)
}

private fun canonicalModeGroup(storedMode: JsonObject): JsonObject {
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
        MODE_ALIASES.getValue(canonicalName).any { alias -> equals(alias, ignoreCase = true) }

private fun migrateFlatModeSettings(valuesByName: Map<String, JsonObject>, choices: JsonObject) {
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

private fun JsonObject.enabledState(): Boolean? {
    val element = configValue(ENABLED_NAME)?.get(VALUE_KEY) ?: return null
    return element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
}

private fun JsonObject.configValue(name: String): JsonObject? = arrayOrNull(VALUE_KEY)?.namedObject(name)

private fun JsonObject.ensureValues(): JsonArray = arrayOrNull(VALUE_KEY) ?: JsonArray().also { add(VALUE_KEY, it) }

private fun JsonArray.putConfigValue(name: String, value: Boolean) {
    val stored = namedObject(name)
    if (stored == null) {
        add(configValue(name, value))
        return
    }
    stored.addProperty(VALUE_KEY, value)
}

private fun JsonArray.replace(old: JsonObject, replacement: JsonObject) {
    for (index in 0 until size()) {
        if (get(index) === old) {
            set(index, replacement)
            return
        }
    }
}

private fun JsonArray.namedObjects(name: String): List<JsonObject> = objectValues()
    .filter { value -> value.stringProperty(NAME_KEY) == name }

private fun JsonArray.namedObject(name: String): JsonObject? = objectValues().firstNamed(name)

private fun Iterable<JsonObject>.firstNamed(name: String): JsonObject? =
    firstOrNull { value -> value.stringProperty(NAME_KEY) == name }

private fun JsonArray.objectValues(): List<JsonObject> = mapNotNull { element ->
    element.takeIf { it.isJsonObject }?.asJsonObject
}

private fun JsonObject.arrayOrNull(name: String): JsonArray? = get(name)
    ?.takeIf { it.isJsonArray }
    ?.asJsonArray

private fun JsonObject.objectOrNull(name: String): JsonObject? = get(name)
    ?.takeIf { it.isJsonObject }
    ?.asJsonObject

private fun JsonObject.stringProperty(name: String): String? = get(name)
    ?.takeIf { element -> element.isJsonPrimitive && element.asJsonPrimitive.isString }
    ?.asString

private fun configValue(name: String, value: Boolean) = JsonObject().apply {
    addProperty(NAME_KEY, name)
    addProperty(VALUE_KEY, value)
}

private fun emptyChoice(name: String) = JsonObject().apply {
    addProperty(NAME_KEY, name)
    add(VALUE_KEY, JsonArray())
}

private fun legacyModeDefault() = JsonObject().apply {
    addProperty(NAME_KEY, MODE_NAME)
    addProperty(VALUE_KEY, PACKET_MODE)
}

private data class LegacyModeSetting(
    val legacyName: String,
    val choiceName: String,
    val settingName: String,
)

private const val NAME_KEY = "name"
private const val VALUE_KEY = "value"
private const val ACTIVE_KEY = "active"
private const val CHOICES_KEY = "choices"
private const val REACH_NAME = "Reach"
private const val SUPER_HIT_NAME = "SuperHit"
private const val HIT_NAME = "Hit"
private const val ENABLED_NAME = "Enabled"
private const val MODE_NAME = "Mode"
private const val PACKET_MODE = "Packet"

private val MODULE_METADATA = setOf(ENABLED_NAME, "Bind", "Hidden")
private val MODULE_INHERITED_METADATA = listOf("Bind", "Hidden")
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
private val LEGACY_MODE_SETTING_NAMES = LEGACY_MODE_SETTINGS.mapTo(mutableSetOf(MODE_NAME)) { it.legacyName }
