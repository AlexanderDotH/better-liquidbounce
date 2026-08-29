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
package net.ccbluex.liquidbounce.features.litematica

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LitematicaDependencyContractTest {

    @Test
    fun `verified Litematica APIs stay compile only`() {
        val build = read(BUILD_FILE)
        val catalog = read(VERSION_CATALOG)

        assertTrue(build.contains("compileOnly(libs.litematica)"))
        assertTrue(build.contains("compileOnly(libs.malilib)"))
        assertTrue(build.contains("litematicaIntegrationTestRuntime"))
        assertTrue(build.contains("includeTags(\"litematica-integration\")"))
        assertTrue(build.contains("excludeTags(\"litematica-integration\")"))
        assertFalse(build.contains("include(libs.litematica)"))
        assertFalse(build.contains("include(libs.malilib)"))
        assertFalse(build.contains("runtimeOnly(libs.litematica)"))
        assertFalse(build.contains("runtimeOnly(libs.malilib)"))
        assertFalse(build.contains("jij(libs.litematica)"))
        assertFalse(build.contains("jij(libs.malilib)"))
        assertTrue(catalog.contains("litematica = \"jzraAo7b\""))
        assertTrue(catalog.contains("malilib = \"xKxhjDJ2\""))
    }

    @Test
    fun `Fabric metadata only suggests the verified optional versions`() {
        val metadata = read(FABRIC_METADATA)

        assertTrue(metadata.contains("\"litematica\": \"=0.28.4\""))
        assertTrue(metadata.contains("\"malilib\": \"=0.29.3\""))
        assertFalse(metadata.substringAfter("\"depends\"").substringBefore("\"suggests\"")
            .contains("\"litematica\""))
        assertFalse(metadata.substringAfter("\"depends\"").substringBefore("\"suggests\"")
            .contains("\"malilib\""))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val BUILD_FILE = "build.gradle.kts"
        const val VERSION_CATALOG = "gradle/libs.versions.toml"
        const val FABRIC_METADATA = "src/main/resources/fabric.mod.json"
    }
}
