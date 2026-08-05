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
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.EventListener

/** Owns SuperHit's mode schema independently from its runtime-only target renderer. */
internal class SuperHitModeConfiguration(eventListener: EventListener?) {

    lateinit var packet: Packet
        private set
    lateinit var aStar: AStar
        private set
    lateinit var adaptive: Adaptive
        private set
    lateinit var motion: Motion
        private set
    lateinit var pulse: Pulse
        private set
    lateinit var sentinel: Sentinel
        private set

    val choice = ModeValueGroup<SuperHitChoice>(eventListener, "Mode", { 0 }) { parent ->
        arrayOf(
            Packet(parent).also { packet = it },
            AStar(parent).also { aStar = it },
            Adaptive(parent).also { adaptive = it },
            Motion(parent).also { motion = it },
            Pulse(parent).also { pulse = it },
            Sentinel(parent).also { sentinel = it },
        )
    }

    internal sealed class SuperHitChoice(
        name: String,
        aliases: List<String> = emptyList(),
        val travelMode: SuperHitMode,
        final override val parent: ModeValueGroup<SuperHitChoice>,
    ) : Mode(name, aliases)

    internal class Packet(parent: ModeValueGroup<SuperHitChoice>) : SuperHitChoice(
        name = "Packet",
        aliases = listOf("Direct", "SinglePacket"),
        travelMode = SuperHitMode.PACKET,
        parent = parent,
    ) {
        val stepSize by float("StepSize", 10f, 1f..20f)
    }

    internal class AStar(parent: ModeValueGroup<SuperHitChoice>) : SuperHitChoice(
        name = "AStar",
        travelMode = SuperHitMode.A_STAR,
        parent = parent,
    ) {
        val maxCost by int("MaxCost", 250, 50..500)
        val diagonal by boolean("Diagonal", false)
    }

    internal class Adaptive(parent: ModeValueGroup<SuperHitChoice>) : SuperHitChoice(
        name = "Adaptive",
        travelMode = SuperHitMode.ADAPTIVE,
        parent = parent,
    ) {
        val initialStep by float("InitialStep", 6f, 1f..10f, "blocks")
        val minimumStep by float("MinimumStep", 0.75f, 0.25f..6f, "blocks")
        val retries by int("Retries", 3, 0..5, "retries")
        val verifyTicks by int("VerifyTicks", 2, 1..5, "ticks")
    }

    internal class Motion(parent: ModeValueGroup<SuperHitChoice>) : SuperHitChoice(
        name = "Motion",
        travelMode = SuperHitMode.MOTION,
        parent = parent,
    )

    internal class Pulse(parent: ModeValueGroup<SuperHitChoice>) : SuperHitChoice(
        name = "Pulse",
        travelMode = SuperHitMode.PULSE,
        parent = parent,
    ) {
        val stepSize by float("StepSize", 10f, 1f..20f)
        val delay by int("Delay", 1, 1..5, "ticks")
    }

    internal class Sentinel(parent: ModeValueGroup<SuperHitChoice>) : SuperHitChoice(
        name = "Sentinel",
        aliases = listOf("Cubecraft", "CubeCraft", "Cube Craft"),
        travelMode = SuperHitMode.SENTINEL,
        parent = parent,
    ) {
        val stayTicks by int("StayTicks", 2, 0..10, "ticks")
    }
}

internal fun migrateLegacySuperHitConfig(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val valuesByName = storedValues
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .associateBy { it["name"]?.asString.orEmpty() }
    val storedMode = valuesByName["Mode"] ?: return
    val modeGroup = if (storedMode.has("choices")) {
        storedMode
    } else {
        createSuperHitModeGroup(storedMode["value"]?.asString.orEmpty())
    }

    modeGroup.addProperty("active", canonicalSuperHitModeName(modeGroup["active"]?.asString.orEmpty()))
    val choices = modeGroup.getAsJsonObject("choices")
    migrateLegacySetting(valuesByName, choices, "StepSize", "Packet", "StepSize")
    migrateLegacySetting(valuesByName, choices, "StepSize", "Pulse", "StepSize")
    migrateLegacySetting(valuesByName, choices, "AStarMaxCost", "AStar", "MaxCost")
    migrateLegacySetting(valuesByName, choices, "AStarDiagonal", "AStar", "Diagonal")
    migrateLegacySetting(valuesByName, choices, "AdaptiveInitialStep", "Adaptive", "InitialStep")
    migrateLegacySetting(valuesByName, choices, "AdaptiveMinimumStep", "Adaptive", "MinimumStep")
    migrateLegacySetting(valuesByName, choices, "AdaptiveRetries", "Adaptive", "Retries")
    migrateLegacySetting(valuesByName, choices, "AdaptiveVerifyTicks", "Adaptive", "VerifyTicks")
    migrateLegacySetting(valuesByName, choices, "PulseDelay", "Pulse", "Delay")
    migrateLegacySetting(valuesByName, choices, "SentinelStayTicks", "Sentinel", "StayTicks")

    val legacyNames = LEGACY_SUPER_HIT_SETTING_NAMES + "Mode"
    val migratedValues = JsonArray()
    storedValues
        .filterNot { it.isJsonObject && it.asJsonObject["name"]?.asString in legacyNames }
        .forEach(migratedValues::add)
    migratedValues.add(modeGroup)
    jsonObject.add("value", migratedValues)
}

private fun createSuperHitModeGroup(activeMode: String) = JsonObject().apply {
    addProperty("name", "Mode")
    addProperty("active", canonicalSuperHitModeName(activeMode))
    add("value", JsonArray())
    add("choices", JsonObject().apply {
        for (name in SUPER_HIT_MODE_NAMES) {
            add(name, JsonObject().apply {
                addProperty("name", name)
                add("value", JsonArray())
            })
        }
    })
}

private fun migrateLegacySetting(
    valuesByName: Map<String, JsonObject>,
    choices: JsonObject,
    legacyName: String,
    choiceName: String,
    settingName: String,
) {
    val legacySetting = valuesByName[legacyName] ?: return
    val choiceValues = choices.getAsJsonObject(choiceName).getAsJsonArray("value")
    if (choiceValues.any { it.asJsonObject["name"].asString == settingName }) return

    choiceValues.add(legacySetting.deepCopy().apply { addProperty("name", settingName) })
}

private fun canonicalSuperHitModeName(storedName: String): String = when {
    storedName.equals("Packet", ignoreCase = true) ||
        storedName.equals("Direct", ignoreCase = true) ||
        storedName.equals("SinglePacket", ignoreCase = true) -> "Packet"
    storedName.equals("AStar", ignoreCase = true) -> "AStar"
    storedName.equals("Adaptive", ignoreCase = true) -> "Adaptive"
    storedName.equals("Motion", ignoreCase = true) -> "Motion"
    storedName.equals("Pulse", ignoreCase = true) -> "Pulse"
    storedName.equals("Sentinel", ignoreCase = true) ||
        storedName.equals("Cubecraft", ignoreCase = true) ||
        storedName.equals("Cube Craft", ignoreCase = true) -> "Sentinel"
    else -> storedName
}

private val SUPER_HIT_MODE_NAMES = arrayOf("Packet", "AStar", "Adaptive", "Motion", "Pulse", "Sentinel")
private val LEGACY_SUPER_HIT_SETTING_NAMES = setOf(
    "StepSize",
    "AStarMaxCost",
    "AStarDiagonal",
    "AdaptiveInitialStep",
    "AdaptiveMinimumStep",
    "AdaptiveRetries",
    "AdaptiveVerifyTicks",
    "PulseDelay",
    "SentinelStayTicks",
)
