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
package net.ccbluex.liquidbounce.features.module.modules.combat

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Migrates retired movement choices without changing current Direct, AStar, or Instant values. */
internal fun migrateLegacyMaceKillConfig(root: JsonObject) {
    val modules = root.getAsJsonArray("value") ?: return
    val maceKill = modules.findMaceKillNamedObject("MaceKill") ?: return
    val values = maceKill.getAsJsonArray("value") ?: return

    values.asSequence()
        .filter { element ->
            element.isJsonObject && element.asJsonObject.get("name")?.asString in MACE_KILL_REMOVED_SETTINGS
        }
        .toList()
        .forEach(values::remove)

    val movement = values.findMaceKillNamedObject("Movement") ?: return
    val packet = movement.getAsJsonObject("choices")?.getAsJsonObject("Packet") ?: return
    val routing = packet.getAsJsonArray("value")?.findMaceKillNamedObject("Routing") ?: return
    val choices = routing.getAsJsonObject("choices")
    val replacement = routing.get("active")?.asString.maceKillReplacementRoutingMode()
    if (replacement != null) {
        routing.addProperty("active", replacement)
        if (choices != null && !choices.has(replacement)) {
            choices.add(replacement, emptyMaceKillRoutingChoice(replacement))
        }
    }

    choices?.keySet()
        ?.filter(::isRetiredMaceKillRoutingChoice)
        ?.toList()
        ?.forEach(choices::remove)
}

private fun JsonArray.findMaceKillNamedObject(name: String): JsonObject? = asSequence()
    .mapNotNull { it.takeIf { element -> element.isJsonObject }?.asJsonObject }
    .firstOrNull { it.get("name")?.asString == name }

private val MACE_KILL_REMOVED_SETTINGS = setOf("SneakWhileMoving", "ElytraWhileMoving")

private fun String?.maceKillReplacementRoutingMode(): String? = when {
    equals("ClipReach", ignoreCase = true) -> "Instant"
    MACE_KILL_RETIRED_NETWORK_MODES.any { equals(it, ignoreCase = true) } -> "AStar"
    else -> null
}

private fun isRetiredMaceKillRoutingChoice(name: String): Boolean =
    name.equals("ClipReach", ignoreCase = true) ||
        MACE_KILL_RETIRED_NETWORK_MODES.any { name.equals(it, ignoreCase = true) }

private fun emptyMaceKillRoutingChoice(name: String) = JsonObject().apply {
    addProperty("name", name)
    add("value", JsonArray())
}

private val MACE_KILL_RETIRED_NETWORK_MODES = setOf(
    "NetworkOptimized",
    "Network",
    "LagOptimized",
    "Network-Optimized",
)
