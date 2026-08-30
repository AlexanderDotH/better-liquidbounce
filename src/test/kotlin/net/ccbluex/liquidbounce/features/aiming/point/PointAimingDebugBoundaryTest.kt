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

package net.ccbluex.liquidbounce.features.aiming.point

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class PointAimingDebugBoundaryTest {

    @Test
    fun `point aiming publishes diagnostics without depending on render modules`() {
        val sources = Files.walk(SOURCE_ROOT).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }
                .map(Files::readString)
                .toList()
        }
        val combinedSource = sources.joinToString("\n")

        assertFalse("features.module.modules.render" in combinedSource)
        listOf(
            "DebugParameterSink.publish(this, \"Delay\")",
            "DebugParameterSink.publish(this, \"Threshold^2\")",
            "DebugParameterSink.publish(this, \"Distance^2\")",
            "DebugGeometrySink.publish(parent, \"Points\")",
            "DebuggedPoint(point, color.argb, 0.05)",
        ).forEach { contract ->
            assertTrue(contract in combinedSource, "Missing point diagnostic contract: $contract")
        }
    }

    private companion object {
        val SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/aiming/point"
        )
    }
}
