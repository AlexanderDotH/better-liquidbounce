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

package net.ccbluex.liquidbounce.features.rotation.processors

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailRotationProcessorDebugBoundaryTest {

    @Test
    fun `fail diagnostics keep their order and running gates behind the neutral sink`() {
        val processor = source("features/rotation/processors/FailRotationProcessor.kt")

        assertFalse("features.module.modules.render" in processor)
        assertTrue(
            "handler<GameTickEvent>(priority = EventPriorityConvention.FIRST_PRIORITY)" in processor
        )
        assertTrue("get() = running && ticksElapsed < currentTransitionInDuration" in processor)
        assertTrue("if (this.running && isInFailState)" in processor)

        assertOrdered(
            processor,
            "DebugParameterSink.publish(this, \"Chance\") { chance }",
            "DebugParameterSink.publish(this, \"Duration\") { currentTransitionInDuration }",
            "DebugParameterSink.publish(this, \"Shift\") { shiftRotation }",
            "DebugParameterSink.publish(this, \"Elapsed\") { ticksElapsed }",
            "DebugParameterSink.publish(this, \"DeltaYaw\") { deltaYaw }",
            "DebugParameterSink.publish(this, \"DeltaPitch\") { deltaPitch }",
        )

        val adapter = source("bootstrap/liquidbounce/DebugGeometrySinkAdapter.kt")
        assertTrue(adapter.contains("DebugParameterSink.install { owner, name, value ->"))
        assertTrue(adapter.contains("debugOwner.debugParameter(name, value)"))
    }

    private fun source(relativePath: String): String = Path.of(
        "src/main/kotlin/net/ccbluex/liquidbounce/$relativePath"
    ).readText()

    private fun assertOrdered(source: String, vararg tokens: String) {
        var previousIndex = -1
        tokens.forEach { token ->
            val index = source.indexOf(token)
            assertTrue(index > previousIndex, "Missing or reordered token: $token")
            previousIndex = index
        }
    }
}
