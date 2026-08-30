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
package net.ccbluex.liquidbounce.features.module.modules.player.reach

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReachConfigMigrationTest {

    @Test
    fun `Reach enabled state is the OR of every old enabled-state combination`() {
        for (reachEnabled in listOf(false, true)) {
            for (superHitEnabled in listOf(false, true)) {
                val root = modulesRoot(
                    module("Reach", setting("Enabled", reachEnabled)),
                    module("SuperHit", setting("Enabled", superHitEnabled)),
                )

                migrateLegacyReachConfig(root)

                val reach = root.module("Reach")
                assertEquals(reachEnabled || superHitEnabled, reach.setting("Enabled")["value"].asBoolean)
                assertEquals(superHitEnabled, reach.group("Hit").setting("Enabled")["value"].asBoolean)
                assertNull(root.moduleOrNull("SuperHit"))
            }
        }
    }

    @Test
    fun `existing Reach metadata wins while legacy combat values move into Hit`() {
        val reachBind = JsonParser.parseString("""{"key":82,"type":"KEYSYM","action":"TOGGLE"}""")
        val superHitBind = JsonParser.parseString("""{"key":71,"type":"KEYSYM","action":"HOLD"}""")
        val root = modulesRoot(
            module(
                "Reach",
                setting("Enabled", false),
                setting("Bind", reachBind),
                setting("Hidden", true),
                setting("Entity", 1.75),
                setting("BlockRangeIncrease", 2.5),
            ),
            module(
                "SuperHit",
                setting("Enabled", true),
                setting("Bind", superHitBind),
                setting("Hidden", false),
                setting("MaxRange", 135.0),
                setting("MinRange", 2.5),
                setting("AttackRange", 4.4),
                setting("Tracers", true),
            ),
        )

        migrateLegacyReachConfig(root)

        val reach = root.module("Reach")
        assertTrue(reach.setting("Enabled")["value"].asBoolean)
        assertEquals(reachBind, reach.setting("Bind")["value"])
        assertTrue(reach.setting("Hidden")["value"].asBoolean)
        assertEquals(1.75, reach.setting("Entity")["value"].asDouble)
        assertEquals(2.5, reach.setting("BlockRangeIncrease")["value"].asDouble)

        val hit = reach.group("Hit")
        assertTrue(hit.setting("Enabled")["value"].asBoolean)
        assertEquals(135.0, hit.setting("MaxRange")["value"].asDouble)
        assertEquals(2.5, hit.setting("MinRange")["value"].asDouble)
        assertEquals(4.4, hit.setting("AttackRange")["value"].asDouble)
        assertTrue(hit.setting("Tracers")["value"].asBoolean)
        assertNull(hit.settingOrNull("Bind"))
        assertNull(hit.settingOrNull("Hidden"))
    }

    @Test
    fun `SuperHit metadata is inherited when Reach did not exist`() {
        val bind = JsonParser.parseString("""{"key":72,"type":"KEYSYM","action":"SMART"}""")
        val root = modulesRoot(
            module(
                "SuperHit",
                setting("Enabled", true),
                setting("Bind", bind),
                setting("Hidden", true),
                setting("MaxRange", 100.0),
            ),
        )

        migrateLegacyReachConfig(root)

        val reach = root.module("Reach")
        assertEquals(listOf("Reach"), root.moduleNames())
        assertTrue(reach.setting("Enabled")["value"].asBoolean)
        assertEquals(bind, reach.setting("Bind")["value"])
        assertTrue(reach.setting("Hidden")["value"].asBoolean)
        assertTrue(reach.group("Hit").setting("Enabled")["value"].asBoolean)
        assertEquals(100.0, reach.group("Hit").setting("MaxRange")["value"].asDouble)
    }

    @Test
    fun `flat SuperHit mode settings migrate into canonical Reach Hit choices`() {
        val root = modulesRoot(
            module(
                "SuperHit",
                setting("Enabled", true),
                setting("Mode", "SinglePacket"),
                setting("StepSize", 7.5),
                setting("AStarMaxCost", 321),
                setting("AStarDiagonal", true),
                setting("AdaptiveInitialStep", 5.5),
                setting("AdaptiveMinimumStep", 0.5),
                setting("AdaptiveRetries", 4),
                setting("AdaptiveVerifyTicks", 3),
                setting("PulseDelay", 2),
                setting("SentinelStayTicks", 6),
            ),
        )

        migrateLegacyReachConfig(root)

        val hit = root.module("Reach").group("Hit")
        val mode = hit.group("Mode")
        val choices = mode.getAsJsonObject("choices")
        assertEquals("Packet", mode["active"].asString)
        assertEquals(setOf("Packet", "AStar", "Adaptive", "Motion", "Pulse", "Sentinel"), choices.keySet())
        assertEquals(7.5, choices.setting("Packet", "StepSize").asDouble)
        assertEquals(7.5, choices.setting("Pulse", "StepSize").asDouble)
        assertEquals(321, choices.setting("AStar", "MaxCost").asInt)
        assertTrue(choices.setting("AStar", "Diagonal").asBoolean)
        assertEquals(5.5, choices.setting("Adaptive", "InitialStep").asDouble)
        assertEquals(0.5, choices.setting("Adaptive", "MinimumStep").asDouble)
        assertEquals(4, choices.setting("Adaptive", "Retries").asInt)
        assertEquals(3, choices.setting("Adaptive", "VerifyTicks").asInt)
        assertEquals(2, choices.setting("Pulse", "Delay").asInt)
        assertEquals(6, choices.setting("Sentinel", "StayTicks").asInt)
        assertFalse(hit.valueNames().any(LEGACY_FLAT_HIT_SETTINGS::contains))
    }

    @Test
    fun `all historical mode aliases select their canonical Reach Hit mode`() {
        val aliases = mapOf(
            "Direct" to "Packet",
            "direct" to "Packet",
            "SinglePacket" to "Packet",
            "Cubecraft" to "Sentinel",
            "CubeCraft" to "Sentinel",
            "Cube Craft" to "Sentinel",
        )

        for ((stored, canonical) in aliases) {
            val root = modulesRoot(module("SuperHit", setting("Mode", stored)))

            migrateLegacyReachConfig(root)

            assertEquals(canonical, root.module("Reach").group("Hit").group("Mode")["active"].asString)
        }
    }

    @Test
    fun `exact canonical mode choice and settings win over aliases and flat values`() {
        val root = modulesRoot(
            module(
                "SuperHit",
                modeGroup(
                    active = "Direct",
                    "Direct" to choice("Direct", setting("StepSize", 11.0)),
                    "Packet" to choice("Packet", setting("StepSize", 12.0)),
                    "CubeCraft" to choice("CubeCraft", setting("StayTicks", 8)),
                ),
                setting("StepSize", 7.5),
                setting("SentinelStayTicks", 6),
            ),
        )

        migrateLegacyReachConfig(root)

        val mode = root.module("Reach").group("Hit").group("Mode")
        val choices = mode.getAsJsonObject("choices")
        assertEquals("Packet", mode["active"].asString)
        assertEquals(12.0, choices.setting("Packet", "StepSize").asDouble)
        assertEquals(8, choices.setting("Sentinel", "StayTicks").asInt)
        assertFalse(choices.has("Direct"))
        assertFalse(choices.has("CubeCraft"))
    }

    @Test
    fun `canonical Reach Hit values win while legacy Enabled still controls Hit activation`() {
        val currentHit = group(
            "Hit",
            setting("Enabled", false),
            setting("MaxRange", 42.0),
        )
        val root = modulesRoot(
            module("Reach", setting("Enabled", false), currentHit),
            module(
                "SuperHit",
                setting("Enabled", true),
                setting("MaxRange", 100.0),
                setting("MinRange", 3.0),
            ),
        )

        migrateLegacyReachConfig(root)

        val hit = root.module("Reach").group("Hit")
        assertTrue(hit.setting("Enabled")["value"].asBoolean)
        assertEquals(42.0, hit.setting("MaxRange")["value"].asDouble)
        assertEquals(3.0, hit.setting("MinRange")["value"].asDouble)
    }

    @Test
    fun `partial SuperHit config without a Mode keeps only the supplied Hit values`() {
        val root = modulesRoot(
            module(
                "SuperHit",
                setting("Enabled", false),
                setting("AttackRange", 4.6),
            ),
        )

        migrateLegacyReachConfig(root)

        val hit = root.module("Reach").group("Hit")
        assertFalse(hit.setting("Enabled")["value"].asBoolean)
        assertEquals(4.6, hit.setting("AttackRange")["value"].asDouble)
        assertNull(hit.settingOrNull("Mode"))
    }

    @Test
    fun `partial legacy flat mode setting is nested even when Mode was omitted`() {
        val root = modulesRoot(
            module(
                "SuperHit",
                setting("Enabled", true),
                setting("AStarMaxCost", 275),
            ),
        )

        migrateLegacyReachConfig(root)

        val hit = root.module("Reach").group("Hit")
        val mode = hit.group("Mode")
        assertEquals("Packet", mode["active"].asString)
        assertEquals(275, mode.getAsJsonObject("choices").setting("AStar", "MaxCost").asInt)
        assertNull(hit.settingOrNull("AStarMaxCost"))
    }

    @Test
    fun `partial Reach config does not gain a disabling Enabled value`() {
        val root = modulesRoot(
            module("Reach", setting("BlockRangeIncrease", 2.0)),
            module("SuperHit", setting("Enabled", false), setting("MaxRange", 80.0)),
        )

        migrateLegacyReachConfig(root)

        val reach = root.module("Reach")
        assertNull(reach.settingOrNull("Enabled"))
        assertFalse(reach.group("Hit").setting("Enabled")["value"].asBoolean)
    }

    @Test
    fun `migration is idempotent after SuperHit removal`() {
        val root = modulesRoot(
            module("Reach", setting("Enabled", true), setting("BlockRangeIncrease", 1.0)),
            module("SuperHit", setting("Enabled", true), setting("Mode", "Cube Craft")),
        )

        migrateLegacyReachConfig(root)
        val migratedOnce = root.deepCopy()
        migrateLegacyReachConfig(root)

        assertEquals(migratedOnce, root)
    }
}

private val LEGACY_FLAT_HIT_SETTINGS = setOf(
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

private fun modulesRoot(vararg modules: JsonObject) = JsonObject().apply {
    addProperty("name", "modules")
    add("value", JsonArray().apply { modules.forEach(::add) })
}

private fun module(name: String, vararg values: JsonObject) = group(name, *values)

private fun group(name: String, vararg values: JsonObject) = JsonObject().apply {
    addProperty("name", name)
    add("value", JsonArray().apply { values.forEach(::add) })
}

private fun setting(name: String, value: Any) = JsonObject().apply {
    addProperty("name", name)
    when (value) {
        is Boolean -> addProperty("value", value)
        is Number -> addProperty("value", value)
        is String -> addProperty("value", value)
        else -> error("Unsupported test value $value")
    }
}

private fun setting(name: String, value: com.google.gson.JsonElement) = JsonObject().apply {
    addProperty("name", name)
    add("value", value)
}

private fun choice(name: String, vararg values: JsonObject) = group(name, *values)

private fun modeGroup(active: String, vararg choices: Pair<String, JsonObject>) = JsonObject().apply {
    addProperty("name", "Mode")
    addProperty("active", active)
    add("value", JsonArray())
    add("choices", JsonObject().apply { choices.forEach { (name, choice) -> add(name, choice) } })
}

private fun JsonObject.module(name: String): JsonObject = requireNotNull(moduleOrNull(name))

private fun JsonObject.moduleOrNull(name: String): JsonObject? = getAsJsonArray("value")
    .asSequence()
    .mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
    .firstOrNull { module -> module["name"]?.asString == name }

private fun JsonObject.moduleNames(): List<String> = getAsJsonArray("value")
    .map { module -> module.asJsonObject["name"].asString }

private fun JsonObject.valueNames(): List<String> = getAsJsonArray("value")
    .map { value -> value.asJsonObject["name"].asString }

private fun JsonObject.setting(name: String): JsonObject = requireNotNull(settingOrNull(name))

private fun JsonObject.settingOrNull(name: String): JsonObject? = getAsJsonArray("value")
    .asSequence()
    .mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
    .firstOrNull { value -> value["name"]?.asString == name }

private fun JsonObject.group(name: String): JsonObject = setting(name)

private fun JsonObject.setting(choice: String, setting: String) = getAsJsonObject(choice)
    .getAsJsonArray("value")
    .map { value -> value.asJsonObject }
    .single { value -> value["name"].asString == setting }["value"]
