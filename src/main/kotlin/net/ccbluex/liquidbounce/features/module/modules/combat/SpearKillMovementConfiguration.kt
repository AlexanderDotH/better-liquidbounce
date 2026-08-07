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
package net.ccbluex.liquidbounce.features.module.modules.combat

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener

/** Owns SpearKill's movement-mode schema independently from its attack runtime. */
internal class SpearKillMovementConfiguration(eventListener: EventListener?) {

    lateinit var motion: Motion
        private set
    lateinit var packet: Packet
        private set

    val choice = ModeValueGroup<SpearKillMovementChoice>(eventListener, "Movement", { 0 }) { parent ->
        arrayOf(
            Motion(parent).also { motion = it },
            Packet(parent).also { packet = it },
        )
    }

    internal sealed class SpearKillMovementChoice(
        name: String,
        aliases: List<String> = emptyList(),
        final override val parent: ModeValueGroup<SpearKillMovementChoice>,
    ) : Mode(name, aliases)

    internal class Motion(parent: ModeValueGroup<SpearKillMovementChoice>) : SpearKillMovementChoice(
        name = "Motion",
        parent = parent,
    ) {
        val stepLimit by float(
            "StepLimit",
            SPEAR_KILL_NORMAL_MAX_SPEED,
            SPEAR_KILL_MIN_SPEED..SPEAR_KILL_NORMAL_MAX_SPEED,
            "blocks",
        )
            .onChange { it.coerceIn(SPEAR_KILL_MIN_SPEED, SPEAR_KILL_NORMAL_MAX_SPEED) }
    }

    internal class Packet(parent: ModeValueGroup<SpearKillMovementChoice>) : SpearKillMovementChoice(
        name = "Packet",
        aliases = listOf("PacketBoot", "Packet-Boot"),
        parent = parent,
    ) {
        val stepLimit by float(
            "StepLimit",
            SPEAR_KILL_ELYTRA_MAX_SPEED,
            SPEAR_KILL_MIN_SPEED..SPEAR_KILL_ELYTRA_MAX_SPEED,
            "blocks",
        )
            .onChange { it.coerceIn(SPEAR_KILL_MIN_SPEED, SPEAR_KILL_ELYTRA_MAX_SPEED) }
        val waitTicks by int("WaitTicks", 0, 0..SPEAR_KILL_MAX_WAIT_TICKS, "ticks")
        val elytra = tree(Elytra(this))
        val aStar = tree(AStar(this))
    }

    internal class Elytra(parent: EventListener) : ToggleableValueGroup(parent, "Elytra", false) {
        val maxSpeed by float(
            "MaxSpeed",
            SPEAR_KILL_ELYTRA_MAX_SPEED,
            SPEAR_KILL_MIN_SPEED..SPEAR_KILL_ELYTRA_MAX_SPEED,
            "blocks/tick",
        )
            .onChange { it.coerceIn(SPEAR_KILL_MIN_SPEED, SPEAR_KILL_ELYTRA_MAX_SPEED) }
    }

    internal class AStar(parent: EventListener) : ToggleableValueGroup(parent, "AStar", false) {
        val maxCost by int("MaxCost", 250, 50..500)
        val diagonal by boolean("Diagonal", false)
        val renderPath by boolean("RenderPath", false)
    }
}

/** Bounds Packet steps to the speed limit that applies to the selected transport profile. */
internal fun effectiveSpearKillPacketSpeed(
    configuredMaxSpeed: Double,
    elytra: Boolean = false,
): Double = minOf(
    configuredMaxSpeed,
    if (elytra) SPEAR_KILL_ELYTRA_MAX_SPEED_DOUBLE else SPEAR_KILL_NORMAL_MAX_SPEED.toDouble(),
)

/** Applies the global speed cap and the active mode's independent per-movement step limit. */
internal fun effectiveSpearKillStepLimit(
    maxSpeed: Double,
    stepLimit: Double,
    packetMode: Boolean,
    packetElytra: Boolean = false,
): Double {
    val configuredLimit = minOf(maxSpeed, stepLimit)
    return if (packetMode) effectiveSpearKillPacketSpeed(configuredLimit, packetElytra) else configuredLimit
}

/** Snapshots the cap selected for a Packet SpearKill session before its first movement is emitted. */
internal data class SpearKillPacketTransport(
    val stepLimit: Double,
    val elytra: Boolean,
)

internal fun resolveSpearKillPacketTransport(
    elytraEnabled: Boolean,
    elytraReady: Boolean,
    normalMaxSpeed: Double,
    elytraMaxSpeed: Double,
    configuredStepLimit: Double,
): SpearKillPacketTransport {
    val useElytra = elytraEnabled && elytraReady
    val maxSpeed = if (useElytra) elytraMaxSpeed else normalMaxSpeed
    return SpearKillPacketTransport(
        stepLimit = effectiveSpearKillStepLimit(
            maxSpeed = maxSpeed,
            stepLimit = configuredStepLimit,
            packetMode = true,
            packetElytra = useElytra,
        ),
        elytra = useElytra,
    )
}

/** Matches vanilla's basic preconditions before SpearKill asks the server to start fall flying. */
internal fun canStartSpearKillElytraFlight(
    isFallFlying: Boolean,
    hasFlyingAbility: Boolean,
    isPassenger: Boolean,
    isOnClimbable: Boolean,
    isInWater: Boolean,
    hasLevitation: Boolean,
    isOnGround: Boolean,
    hasUsableElytra: Boolean,
): Boolean = hasUsableElytra && !hasFlyingAbility && !isPassenger && !isOnClimbable &&
    !isInWater && !hasLevitation && (isFallFlying || !isOnGround)

/**
 * Converts SpearKill's former flat Movement choice into the nested ModeValueGroup format.
 *
 * Existing nested values are deliberately left byte-for-byte intact so a second migration is
 * idempotent and omitted AStar values continue to deserialize to their disabled defaults.
 */
internal fun migrateLegacySpearKillMovementConfig(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val storedMovement = storedValues
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .firstOrNull { it["name"]?.asString == "Movement" }
        ?: return

    if (!storedMovement.has("choices")) {
        val migratedValues = JsonArray()
        for (storedValue in storedValues) {
            if (storedValue !== storedMovement) {
                migratedValues.add(storedValue.deepCopy())
                continue
            }

            migratedValues.add(spearKillMovementModeValue(
                canonicalSpearKillMovementName(
                    storedMovement["value"]?.takeIf { it.isJsonPrimitive }?.asString,
                ),
            ))
        }
        jsonObject.add("value", migratedValues)
        return
    }

    migrateLegacySpearKillAStarWait(storedMovement)
}

/** Promotes the removed AStar wait to Packet while preserving direct-Packet configurations. */
private fun migrateLegacySpearKillAStarWait(storedMovement: JsonObject) {
    val packet = storedMovement["choices"]?.takeIf { it.isJsonObject }?.asJsonObject
        ?.get("Packet")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
    val packetValues = packet["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val aStar = packetValues.namedSpearKillValue("AStar") ?: return
    val aStarValues = aStar["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val legacyWait = aStarValues.namedSpearKillValue("WaitTicks") ?: return
    val aStarEnabled = aStarValues.namedSpearKillValue("Enabled")
        ?.get("value")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
    val packetWait = packetValues.namedSpearKillValue("WaitTicks")

    if (packetWait == null || aStarEnabled) {
        packet.add("value", packetValues.replacingSpearKillValue("WaitTicks", legacyWait.deepCopy()))
    }
    aStar.add("value", aStarValues.replacingSpearKillValue("WaitTicks", null))
}

private fun JsonArray.namedSpearKillValue(name: String): JsonObject? =
    firstOrNull { it.isJsonObject && it.asJsonObject["name"]?.asString == name }?.asJsonObject

private fun JsonArray.replacingSpearKillValue(name: String, replacement: JsonObject?): JsonArray = JsonArray().also {
    var replaced = false
    for (element in this) {
        if (element.isJsonObject && element.asJsonObject["name"]?.asString == name) {
            if (!replaced && replacement != null) it.add(replacement)
            replaced = true
        } else {
            it.add(element)
        }
    }
    if (!replaced && replacement != null) it.add(replacement)
}

private fun spearKillMovementModeValue(activeMode: String) = JsonObject().apply {
    addProperty("name", "Movement")
    addProperty("active", activeMode)
    add("value", JsonArray())
    add("choices", JsonObject().apply {
        for (name in SPEAR_KILL_MOVEMENT_MODE_NAMES) {
            add(name, JsonObject().apply {
                addProperty("name", name)
                add("value", JsonArray())
            })
        }
    })
}

private fun canonicalSpearKillMovementName(storedName: String?): String = when {
    storedName?.equals("Packet", ignoreCase = true) == true ||
        storedName?.equals("PacketBoot", ignoreCase = true) == true ||
        storedName?.equals("Packet-Boot", ignoreCase = true) == true -> "Packet"
    else -> "Motion"
}

private val SPEAR_KILL_MOVEMENT_MODE_NAMES = arrayOf("Motion", "Packet")

internal const val SPEAR_KILL_MIN_SPEED = 2f
internal const val SPEAR_KILL_NORMAL_MAX_SPEED = 10f
internal const val SPEAR_KILL_ELYTRA_MAX_SPEED = 17.32f
internal const val SPEAR_KILL_ELYTRA_MAX_SPEED_DOUBLE = 17.32
internal const val SPEAR_KILL_MAX_WAIT_TICKS = 4
