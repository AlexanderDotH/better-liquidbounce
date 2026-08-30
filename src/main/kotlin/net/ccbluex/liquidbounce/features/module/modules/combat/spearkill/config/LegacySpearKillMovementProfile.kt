/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config


import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Values removed from Packet movement that still contribute to top-level canonical settings. */
internal data class LegacySpearKillMovementProfile(
    val elytraEnabled: Boolean?,
    val elytraMaxSpeed: Double?,
    val renderPath: Boolean?,
)

private data class LegacySpearKillPacketRouting(
    val tag: String,
    val canonicalAStarValues: JsonArray,
    val legacyAStarValues: JsonArray,
    val sourceChoices: JsonObject?,
    val renderPath: Boolean?,
)

internal fun spearKillMovementValue() = JsonObject().apply {
    addProperty("name", "Movement")
    addProperty("active", "Packet")
    add("value", JsonArray())
    add("choices", JsonObject().apply {
        add("Motion", spearKillChoice("Motion"))
        add("Packet", spearKillChoice("Packet"))
    })
}

internal fun migrateSpearKillMovementConfig(movement: JsonObject): LegacySpearKillMovementProfile {
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
    val legacyElytraValues = originalValues.spearKillConfigValue("Elytra")?.spearKillConfigValues()
    val elytraEnabled = legacyElytraValues?.spearKillConfigValue("Enabled")?.booleanValue()
    val elytraMaxSpeed = legacyElytraValues?.spearKillConfigValue("MaxSpeed")?.numberValue()
    val routing = resolveLegacySpearKillPacketRouting(originalValues)
    val selectedWait = selectLegacySpearKillStepDelay(originalValues, routing)
    packet.add("value", buildCanonicalSpearKillPacketValues(originalValues, routing, selectedWait))
    return LegacySpearKillMovementProfile(elytraEnabled, elytraMaxSpeed, routing.renderPath)
}

private fun resolveLegacySpearKillPacketRouting(originalValues: JsonArray): LegacySpearKillPacketRouting {
    val legacyAStarValues = originalValues.spearKillConfigValue("AStar")?.spearKillConfigValues() ?: JsonArray()
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
                ?: if (legacyAStarValues.spearKillConfigValue("Enabled")?.booleanValue() == true) "AStar" else "Direct"
        },
    )
    val canonicalAStarValues = if (routingIsCanonical) {
        routingChoices.spearKillMovementChoiceValues("AStar", "Adaptive")
    } else {
        legacyAStarValues
    }
    val renderPath = canonicalAStarValues.spearKillConfigValue("RenderPath")?.booleanValue()
        ?: legacyAStarValues.spearKillConfigValue("RenderPath")?.booleanValue()
    return LegacySpearKillPacketRouting(
        routingTag,
        canonicalAStarValues,
        legacyAStarValues,
        routingChoices,
        renderPath,
    )
}

private fun selectLegacySpearKillStepDelay(
    originalValues: JsonArray,
    routing: LegacySpearKillPacketRouting,
): JsonObject? {
    val explicitWait = originalValues.spearKillConfigValue("StepDelay")?.deepCopy()
        ?: originalValues.spearKillConfigValue("WaitBeforeTeleport")?.deepCopy()
    val packetWait = originalValues.spearKillConfigValue("WaitTicks")?.deepCopy()
    val aStarWait = routing.legacyAStarValues.spearKillConfigValue("WaitTicks")?.deepCopy()
    val selectedWait = explicitWait ?: if (routing.tag == "AStar") aStarWait ?: packetWait else packetWait ?: aStarWait
    selectedWait?.addProperty("name", "StepDelay")
    return selectedWait
}

private fun buildCanonicalSpearKillPacketValues(
    originalValues: JsonArray,
    routing: LegacySpearKillPacketRouting,
    selectedWait: JsonObject?,
): JsonArray {
    val retainedValues = originalValues
        .canonicalizingSpearKillValue("StepDistance", "StepsPerTeleport", "StepLimit")
        .withoutSpearKillConfigValues(
            "StepDelay", "WaitBeforeTeleport", "WaitTicks", "Routing", "Elytra", "AStar",
        )
        .withSpearKillConfigValue(selectedWait)
    return retainedValues.withSpearKillConfigValue(
        spearKillRoutingValue(
            active = routing.tag,
            aStarValues = routing.canonicalAStarValues.withoutSpearKillConfigValues(
                "Enabled",
                "WaitTicks",
                "RenderPath",
            ),
            sourceChoices = routing.sourceChoices,
        ),
    )
}
