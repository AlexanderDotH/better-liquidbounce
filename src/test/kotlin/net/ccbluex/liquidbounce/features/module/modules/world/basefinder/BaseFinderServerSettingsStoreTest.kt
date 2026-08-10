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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseFinderServerSettingsStoreTest {

    @Test
    fun `seed and complete score matrix round trip per server without a dimension key`() {
        val root = createTempDirectory("basefinder-server-settings")
        val store = BaseFinderServerSettingsStore(root)
        val custom = BaseFinderScoringWeights.DEFAULT
            .with(BaseFinderScoreWeight.UTILITY_CATEGORY, 12)
            .with(BaseFinderScoreWeight.FALSE_POSITIVE_VILLAGE, 4)
        val settings = BaseFinderServerSettings("-123456789", custom)

        val path = store.save("example.org:25565", settings)

        assertEquals(settings, store.load("example.org:25565"))
        assertNull(store.load("other.example.org:25565"))
        assertEquals(path, store.settingsPath("example.org:25565"))

        val expectedHash = sha256("example.org:25565")
        val encoded = Files.readString(path)
        val document = JsonParser.parseString(encoded).asJsonObject
        assertEquals("$expectedHash.json", path.fileName.toString())
        assertEquals(expectedHash, document["serverKeyHash"].asString)
        assertEquals(custom.toPersistedMap().size, document["scoring"].asJsonObject.size())
        assertFalse(encoded.contains("example.org:25565"))
        assertTrue(Files.list(path.parent).use { children ->
            children.noneMatch { child -> child.fileName.toString().endsWith(".tmp") }
        })
    }

    @Test
    fun `legacy global settings are claimed once and later unknown servers start at defaults`() {
        val root = createTempDirectory("basefinder-server-settings-migration")
        val store = BaseFinderServerSettingsStore(root)
        val legacy = BaseFinderServerSettings(
            worldSeed = "legacy-seed",
            scoringWeights = BaseFinderScoringWeights.DEFAULT.with(BaseFinderScoreWeight.ACTIVITY_CATEGORY, 9),
        )

        assertEquals(legacy, store.loadOrInitialize("first.example", legacy))
        assertTrue(Files.isRegularFile(store.migrationMarkerPath))

        val fresh = store.loadOrInitialize("second.example", legacy)
        assertEquals("", fresh.worldSeed)
        assertEquals(BaseFinderScoringWeights.DEFAULT, fresh.scoringWeights)
        assertEquals(legacy, store.load("first.example"))
        assertEquals(fresh, store.load("second.example"))

        Files.delete(store.settingsPath("first.example"))
        assertEquals(BaseFinderServerSettings(), store.loadOrInitialize("first.example", legacy))
    }

    @Test
    fun `loading an existing profile does not consume the one-time legacy migration`() {
        val root = createTempDirectory("basefinder-server-settings-existing")
        val store = BaseFinderServerSettingsStore(root)
        val existing = BaseFinderServerSettings("stored", BaseFinderScoringWeights.DEFAULT)
        val legacy = BaseFinderServerSettings(
            "legacy",
            BaseFinderScoringWeights.DEFAULT.with(BaseFinderScoreWeight.ACTIVITY_CATEGORY, 9),
        )
        store.save("existing.example", existing)

        assertEquals(existing, store.loadOrInitialize("existing.example", legacy))
        assertFalse(Files.exists(store.migrationMarkerPath))
        assertEquals(legacy, store.loadOrInitialize("first-new.example", legacy))
    }

    @Test
    fun `malformed and future documents fail closed without replacement`() {
        val root = createTempDirectory("basefinder-server-settings-invalid")
        val store = BaseFinderServerSettingsStore(root)
        val path = store.settingsPath("example.org")
        Files.createDirectories(path.parent)

        val malformed = "not json"
        Files.writeString(path, malformed)
        assertNull(store.load("example.org"))
        assertEquals(
            BaseFinderServerSettings(),
            store.loadOrInitialize("example.org", BaseFinderServerSettings("legacy")),
        )
        assertEquals(malformed, Files.readString(path))

        val future = """{"version":999,"worldSeed":"1","scoring":{}}"""
        Files.writeString(path, future)
        assertNull(store.load("example.org"))
        assertEquals(future, Files.readString(path))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
