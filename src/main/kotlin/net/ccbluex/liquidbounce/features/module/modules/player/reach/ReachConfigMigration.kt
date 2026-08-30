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
    if (existingReach == null) modules.replace(legacy, reach)
    legacyModules
        .filterNot { it === legacy && existingReach == null }
        .forEach(modules::remove)
}

private fun createReachFrom(legacy: JsonObject): JsonObject = JsonObject().apply {
    addProperty(NAME_KEY, REACH_NAME)
    add(VALUE_KEY, JsonArray().apply {
        add(configValue(ENABLED_NAME, legacy.enabledState() ?: false))
        MODULE_INHERITED_METADATA
            .mapNotNull(legacy::configValue)
            .forEach { add(it.deepCopy()) }
    })
}

private fun mergeLegacyHit(reach: JsonObject, legacy: JsonObject) {
    val reachValues = reach.ensureValues()
    val legacyEnabled = legacy.enabledState()
    val migratedValues = migrateLegacyHitValues(legacy)
    val hit = reachValues.namedObject(HIT_NAME)
    if (hit == null) {
        reachValues.add(legacyHitGroup(legacyEnabled, migratedValues))
        return
    }
    val hitValues = hit.ensureValues()
    legacyEnabled?.let { hitValues.putConfigValue(ENABLED_NAME, it) }
    migratedValues.forEach { legacyValue ->
        val name = legacyValue.stringProperty(NAME_KEY) ?: return@forEach
        if (hitValues.namedObject(name) == null) hitValues.add(legacyValue)
    }
}

private fun legacyHitGroup(enabled: Boolean?, values: List<JsonObject>) = JsonObject().apply {
    addProperty(NAME_KEY, HIT_NAME)
    add(VALUE_KEY, JsonArray().apply {
        add(configValue(ENABLED_NAME, enabled ?: false))
        values.forEach(::add)
    })
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
    val copiedValues = legacy.arrayOrNull(VALUE_KEY)
        ?.objectValues()
        ?.filterNot { it.stringProperty(NAME_KEY) in MODULE_METADATA }
        ?.map(JsonObject::deepCopy)
        ?: return emptyList()
    val mode = copiedValues.firstNamed(MODE_NAME)
    val hasFlatModeSetting = copiedValues.any { it.stringProperty(NAME_KEY) in LEGACY_MODE_SETTING_NAMES }
    if (mode == null && !hasFlatModeSetting) return copiedValues
    val valuesByName = copiedValues.associateBy { it.stringProperty(NAME_KEY).orEmpty() }
    val canonicalMode = canonicalModeGroup(mode ?: legacyModeDefault())
    migrateFlatModeSettings(valuesByName, canonicalMode.getAsJsonObject(CHOICES_KEY))
    return copiedValues
        .filterNot { it.stringProperty(NAME_KEY) in LEGACY_MODE_SETTING_NAMES }
        .plus(canonicalMode)
}
