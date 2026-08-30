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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config


import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

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
    migrateSpearKillTargetSource(values)

    var movement = values.spearKillConfigValue("Movement")
    if (movement == null && (explicitSpeed != null || legacySpeed != null)) {
        values = values.withSpearKillConfigValue(spearKillMovementValue())
        movement = values.spearKillConfigValue("Movement")
    }
    val legacyMovement = movement?.let(::migrateSpearKillMovementConfig)
        ?: LegacySpearKillMovementProfile(null, null, null)
    movement?.let { migrateSpearKillTargetSpeed(it, explicitSpeed, legacySpeed, legacyMovement) }
    values = migrateSpearKillTopLevelMovementSettings(
        values,
        explicitSneak,
        legacyServerSneak,
        explicitElytra,
        legacyMovement,
    )

    migrateSpearKillRenderPath(values, legacyMovement.renderPath)
    jsonObject.add("value", values)
}

private fun migrateSpearKillTargetSource(values: JsonArray) {
    val source = values.spearKillConfigValue("TargetSource") ?: return
    val tag = source["value"]?.takeIf(JsonElement::isJsonPrimitive)?.asString ?: return
    if (tag.equals("LookRay", ignoreCase = true) || tag.equals("KillAura", ignoreCase = true)) {
        source.addProperty("value", "Crosshair")
    }
}

private fun migrateSpearKillTargetSpeed(
    movement: JsonObject,
    explicitSpeed: Double?,
    legacySpeed: Double?,
    legacyMovement: LegacySpearKillMovementProfile,
) {
    val values = movement.spearKillConfigValues()
    val storedTargetSpeed = values.spearKillConfigValue("TargetSpeed")
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
    movement.add("value", values.withSpearKillConfigValue(targetSpeed))
}

private fun migrateSpearKillTopLevelMovementSettings(
    values: JsonArray,
    explicitSneak: JsonObject?,
    legacyServerSneak: Boolean?,
    explicitElytra: JsonObject?,
    legacyMovement: LegacySpearKillMovementProfile,
): JsonArray {
    val sneak = explicitSneak ?: legacyServerSneak?.let { enabled ->
        spearKillScalarValue("SneakWhileMoving", if (enabled) "Packet" else "None")
    }
    val withSneak = values.withSpearKillConfigValue(sneak)
    val elytra = explicitElytra ?: legacyMovement.elytraEnabled?.let { enabled ->
        spearKillScalarValue("ElytraWhileMoving", if (enabled) "Packet" else "None")
    }
    return withSneak.withSpearKillConfigValue(elytra)
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
