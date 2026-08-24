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

package net.ccbluex.liquidbounce.features.baritone.packaging

import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaritoneVendorPackagingTest {

    @Test
    fun `vendored api fabric jar has the pinned digest`() {
        assertTrue(Files.isRegularFile(vendorJar), "Missing vendored Baritone JAR: $vendorJar")

        assertEquals(EXPECTED_JAR_SHA256, Files.readAllBytes(vendorJar).sha256())
    }

    @Test
    fun `vendored jar declares the exact Fabric runtime contract`() = withVendorJar { jar ->
        val descriptor = JsonParser.parseString(jar.readText("fabric.mod.json")).asJsonObject

        assertEquals("baritone", descriptor["id"].asString)
        assertEquals(VERSION, descriptor["version"].asString)
        assertEquals("LGPL-3.0", descriptor["license"].asString)
        assertEquals(listOf("mixins.baritone.json"), descriptor["mixins"].asJsonArray.map { it.asString })
        assertEquals(listOf("26.2"), descriptor["depends"].asJsonObject["minecraft"].asJsonArray.map { it.asString })
        assertEquals(">=0.19.3", descriptor["depends"].asJsonObject["fabricloader"].asString)
        assertEquals(
            listOf(NETHER_PATHFINDER_ENTRY),
            descriptor["jars"].asJsonArray.map { it.asJsonObject["file"].asString },
        )
    }

    @Test
    fun `vendored jar retains mixins and reflection loaded provider classes`() = withVendorJar { jar ->
        REQUIRED_BARITONE_ENTRIES.forEach { entry ->
            assertTrue(jar.getJarEntry(entry) != null, "Missing required Baritone entry: $entry")
        }

        val mixins = JsonParser.parseString(jar.readText("mixins.baritone.json")).asJsonObject
        assertTrue(mixins["required"].asBoolean)
        assertEquals("baritone.launch.mixins", mixins["package"].asString)
        assertTrue(
            REQUIRED_MIXINS.all { it in mixins["client"].asJsonArray.map { element -> element.asString } },
            "Baritone's input, network, tick, and render mixins must remain available",
        )
        assertTrue(
            jar.readBytes("baritone/api/BaritoneAPI.class").containsAscii("baritone.BaritoneProvider"),
            "BaritoneAPI must retain the reflection target used to load BaritoneProvider",
        )
    }

    @Test
    fun `vendored jar carries the pinned Nether pathfinder nested mod`() = withVendorJar { jar ->
        val nestedJar = jar.readBytes(NETHER_PATHFINDER_ENTRY)

        assertEquals(EXPECTED_NETHER_PATHFINDER_SHA256, nestedJar.sha256())
        JarInputStream(ByteArrayInputStream(nestedJar)).use { input ->
            val entries = buildSet {
                while (true) {
                    val entry = input.nextJarEntry ?: break
                    add(entry.name)
                }
            }
            assertTrue(REQUIRED_NETHER_PATHFINDER_ENTRIES.all(entries::contains))
        }
    }

    @Test
    fun `vendor directory carries exact source license and rebuild evidence`() {
        assertEquals(EXPECTED_LICENSE_SHA256, Files.readAllBytes(vendorDirectory.resolve("LICENSE")).sha256())
        assertTrue(Files.isRegularFile(sourceArchive), "Missing corresponding Baritone source archive")
        assertEquals(EXPECTED_SOURCE_SHA256, Files.readAllBytes(sourceArchive).sha256())

        val checksums = Files.readAllLines(vendorDirectory.resolve("SHA256SUMS")).toSet()
        assertTrue("$EXPECTED_JAR_SHA256  $JAR_NAME" in checksums)
        assertTrue("$EXPECTED_SOURCE_SHA256  $SOURCE_ARCHIVE_NAME" in checksums)

        val origin = Files.readString(vendorDirectory.resolve("ORIGIN.md"))
        assertTrue(COMMIT in origin)
        assertTrue("Java 25" in origin)
        assertTrue(":fabric:build" in origin)

        val notice = Files.readString(vendorDirectory.resolve("NOTICE.md"))
        assertTrue("LGPL-3.0" in notice)
        assertTrue(SOURCE_ARCHIVE_NAME in notice)
        assertTrue("unmodified" in notice.lowercase())
    }

    private fun withVendorJar(assertions: (JarFile) -> Unit) {
        JarFile(vendorJar.toFile()).use(assertions)
    }

    private fun JarFile.readBytes(entryName: String): ByteArray {
        val entry = requireNotNull(getJarEntry(entryName)) { "Missing JAR entry: $entryName" }
        return getInputStream(entry).use { it.readAllBytes() }
    }

    private fun JarFile.readText(entryName: String) = readBytes(entryName).decodeToString()

    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun ByteArray.containsAscii(value: String) = indexOf(value.encodeToByteArray()) >= 0

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        return indices.firstOrNull { start ->
            start + needle.size <= size && needle.indices.all { offset -> this[start + offset] == needle[offset] }
        } ?: -1
    }

    companion object {
        private const val COMMIT = "2991d9218050707df9c8daca5efd371091a92d36"
        private const val VERSION = "1.15.0-10-g2991d921"
        private const val JAR_NAME = "baritone-api-fabric-$VERSION.jar"
        private const val SOURCE_ARCHIVE_NAME = "baritone-$VERSION-sources.tar.gz"
        private const val EXPECTED_JAR_SHA256 =
            "ab779fd74cb995b89b0979e71adb0a1a839ff2d9a1b59d0813dab7a71759509f"
        private const val EXPECTED_SOURCE_SHA256 =
            "d9a4994c3dd33ea1bb729305470a2bcad4f5cd677be21b0f860524b563f1bab8"
        private const val EXPECTED_LICENSE_SHA256 =
            "a5681bf9b05db14d86776930017c647ad9e6e56ff6bbcfdf21e5848288dfaf1b"
        private const val EXPECTED_NETHER_PATHFINDER_SHA256 =
            "2ab97a3ef0d828eb8fc53adcbf78e92c645409eab10a8cff2646d52f64b11210"
        private const val NETHER_PATHFINDER_ENTRY = "META-INF/jars/nether-pathfinder-1.6.jar"

        private val vendorDirectory = Path.of("third_party", "baritone")
        private val vendorJar = vendorDirectory.resolve(JAR_NAME)
        private val sourceArchive = vendorDirectory.resolve(SOURCE_ARCHIVE_NAME)

        private val REQUIRED_BARITONE_ENTRIES = setOf(
            "baritone/BaritoneProvider.class",
            "baritone/api/BaritoneAPI.class",
            "baritone/api/IBaritone.class",
            "baritone/api/IBaritoneProvider.class",
            "fabric.mod.json",
            "mixins.baritone.json",
            NETHER_PATHFINDER_ENTRY,
        )
        private val REQUIRED_MIXINS = setOf(
            "MixinClientPlayerEntity",
            "MixinMinecraft",
            "MixinNetworkManager",
            "MixinWorldRenderer",
        )
        private val REQUIRED_NETHER_PATHFINDER_ENTRIES = setOf(
            "dev/babbaj/pathfinder/NetherPathfinder.class",
            "dev/babbaj/pathfinder/Octree.class",
            "dev/babbaj/pathfinder/PathSegment.class",
            "fabric.mod.json",
        )
    }
}
