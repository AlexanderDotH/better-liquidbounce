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
package net.ccbluex.liquidbounce.render.trajectory

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TrajectoryInfoRendererStructureTest {

    @Test
    fun `public factory and simulation facade remain on TrajectoryInfoRenderer`() {
        val source = readSource("TrajectoryInfoRenderer.kt")

        assertTrue(source.contains("@JvmStatic\n        @JvmOverloads"))
        assertTrue(source.contains("fun getHypotheticalTrajectory("))
        assertTrue(source.contains("fun runSimulation("))
        assertTrue(source.contains("SimulationResult"))
        assertFalse(source.contains("ModuleFreeze"))
    }

    private fun readSource(name: String): String = Files.readString(
        Path.of("src/main/kotlin/net/ccbluex/liquidbounce/render/trajectory", name)
    )
}
