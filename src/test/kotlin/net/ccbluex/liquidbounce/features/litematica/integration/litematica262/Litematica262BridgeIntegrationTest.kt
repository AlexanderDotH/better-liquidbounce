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
package net.ccbluex.liquidbounce.features.litematica.integration.litematica262

import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaBridgeResult
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Tag("litematica-integration")
class Litematica262BridgeIntegrationTest {

    @Test
    fun `exact external artifacts satisfy every adapter capability`() {
        assertModVersion("fi.dy.masa.litematica.Litematica", "litematica", "0.28.4")
        assertModVersion("fi.dy.masa.malilib.MaLiLib", "malilib", "0.29.3")

        val result = assertIs<LitematicaBridgeResult.Ready>(Litematica262BridgeFactory().create())
        try {
            assertEquals("0.28.4", result.port.versions.litematica)
            assertEquals("0.29.3", result.port.versions.malilib)
            assertTrue(result.port.capabilities.missingRequired().isEmpty())
        } finally {
            result.port.close()
        }
    }

    private fun assertModVersion(className: String, modId: String, version: String) {
        val type = Class.forName(className, false, javaClass.classLoader)
        val jar = Path.of(type.protectionDomain.codeSource.location.toURI())
        val metadata = ZipFile(jar.toFile()).use { zip ->
            val entry = checkNotNull(zip.getEntry("fabric.mod.json"))
            zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        assertTrue(metadata.containsJsonString("id", modId))
        assertTrue(metadata.containsJsonString("version", version))
    }

    private fun String.containsJsonString(key: String, value: String): Boolean =
        Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"${Regex.escape(value)}\"").containsMatchIn(this)
}
