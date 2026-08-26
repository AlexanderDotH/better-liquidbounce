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
package net.ccbluex.liquidbounce.features.global

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialTargetConfigMigrationTest {

    @Test
    fun `legacy Hostile selections gain Trial independently`() {
        val settings = settingsFile(
            combat = listOf("Players", "Hostile"),
            visual = listOf("Players"),
        )

        assertTrue(TrialTargetConfigMigration.migrateFileSettings(settings))

        assertEquals(listOf("Players", "Hostile", "Trial"), settings.targetValues("Combat"))
        assertEquals(listOf("Players"), settings.targetValues("Visual"))
        assertEquals(1, settings.markerCount())
    }

    @Test
    fun `Visual Hostile gains Trial without changing Combat`() {
        val settings = settingsFile(
            combat = listOf("Players", "Passive"),
            visual = listOf("Invisible", "Hostile"),
        )

        assertTrue(TrialTargetConfigMigration.migrateFileSettings(settings))

        assertEquals(listOf("Players", "Passive"), settings.targetValues("Combat"))
        assertEquals(listOf("Invisible", "Hostile", "Trial"), settings.targetValues("Visual"))
    }

    @Test
    fun `migration preserves target ordering and unknown wire values`() {
        val settings = settingsFile(
            combat = listOf("FutureTarget", "Hostile", "Players", "UnknownTarget"),
            visual = listOf("Invisible"),
        )

        TrialTargetConfigMigration.migrateFileSettings(settings)

        assertEquals(
            listOf("FutureTarget", "Hostile", "Players", "UnknownTarget", "Trial"),
            settings.targetValues("Combat"),
        )
    }

    @Test
    fun `hidden marker makes migration idempotent and avoids duplicate Trial values`() {
        val settings = settingsFile(
            combat = listOf("Players", "Hostile", "Trial"),
            visual = listOf("Hostile"),
        )

        assertTrue(TrialTargetConfigMigration.migrateFileSettings(settings))
        val migrated = settings.deepCopy()

        assertFalse(TrialTargetConfigMigration.migrateFileSettings(settings))
        assertEquals(migrated, settings)
        assertEquals(1, settings.targetValues("Combat").count { it == "Trial" })
        assertEquals(1, settings.targetValues("Visual").count { it == "Trial" })
        assertEquals(1, settings.markerCount())
    }

    @Test
    fun `manual Trial removal after migration survives subsequent file loads`() {
        val settings = settingsFile(
            combat = listOf("Players", "Hostile"),
            visual = listOf("Players", "Hostile"),
        )
        TrialTargetConfigMigration.migrateFileSettings(settings)
        settings.targetValue("Combat").getAsJsonArray("value").run {
            remove(first { it.asString == "Trial" })
        }
        val manuallyDisabled = settings.deepCopy()

        assertFalse(TrialTargetConfigMigration.migrateFileSettings(settings))

        assertEquals(manuallyDisabled, settings)
        assertFalse("Trial" in settings.targetValues("Combat"))
        assertTrue("Trial" in settings.targetValues("Visual"))
    }

    @Test
    fun `REST interop settings never trigger or reapply migration`() {
        val settings = settingsFile(
            combat = listOf("Players", "Hostile"),
            visual = listOf("Hostile"),
        ).apply {
            addProperty("valueType", "CONFIGURABLE")
            targets().addProperty("valueType", "CONFIGURABLE")
            targetValue("Combat").addProperty("valueType", "MULTI_CHOOSE")
            targetValue("Visual").addProperty("valueType", "MULTI_CHOOSE")
        }
        val interopPayload = settings.deepCopy()

        assertFalse(TrialTargetConfigMigration.migrateFileSettings(settings))

        assertEquals(interopPayload, settings)
        assertEquals(0, settings.markerCount())
        assertFalse("Trial" in settings.targetValues("Combat"))
        assertFalse("Trial" in settings.targetValues("Visual"))
    }

    @Test
    fun `nested interop Targets group is skipped even without root metadata`() {
        val settings = settingsFile(
            combat = listOf("Hostile"),
            visual = listOf("Hostile"),
        ).apply {
            targets().addProperty("valueType", "CONFIGURABLE")
        }
        val interopPayload = settings.deepCopy()

        assertFalse(TrialTargetConfigMigration.migrateFileSettings(settings))

        assertEquals(interopPayload, settings)
    }

    @Test
    fun `absent and malformed file fields remain untouched`() {
        val absentTargets = jsonObject("""{"name":"Settings","value":[]}""")
        val malformedRootValues = jsonObject("""{"name":"Settings","value":true}""")
        val malformedTargetsValues = jsonObject(
            """{"name":"Settings","value":[{"name":"Targets","value":"broken"}]}""",
        )
        val malformedTargetLists = jsonObject(
            """
            {"name":"Settings","value":[{"name":"Targets","value":[
              {"name":"Combat","value":"Hostile"},
              {"name":"Visual","value":null}
            ]}]}
            """.trimIndent(),
        )
        val documents = listOf(absentTargets, malformedRootValues, malformedTargetsValues, malformedTargetLists)
        val originals = documents.map(JsonObject::deepCopy)

        documents.forEach { assertFalse(TrialTargetConfigMigration.migrateFileSettings(it)) }

        assertEquals(originals, documents)
    }

    @Test
    fun `a fresh settings document is not treated as legacy target state`() {
        val fresh = JsonObject()

        assertFalse(TrialTargetConfigMigration.migrateFileSettings(fresh))

        assertEquals(JsonObject(), fresh)
    }

    @Test
    fun `legacy Enemies alias is migrated using the persisted Targets contract`() {
        val settings = settingsFile(
            combat = listOf("Hostile"),
            visual = listOf("Players"),
            groupName = "Enemies",
        )

        assertTrue(TrialTargetConfigMigration.migrateFileSettings(settings))

        assertEquals(listOf("Hostile", "Trial"), settings.targetValues("Combat"))
        assertEquals(1, settings.markerCount())
    }

    @Test
    fun `nested Targets prepareDeserialize hook can migrate its group directly`() {
        val targets = settingsFile(
            combat = listOf("Players", "Hostile"),
            visual = listOf("Players"),
        ).targets()

        assertTrue(TrialTargetConfigMigration.migrateFileSettings(targets))

        assertEquals(listOf("Players", "Hostile", "Trial"), targets.directTargetValues("Combat"))
        assertEquals(1, targets.getAsJsonArray("value").count { element ->
            element.isJsonObject && element.asJsonObject["name"].asString ==
                TrialTargetConfigMigration.MARKER_NAME
        })
    }

    private fun settingsFile(
        combat: List<String>,
        visual: List<String>,
        groupName: String = "Targets",
    ): JsonObject = JsonObject().apply {
        addProperty("name", "Settings")
        add("value", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", groupName)
                add("value", JsonArray().apply {
                    add(targetSetting("Combat", combat))
                    add(targetSetting("Visual", visual))
                })
            })
        })
    }

    private fun targetSetting(name: String, targets: List<String>) = JsonObject().apply {
        addProperty("name", name)
        add("value", JsonArray().apply { targets.forEach(::add) })
    }

    private fun JsonObject.targets(): JsonObject = getAsJsonArray("value")
        .map { it.asJsonObject }
        .single { it["name"].asString in setOf("Targets", "Enemies") }

    private fun JsonObject.targetValue(name: String): JsonObject = targets()
        .getAsJsonArray("value")
        .map { it.asJsonObject }
        .single { it["name"].asString == name }

    private fun JsonObject.targetValues(name: String): List<String> = targetValue(name)
        .getAsJsonArray("value")
        .map { it.asString }

    private fun JsonObject.directTargetValues(name: String): List<String> = getAsJsonArray("value")
        .map { it.asJsonObject }
        .single { it["name"].asString == name }
        .getAsJsonArray("value")
        .map { it.asString }

    private fun JsonObject.markerCount(): Int = runCatching {
        targets().getAsJsonArray("value").count { element ->
            element.isJsonObject && element.asJsonObject["name"]?.asString ==
                TrialTargetConfigMigration.MARKER_NAME
        }
    }.getOrDefault(0)

    private fun jsonObject(source: String) = JsonParser.parseString(source).asJsonObject
}
