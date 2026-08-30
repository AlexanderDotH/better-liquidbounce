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
        val dependencies = read(DEPENDENCY_FILE)
        val testing = read(TESTING_FILE)
        val catalog = read(VERSION_CATALOG)

        assertTrue(dependencies.contains("add(\"compileOnly\", library(\"litematica\"))"))
        assertTrue(dependencies.contains("add(\"compileOnly\", library(\"malilib\"))"))
        assertTrue(dependencies.contains("litematicaIntegrationTestRuntime"))
        assertTrue(testing.contains("includeTags(\"litematica-integration\")"))
        assertTrue(testing.contains("excludeTags(\"litematica-integration\")"))
        assertFalse(dependencies.contains("add(\"include\", library(\"litematica\"))"))
        assertFalse(dependencies.contains("add(\"include\", library(\"malilib\"))"))
        assertFalse(dependencies.contains("add(\"runtimeOnly\", library(\"litematica\"))"))
        assertFalse(dependencies.contains("add(\"runtimeOnly\", library(\"malilib\"))"))
        assertFalse(dependencies.contains("add(\"jij\", library(\"litematica\"))"))
        assertFalse(dependencies.contains("add(\"jij\", library(\"malilib\"))"))
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
        const val DEPENDENCY_FILE = "gradle/game-dependencies.gradle.kts"
        const val TESTING_FILE = "gradle/testing.gradle.kts"
        const val VERSION_CATALOG = "gradle/libs.versions.toml"
        const val FABRIC_METADATA = "src/main/resources/fabric.mod.json"
    }
}
