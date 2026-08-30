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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import java.io.InputStreamReader

internal fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name } as Value<*>

internal fun ValueGroup.group(name: String): ValueGroup = inner.single { it.name == name } as ValueGroup

internal fun baseFinderConfig(minimumConfidence: Int, highSensitivity: Boolean? = null) = JsonObject().apply {
    addProperty("name", "BaseFinder")
    add("value", JsonArray().apply {
        add(storedBaseFinderValue("MinimumConfidence", minimumConfidence))
        highSensitivity?.let { add(storedBaseFinderValue("HighSensitivity", it)) }
    })
}

internal fun storedBaseFinderValue(name: String, value: Any) = JsonObject().apply {
    addProperty("name", name)
    when (value) {
        is Boolean -> addProperty("value", value)
        is Int -> addProperty("value", value)
        is String -> addProperty("value", value)
    }
}

internal fun storedBaseFinderValue(config: JsonObject, name: String) = config.getAsJsonArray("value")
    .map { it.asJsonObject }
    .single { it["name"].asString == name }
    .get("value")

internal fun nestedBaseFinderValue(config: JsonObject, group: String, name: String) =
    storedBaseFinderValue(config, group).asJsonArray
        .map { it.asJsonObject }
        .single { it["name"].asString == name }
        .get("value")

internal fun nestedBaseFinderValue(
    config: JsonObject,
    parentGroup: String,
    group: String,
    name: String,
) = nestedBaseFinderValue(config, parentGroup, group).asJsonArray
    .map { it.asJsonObject }
    .single { it["name"].asString == name }
    .get("value")

internal fun readBaseFinderLocale(locale: String): JsonObject {
    val resource = checkNotNull(
        ModuleBaseFinderTest::class.java.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
    )
    return resource.use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }
}
