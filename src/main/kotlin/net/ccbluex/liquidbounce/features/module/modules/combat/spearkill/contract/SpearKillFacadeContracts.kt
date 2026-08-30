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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract


import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.utils.math.geometry.LineSegment
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal const val SPEAR_KILL_TARGET_SELECTION_MARGIN = 0.75
internal const val KILL_AURA_INHERITED_TARGET_SOURCE = "KillAura"
internal const val KILL_AURA_DISABLED_REASON = "killaura-disabled"
internal const val SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED = 1e-9
internal const val SPEAR_KILL_MIN_ATTACK_RAY_RANGE = 2.0
internal const val SPEAR_KILL_ATTACK_RAY_RANGE = 4.5
internal const val SPEAR_KILL_ATTACK_REQUEST_WINDOW_MS = 250L
internal const val SPEAR_KILL_FAILURE_NOTIFICATION_COOLDOWN_TICKS = 200
internal const val SPEAR_KILL_REJECTED_TARGET_RETRY_TICKS = 20
internal const val SPEAR_KILL_MAX_RECOVERY_STALL_TICKS = 40
internal const val SPEAR_KILL_RECOVERY_STEP_EPSILON = 1.0E-6
internal const val SPEAR_KILL_RECOVERY_POSITION_EPSILON_SQUARED = 1.0E-6
internal const val SPEAR_KILL_FALL_SAFETY_OFFSET_EPSILON_SQUARED = 1.0E-12
internal const val SPEAR_KILL_NEAR_GROUND_PROBE_DEPTH = 0.08
internal const val SPEAR_KILL_NEAR_GROUND_HORIZONTAL_INSET = 0.001
internal const val SPEAR_KILL_HITBOX_RAYCAST_MAX_SPAN_LENGTH = 4.0
internal const val SPEAR_KILL_DIRECT_PREDICTION_REFINEMENT_LIMIT = 4
internal const val SPEAR_KILL_ROUTE_SNAPSHOT_MAX_CELLS = 16_384
internal const val SPEAR_KILL_PRIMED_ENDPOINT_EPSILON = 1.0E-5
internal const val SPEAR_KILL_PRIMED_MAX_SERVER_PACKETS = 5
internal const val SPEAR_KILL_PRIMED_MAX_PACKETS_PER_MOVEMENT = 5
internal const val SPEAR_KILL_DEBUG_ROUTE_PREVIEW_STEPS = 24
internal const val SPEAR_KILL_HIGH_SPEED_MAX_EXPLICIT_PRIMING = 18
internal const val SPEAR_KILL_HIGH_SPEED_MIN_DISTANCE = 0.01
internal const val SPEAR_KILL_HIGH_SPEED_MAX_DISTANCE = 200.0
internal const val SPEAR_KILL_DIRECT_SNAPSHOT_HORIZONTAL_MARGIN = 0
internal const val SPEAR_KILL_DIRECT_SNAPSHOT_VERTICAL_MARGIN = 0
internal const val SPEAR_KILL_A_STAR_SNAPSHOT_HORIZONTAL_MARGIN = 10
internal const val SPEAR_KILL_A_STAR_SNAPSHOT_VERTICAL_MARGIN = 6

internal data class SpearKillLookRayPriority(
    val directlyHovered: Boolean,
    val angularErrorSquared: Double,
    val distanceAlongRaySquared: Double,
) : Comparable<SpearKillLookRayPriority> {

    override operator fun compareTo(other: SpearKillLookRayPriority): Int {
        if (directlyHovered != other.directlyHovered) {
            return if (directlyHovered) -1 else 1
        }
        val angularComparison = angularErrorSquared.compareTo(other.angularErrorSquared)
        return if (angularComparison != 0) {
            angularComparison
        } else {
            distanceAlongRaySquared.compareTo(other.distanceAlongRaySquared)
        }
    }
}

/** Fixed look-ray pad around the entity hitbox. Intentionally not distance-scaled. */
internal fun spearKillTargetSelectionMargin(): Double = SPEAR_KILL_TARGET_SELECTION_MARGIN

internal fun spearKillLookRayPriority(
    entityBox: AABB,
    eye: Vec3,
    lookEnd: Vec3,
    hitboxMargin: Double = SPEAR_KILL_TARGET_SELECTION_MARGIN,
): SpearKillLookRayPriority? {
    if (lookEnd.distanceToSqr(eye) <= SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED) return null
    if (!hitboxMargin.isFinite() || hitboxMargin < 0.0) return null

    val lookRay = LineSegment(eye, lookEnd)
    lookRay.firstIntersectionWith(entityBox)?.let { hitPoint ->
        return SpearKillLookRayPriority(
            directlyHovered = true,
            angularErrorSquared = 0.0,
            distanceAlongRaySquared = eye.distanceToSqr(hitPoint),
        )
    }

    val expandedHit = lookRay.firstIntersectionWith(entityBox.inflate(hitboxMargin)) ?: return null
    val nearest = lookRay.getNearestPointTo(entityBox)
    val distanceAlongRaySquared = eye.distanceToSqr(expandedHit)
    val angularErrorSquared = nearest.distanceSquared /
        maxOf(eye.distanceToSqr(nearest.point), SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED)

    return SpearKillLookRayPriority(
        directlyHovered = false,
        angularErrorSquared = angularErrorSquared,
        distanceAlongRaySquared = distanceAlongRaySquared,
    )
}

internal fun isNearSpearKillLookRay(entityBox: AABB, eye: Vec3, lookEnd: Vec3): Boolean =
    spearKillLookRayPriority(entityBox, eye, lookEnd) != null

internal fun findSpearKillAttackHitPoint(
    eye: Vec3,
    direction: Vec3,
    targetBox: AABB,
    range: Double,
): Vec3? {
    if (!range.isFinite() || range <= 0.0) return null

    val normalizedDirection = direction.normalize()
    if (normalizedDirection.lengthSqr() <= SPEAR_KILL_LOOK_RAY_EPSILON_SQUARED) return null

    return targetBox.clip(eye, eye.add(normalizedDirection.scale(range))).orElse(null)
}

internal fun findSpearKillTerminalAttackHitPoint(
    eye: Vec3,
    terminalMovement: Vec3,
    targetBox: AABB,
    range: Double,
): Vec3? = findSpearKillAttackHitPoint(
    eye = eye,
    direction = terminalMovement,
    targetBox = targetBox,
    range = range,
)

internal fun shouldClearSpearKillAStarRenderPath(
    attackKeyDown: Boolean,
    packetSessionActive: Boolean,
): Boolean = !attackKeyDown && !packetSessionActive

internal fun isSpearKillAttackRequested(
    attackKeyDown: Boolean,
    attackPressedRecently: Boolean,
): Boolean = attackKeyDown || attackPressedRecently

internal fun calculateSpearKillAttackDirection(
    playerEyePosition: Vec3,
    predictedTargetPosition: Vec3,
    targetEyeOffset: Vec3,
    fallbackDirection: Vec3,
): Vec3 {
    val targetDirection = predictedTargetPosition
        .add(targetEyeOffset)
        .subtract(playerEyePosition)

    return targetDirection.normalize().takeIf { it.lengthSqr() > 0 }
        ?: fallbackDirection.normalize()
}

internal fun migrateLegacySpearKillPreviewConfig(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val storedMode = storedValues
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .firstOrNull { it["name"]?.asString == "Mode" }

    if (storedMode?.has("choices") == true) return

    val activeMode = when {
        storedMode?.get("value")?.asString.equals("Glow", ignoreCase = true) -> "Glow"
        else -> "Box"
    }
    val boxValues = JsonArray()
    val glowValues = JsonArray()
    val retainedValues = JsonArray()

    for (storedValue in storedValues) {
        if (!storedValue.isJsonObject) {
            retainedValues.add(storedValue.deepCopy())
            continue
        }

        val setting = storedValue.asJsonObject
        when (setting["name"]?.asString) {
            "Mode" -> Unit
            in BOX_PREVIEW_SETTING_NAMES -> boxValues.add(setting.deepCopy())
            in GLOW_PREVIEW_SETTING_NAMES -> glowValues.add(setting.deepCopy())
            else -> retainedValues.add(setting.deepCopy())
        }
    }

    retainedValues.add(spearKillPreviewModeValue(activeMode, boxValues, glowValues))
    jsonObject.add("value", retainedValues)
}

private fun spearKillPreviewModeValue(activeMode: String, boxValues: JsonArray, glowValues: JsonArray) =
    JsonObject().apply {
        addProperty("name", "Mode")
        addProperty("active", activeMode)
        add("value", JsonArray())
        add("choices", JsonObject().apply {
            add("Box", spearKillPreviewChoiceValue("Box", boxValues))
            add("Glow", spearKillPreviewChoiceValue("Glow", glowValues))
        })
    }

private fun spearKillPreviewChoiceValue(name: String, values: JsonArray) = JsonObject().apply {
    addProperty("name", name)
    add("value", values)
}

private val BOX_PREVIEW_SETTING_NAMES = setOf("FillColor", "OutlineColor")
private val GLOW_PREVIEW_SETTING_NAMES = setOf(
    "GlowColor",
    "Radius",
    "Softness",
    "Intensity",
    "CoreSize",
    "Opacity",
)
