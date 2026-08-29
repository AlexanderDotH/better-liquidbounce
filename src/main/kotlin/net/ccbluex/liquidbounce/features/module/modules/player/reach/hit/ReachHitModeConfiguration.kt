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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.EventListener

/** Owns Reach Hit's mode schema independently from its runtime-only target renderer. */
internal class ReachHitModeConfiguration(eventListener: EventListener?) {

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

    val choice = ModeValueGroup<ReachHitChoice>(eventListener, "Mode", { 0 }) { parent ->
        arrayOf(
            Packet(parent).also { packet = it },
            AStar(parent).also { aStar = it },
            Adaptive(parent).also { adaptive = it },
            Motion(parent).also { motion = it },
            Pulse(parent).also { pulse = it },
            Sentinel(parent).also { sentinel = it },
        )
    }

    internal sealed class ReachHitChoice(
        name: String,
        aliases: List<String> = emptyList(),
        val travelMode: ReachHitMode,
        final override val parent: ModeValueGroup<ReachHitChoice>,
    ) : Mode(name, aliases)

    internal class Packet(parent: ModeValueGroup<ReachHitChoice>) : ReachHitChoice(
        name = "Packet",
        aliases = listOf("Direct", "SinglePacket"),
        travelMode = ReachHitMode.PACKET,
        parent = parent,
    ) {
        val stepSize by float("StepSize", 10f, 1f..20f)
    }

    internal class AStar(parent: ModeValueGroup<ReachHitChoice>) : ReachHitChoice(
        name = "AStar",
        travelMode = ReachHitMode.A_STAR,
        parent = parent,
    ) {
        val maxCost by int("MaxCost", 250, 50..500)
        val diagonal by boolean("Diagonal", false)
    }

    internal class Adaptive(parent: ModeValueGroup<ReachHitChoice>) : ReachHitChoice(
        name = "Adaptive",
        travelMode = ReachHitMode.ADAPTIVE,
        parent = parent,
    ) {
        val initialStep by float("InitialStep", 6f, 1f..10f, "blocks")
        val minimumStep by float("MinimumStep", 0.75f, 0.25f..6f, "blocks")
        val retries by int("Retries", 3, 0..5, "retries")
        val verifyTicks by int("VerifyTicks", 2, 1..5, "ticks")
    }

    internal class Motion(parent: ModeValueGroup<ReachHitChoice>) : ReachHitChoice(
        name = "Motion",
        travelMode = ReachHitMode.MOTION,
        parent = parent,
    )

    internal class Pulse(parent: ModeValueGroup<ReachHitChoice>) : ReachHitChoice(
        name = "Pulse",
        travelMode = ReachHitMode.PULSE,
        parent = parent,
    ) {
        val stepSize by float("StepSize", 10f, 1f..20f)
        val delay by int("Delay", 1, 1..5, "ticks")
    }

    internal class Sentinel(parent: ModeValueGroup<ReachHitChoice>) : ReachHitChoice(
        name = "Sentinel",
        aliases = listOf("Cubecraft", "CubeCraft", "Cube Craft"),
        travelMode = ReachHitMode.SENTINEL,
        parent = parent,
    ) {
        val stayTicks by int("StayTicks", 2, 0..10, "ticks")
    }
}

/** Migrates the legacy flat SuperHit mode layout after it has been moved under Reach > Hit. */
internal fun migrateLegacyReachHitConfig(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val valuesByName = storedValues
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .associateBy { it["name"]?.asString.orEmpty() }
    val storedMode = valuesByName["Mode"] ?: return
    val modeGroup = if (storedMode.has("choices")) {
        storedMode
    } else {
        createReachHitModeGroup(storedMode["value"]?.asString.orEmpty())
    }

    modeGroup.addProperty("active", canonicalReachHitModeName(modeGroup["active"]?.asString.orEmpty()))
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

    val legacyNames = LEGACY_REACH_HIT_SETTING_NAMES + "Mode"
    val migratedValues = JsonArray()
    storedValues
        .filterNot { it.isJsonObject && it.asJsonObject["name"]?.asString in legacyNames }
        .forEach(migratedValues::add)
    migratedValues.add(modeGroup)
    jsonObject.add("value", migratedValues)
}

private fun createReachHitModeGroup(activeMode: String) = JsonObject().apply {
    addProperty("name", "Mode")
    addProperty("active", canonicalReachHitModeName(activeMode))
    add("value", JsonArray())
    add("choices", JsonObject().apply {
        for (name in REACH_HIT_MODE_NAMES) {
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

private fun canonicalReachHitModeName(storedName: String): String = when {
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

private val REACH_HIT_MODE_NAMES = arrayOf("Packet", "AStar", "Adaptive", "Motion", "Pulse", "Sentinel")
private val LEGACY_REACH_HIT_SETTING_NAMES = setOf(
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
