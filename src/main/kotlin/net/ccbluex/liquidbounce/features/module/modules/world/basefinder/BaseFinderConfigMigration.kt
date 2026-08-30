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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import com.google.gson.JsonObject

internal fun migrateBaseFinderSettings(jsonObject: JsonObject) {
    migrateLegacyBaseFinderSensitivity(jsonObject)
    migrateBaseFinderGroupedSettings(jsonObject)
}

internal fun migrateLegacyBaseFinderSensitivity(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val valuesByName = storedValues
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .associateBy { it["name"]?.asString.orEmpty() }
    if ("HighSensitivity" in valuesByName) return

    val legacyMinimumConfidence = valuesByName["MinimumConfidence"] ?: return
    val storedConfidence = legacyMinimumConfidence["value"]
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asInt
        ?: return
    if (storedConfidence == LEGACY_BASE_FINDER_MINIMUM_CONFIDENCE) {
        legacyMinimumConfidence.addProperty("value", 0)
    }
}

/**
 * Moves flat legacy BaseFinder values into Evidence / Evidence.SeedMismatch / Alerts groups,
 * and folds the old root SeedCompare / SeedMismatch groups and Evidence.SeedMismatch toggle into the nested group.
 * Obsolete Performance / SeedMismatch tuning knobs are dropped (hardcoded defaults now).
 */
internal fun migrateBaseFinderGroupedSettings(jsonObject: JsonObject) {
    val root = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    BaseFinderSettingsMigrator(root).migrateLegacyLayout()
}

private const val LEGACY_BASE_FINDER_MINIMUM_CONFIDENCE = 65
