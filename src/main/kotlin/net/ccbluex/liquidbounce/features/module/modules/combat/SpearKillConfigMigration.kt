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

package net.ccbluex.liquidbounce.features.module.modules.combat

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Values removed from Packet movement that still contribute to top-level canonical settings. */
private data class LegacySpearKillMovementProfile(
    val elytraEnabled: Boolean?,
    val elytraMaxSpeed: Double?,
    val renderPath: Boolean?,
)

/**
 * Canonicalizes every historical SpearKill schema before any child value is deserialized.
 *
 * Canonical records always win. Legacy records are used only to fill an absent canonical value,
 * then removed so the next ordinary save can emit only the current hierarchy.
 */
internal fun migrateLegacySpearKillConfig(jsonObject: JsonObject) {
    val originalValues = jsonObject["value"]?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: return
    val explicitSpeed = originalValues.spearKillConfigValue("Speed")?.numberValue()
    val legacySpeed = originalValues.spearKillConfigValue("MaxSpeed")?.numberValue()
    val explicitSneak = originalValues.spearKillConfigValue("SneakWhileMoving")?.deepCopy()
    val legacyServerSneak = originalValues.spearKillConfigValue("ServerSneak")?.booleanValue()
    val explicitElytra = originalValues.spearKillConfigValue("ElytraWhileMoving")?.deepCopy()

    var values = originalValues
        .canonicalizingSpearKillValue("TargetDistance", "MaxTargetDistance")
        .withoutSpearKillConfigValues("Speed", "MaxSpeed", "ServerSneak", "SneakWhileMoving", "ElytraWhileMoving")

    values.spearKillConfigValue("TargetSource")
        ?.get("value")
        ?.takeIf(JsonElement::isJsonPrimitive)
        ?.takeIf {
            it.asString.equals("LookRay", ignoreCase = true) ||
                it.asString.equals("KillAura", ignoreCase = true)
        }
        ?.let { values.spearKillConfigValue("TargetSource")?.addProperty("value", "Crosshair") }

    var movement = values.spearKillConfigValue("Movement")
    if (movement == null && (explicitSpeed != null || legacySpeed != null)) {
        values = values.withSpearKillConfigValue(spearKillMovementValue())
        movement = values.spearKillConfigValue("Movement")
    }
    val legacyMovement = movement?.let(::migrateSpearKillMovementConfig)
        ?: LegacySpearKillMovementProfile(null, null, null)

    movement?.let { movementValue ->
        val movementValues = movementValue.spearKillConfigValues()
        val storedTargetSpeed = movementValues.spearKillConfigValue("TargetSpeed")
        val migratedTargetSpeed = storedTargetSpeed?.numberValue()
            ?: explicitSpeed
            ?: legacyMovement.elytraMaxSpeed.takeIf { legacyMovement.elytraEnabled == true }
            ?: legacySpeed
        val targetSpeed = when {
            migratedTargetSpeed != null -> spearKillScalarValue(
                "TargetSpeed",
                migratedTargetSpeed.coerceIn(
                    SPEAR_KILL_MIN_TARGET_SPEED.toDouble(),
                    SPEAR_KILL_EXPERIMENTAL_MAX_SPEED.toDouble(),
                ),
            )
            storedTargetSpeed != null -> storedTargetSpeed.deepCopy()
            else -> null
        }
        movementValue.add("value", movementValues.withSpearKillConfigValue(targetSpeed))
    }

    val sneak = explicitSneak ?: legacyServerSneak?.let { enabled ->
        spearKillScalarValue("SneakWhileMoving", if (enabled) "Packet" else "None")
    }
    values = values.withSpearKillConfigValue(sneak)

    val elytra = explicitElytra ?: legacyMovement.elytraEnabled?.let { enabled ->
        spearKillScalarValue("ElytraWhileMoving", if (enabled) "Packet" else "None")
    }
    values = values.withSpearKillConfigValue(elytra)

    migrateSpearKillRenderPath(values, legacyMovement.renderPath)
    jsonObject.add("value", values)
}

private fun spearKillMovementValue() = JsonObject().apply {
    addProperty("name", "Movement")
    addProperty("active", "Packet")
    add("value", JsonArray())
    add("choices", JsonObject().apply {
        add("Motion", spearKillChoice("Motion"))
        add("Packet", spearKillChoice("Packet"))
    })
}

private fun migrateSpearKillMovementConfig(movement: JsonObject): LegacySpearKillMovementProfile {
    if (!movement.has("choices")) {
        val active = canonicalSpearKillMovementMode(
            movement["value"]?.takeIf(JsonElement::isJsonPrimitive)?.asString,
        )
        movement.addProperty("active", active)
        movement.add("value", JsonArray())
        movement.add("choices", JsonObject().apply {
            add("Motion", spearKillChoice("Motion"))
            add("Packet", spearKillChoice("Packet"))
        })
    }

    movement.addProperty(
        "active",
        canonicalSpearKillMovementMode(
            movement["active"]?.takeIf(JsonElement::isJsonPrimitive)?.asString,
        ),
    )
    val oldChoices = movement["choices"]?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: JsonObject()
    val motion = oldChoices.spearKillMovementChoice("Motion") ?: spearKillChoice("Motion")
    val packet = oldChoices.spearKillMovementChoice("Packet", "PacketBoot", "Packet-Boot")
        ?: spearKillChoice("Packet")
    motion.addProperty("name", "Motion")
    packet.addProperty("name", "Packet")
    movement.add("choices", JsonObject().apply {
        add("Motion", motion)
        add("Packet", packet)
    })

    motion.add(
        "value",
        motion.spearKillConfigValues()
            .canonicalizingSpearKillValue("StepDistance", "StepsPerTeleport", "StepLimit"),
    )
    return migrateSpearKillPacketConfig(packet)
}

private fun migrateSpearKillPacketConfig(packet: JsonObject): LegacySpearKillMovementProfile {
    val originalValues = packet.spearKillConfigValues()
    val legacyElytra = originalValues.spearKillConfigValue("Elytra")
    val legacyElytraValues = legacyElytra?.spearKillConfigValues()
    val elytraEnabled = legacyElytraValues?.spearKillConfigValue("Enabled")?.booleanValue()
    val elytraMaxSpeed = legacyElytraValues?.spearKillConfigValue("MaxSpeed")?.numberValue()

    val legacyAStar = originalValues.spearKillConfigValue("AStar")
    val legacyAStarValues = legacyAStar?.spearKillConfigValues() ?: JsonArray()
    val routingRecord = originalValues.spearKillConfigValue("Routing")
    val routingChoices = routingRecord?.get("choices")
        ?.takeIf(JsonElement::isJsonObject)
        ?.asJsonObject
    val routingIsCanonical = routingChoices != null
    val routingTag = canonicalSpearKillRoutingMode(
        if (routingIsCanonical) {
            routingRecord?.get("active")?.takeIf(JsonElement::isJsonPrimitive)?.asString
        } else {
            routingRecord?.get("value")?.takeIf(JsonElement::isJsonPrimitive)?.asString
                ?: if (legacyAStarValues.spearKillConfigValue("Enabled")?.booleanValue() == true) {
                    "AStar"
                } else {
                    "Direct"
                }
        },
    )
    val canonicalAStarValues = if (routingIsCanonical) {
        routingChoices.spearKillMovementChoiceValues("AStar", "Adaptive")
    } else {
        legacyAStarValues
    }
    val renderPath = canonicalAStarValues.spearKillConfigValue("RenderPath")?.booleanValue()
        ?: legacyAStarValues.spearKillConfigValue("RenderPath")?.booleanValue()

    val explicitWait = originalValues.spearKillConfigValue("StepDelay")?.deepCopy()
        ?: originalValues.spearKillConfigValue("WaitBeforeTeleport")?.deepCopy()
    val packetWait = originalValues.spearKillConfigValue("WaitTicks")?.deepCopy()
    val aStarWait = legacyAStarValues.spearKillConfigValue("WaitTicks")?.deepCopy()
    val selectedWait = explicitWait ?: if (routingTag == "AStar") aStarWait ?: packetWait else packetWait ?: aStarWait
    selectedWait?.addProperty("name", "StepDelay")

    var retainedValues = originalValues
        .canonicalizingSpearKillValue("StepDistance", "StepsPerTeleport", "StepLimit")
        .withoutSpearKillConfigValues(
            "StepDelay", "WaitBeforeTeleport", "WaitTicks", "Routing", "Elytra", "AStar",
        )
        .withSpearKillConfigValue(selectedWait)
    retainedValues = retainedValues.withSpearKillConfigValue(
        spearKillRoutingValue(
            active = routingTag,
            aStarValues = canonicalAStarValues.withoutSpearKillConfigValues(
                "Enabled",
                "WaitTicks",
                "RenderPath",
            ),
            sourceChoices = routingChoices,
        ),
    )
    packet.add("value", retainedValues)

    return LegacySpearKillMovementProfile(elytraEnabled, elytraMaxSpeed, renderPath)
}

private fun migrateSpearKillRenderPath(values: JsonArray, legacyRenderPath: Boolean?) {
    if (legacyRenderPath == null) return

    val preview = values.spearKillConfigValue("Preview") ?: spearKillChoice("Preview").also(values::add)
    val previewValues = preview.spearKillConfigValues()
    if (previewValues.spearKillConfigValue("RenderPath") != null) return

    preview.add(
        "value",
        previewValues.withSpearKillConfigValue(spearKillScalarValue("RenderPath", legacyRenderPath)),
    )
}

private fun spearKillRoutingValue(
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

private fun spearKillChoice(name: String, values: JsonArray = JsonArray()) = JsonObject().apply {
    addProperty("name", name)
    add("value", values)
}

private fun spearKillScalarValue(name: String, value: Any) = JsonObject().apply {
    addProperty("name", name)
    when (value) {
        is Boolean -> addProperty("value", value)
        is Number -> addProperty("value", value)
        else -> addProperty("value", value.toString())
    }
}

private fun canonicalSpearKillMovementMode(value: String?): String = when {
    value.equals("Packet", ignoreCase = true) ||
        value.equals("PacketBoot", ignoreCase = true) ||
        value.equals("Packet-Boot", ignoreCase = true) -> "Packet"
    else -> "Motion"
}

private fun canonicalSpearKillRoutingMode(value: String?): String = when {
    value.equals("Instant", ignoreCase = true) -> "Instant"
    value.equals("AStar", ignoreCase = true) || value.equals("Adaptive", ignoreCase = true) -> "AStar"
    value.equals("NetworkOptimized", ignoreCase = true) ||
        value.equals("Network", ignoreCase = true) ||
        value.equals("LagOptimized", ignoreCase = true) ||
        value.equals("Network-Optimized", ignoreCase = true) -> "NetworkOptimized"
    else -> "Direct"
}

private fun JsonObject.spearKillConfigValues(): JsonArray =
    get("value")?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()

private fun JsonObject.spearKillMovementChoice(name: String, vararg aliases: String): JsonObject? {
    val acceptedNames = setOf(name, *aliases)
    return entrySet().firstOrNull { (choiceName, _) ->
        acceptedNames.any { choiceName.equals(it, ignoreCase = true) }
    }?.value?.takeIf(JsonElement::isJsonObject)?.asJsonObject
}

private fun JsonObject?.spearKillMovementChoiceValues(
    name: String,
    vararg aliases: String,
): JsonArray = this?.spearKillMovementChoice(name, *aliases)?.spearKillConfigValues() ?: JsonArray()

private fun JsonObject?.spearKillNetworkOptimizedChoiceValues(): JsonArray = spearKillMovementChoiceValues(
    "NetworkOptimized",
    "Network",
    "LagOptimized",
    "Network-Optimized",
)

private fun JsonArray.spearKillConfigValue(name: String): JsonObject? = firstOrNull { element ->
    element.isJsonObject && element.asJsonObject["name"]
        ?.takeIf(JsonElement::isJsonPrimitive)
        ?.asString
        ?.equals(name, ignoreCase = true) == true
}?.asJsonObject

private fun JsonObject.booleanValue(): Boolean? =
    get("value")?.takeIf(JsonElement::isJsonPrimitive)?.asBoolean

private fun JsonObject.numberValue(): Double? =
    get("value")?.takeIf(JsonElement::isJsonPrimitive)?.asDouble

private fun JsonArray.canonicalizingSpearKillValue(canonical: String, vararg aliases: String): JsonArray {
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

private fun JsonArray.withoutSpearKillConfigValues(vararg names: String): JsonArray = JsonArray().also { result ->
    for (element in this) {
        val name = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?.get("name")?.takeIf(JsonElement::isJsonPrimitive)?.asString
        if (name == null || names.none { name.equals(it, ignoreCase = true) }) {
            result.add(element.deepCopy())
        }
    }
}

private fun JsonArray.withSpearKillConfigValue(value: JsonObject?): JsonArray {
    if (value == null) return this
    return withoutSpearKillConfigValues(value["name"].asString).also { it.add(value.deepCopy()) }
}
