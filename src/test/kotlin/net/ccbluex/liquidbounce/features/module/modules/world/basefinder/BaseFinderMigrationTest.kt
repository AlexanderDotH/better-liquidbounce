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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaseFinderMigrationTest {

    @Test
    fun `legacy default confidence migrates only when high sensitivity is absent`() {
        val legacyDefault = baseFinderConfig(minimumConfidence = 65)
        migrateLegacyBaseFinderSensitivity(legacyDefault)
        assertEquals(0, storedBaseFinderValue(legacyDefault, "MinimumConfidence").asInt)

        listOf(0, 1, 64, 66, 100).forEach { confidence ->
            val custom = baseFinderConfig(minimumConfidence = confidence)
            migrateLegacyBaseFinderSensitivity(custom)
            assertEquals(confidence, storedBaseFinderValue(custom, "MinimumConfidence").asInt)
        }

        val modern = baseFinderConfig(minimumConfidence = 65, highSensitivity = false)
        migrateLegacyBaseFinderSensitivity(modern)
        assertEquals(65, storedBaseFinderValue(modern, "MinimumConfidence").asInt)
    }

    @Test
    fun `flat legacy settings migrate into nested groups`() {
        val legacy = JsonObject().apply {
            addProperty("name", "BaseFinder")
            add("value", JsonArray().apply {
                add(storedBaseFinderValue("WorldSeed", "12345"))
                add(storedBaseFinderValue("Storage", false))
                add(storedBaseFinderValue("DirtyChunksPerTick", 3))
                add(storedBaseFinderValue("EntitySampleInterval", 12))
                add(storedBaseFinderValue("Notifications", false))
                add(storedBaseFinderValue("ChatCoordinates", true))
                add(JsonObject().apply {
                    addProperty("name", "SeedCompare")
                    add("value", JsonArray().apply {
                        add(storedBaseFinderValue("Enabled", true))
                        add(storedBaseFinderValue("ShowMismatches", true))
                    })
                })
            })
        }

        migrateBaseFinderGroupedSettings(legacy)

        assertEquals("12345", nestedBaseFinderValue(legacy, "Evidence", "SeedMismatch", "WorldSeed").asString)
        assertEquals(true, nestedBaseFinderValue(legacy, "Evidence", "SeedMismatch", "Enabled").asBoolean)
        assertEquals(false, nestedBaseFinderValue(legacy, "Evidence", "Storage").asBoolean)
        assertEquals(false, nestedBaseFinderValue(legacy, "Alerts", "Notifications").asBoolean)
        assertEquals(true, nestedBaseFinderValue(legacy, "Alerts", "ChatCoordinates").asBoolean)

        val rootNames = legacy.getAsJsonArray("value").map { it.asJsonObject["name"].asString }
        assertTrue("SeedCompare" !in rootNames)
        assertTrue("WorldSeed" !in rootNames)
        assertTrue("Storage" !in rootNames)
        assertTrue("DirtyChunksPerTick" !in rootNames)
        assertTrue("Performance" !in rootNames)
        assertTrue("Notifications" !in rootNames)
        assertTrue("SeedMismatch" !in rootNames)
        val evidenceNames = storedBaseFinderValue(legacy, "Evidence").asJsonArray
            .map { it.asJsonObject["name"].asString }
        assertTrue("SeedMismatch" in evidenceNames)
    }

    @Test
    fun `evidence SeedMismatch boolean migrates only when group Enabled is absent`() {
        val legacy = JsonObject().apply {
            addProperty("name", "BaseFinder")
            add("value", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("name", "Evidence")
                    add("value", JsonArray().apply {
                        add(storedBaseFinderValue("SeedMismatch", false))
                        add(storedBaseFinderValue("Storage", true))
                    })
                })
            })
        }

        migrateBaseFinderGroupedSettings(legacy)

        assertEquals(false, nestedBaseFinderValue(legacy, "Evidence", "SeedMismatch", "Enabled").asBoolean)
        val evidenceNames = storedBaseFinderValue(legacy, "Evidence").asJsonArray
            .map { it.asJsonObject["name"].asString }
        assertTrue("SeedMismatch" in evidenceNames)
        assertTrue("Storage" in evidenceNames)
    }
}
