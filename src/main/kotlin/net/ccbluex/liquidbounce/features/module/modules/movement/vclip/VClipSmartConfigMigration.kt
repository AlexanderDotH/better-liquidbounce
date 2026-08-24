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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal fun migrateLegacyVClipBedrockSafety(vClip: JsonObject) {
    val values = vClip["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val canonicalSafety = values.findValue("DoNotClipAroundBedrock")
    val target = values.findValue("Target") ?: return
    val choices = target["choices"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return
    val smart = choices["Smart"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return
    val smartValues = smart["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val legacySafety = smartValues.findValue("DoNotClipAroundBedrock") ?: return

    smartValues.remove(legacySafety)
    if (canonicalSafety == null) {
        values.add(legacySafety.deepCopy())
    }
}

internal fun migrateLegacyVClipSmartScanDistance(smartMode: JsonObject) {
    val values = smartMode["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val scanDistance = values.findValue("ScanDistance")
    val legacyMaxDistance = values.findValue("MaxDistance") ?: return
    val legacyValue = legacyMaxDistance["value"] ?: return

    values.remove(legacyMaxDistance)
    if (scanDistance != null) {
        return
    }

    values.add(JsonObject().apply {
        addProperty("name", "ScanDistance")
        add("value", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", "Enabled")
                addProperty("value", true)
            })
            add(JsonObject().apply {
                addProperty("name", "MaxDistance")
                add("value", legacyValue.deepCopy())
            })
        })
    })
}

private fun JsonArray.findValue(name: String): JsonObject? =
    firstOrNull { element ->
        element.isJsonObject && element.asJsonObject["name"]?.asString == name
    }?.asJsonObject
