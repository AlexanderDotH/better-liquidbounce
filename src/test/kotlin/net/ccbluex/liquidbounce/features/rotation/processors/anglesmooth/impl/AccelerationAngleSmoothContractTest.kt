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
package net.ccbluex.liquidbounce.features.rotation.processors.anglesmooth.impl

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccelerationAngleSmoothContractTest {

    @Test
    fun `turn speed keeps acceleration error and deceleration order without structural suppression`() {
        val source = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/rotation/processors/anglesmooth/impl/" +
                "AccelerationAngleSmooth.kt",
        ).readText()
        val body = source.substringAfter("private fun computeTurnSpeed(")
            .substringBefore("private fun calculateAcceleration(")

        assertFalse("CognitiveComplexMethod" in source)
        assertOrdered(
            body,
            "sigmoidDeceleration.computeDecelerationFactor(diff.length())",
            "dynamicAcceleration.enabled && crosshair",
            "dynamicAcceleration.coefDistance * distance",
            "this.errorProviders",
            "dynamicAcceleration.yawCrosshairAccel to dynamicAcceleration.pitchCrosshairAccel",
            "yawAcceleration to pitchAcceleration",
            "calculateAcceleration(diff.deltaYaw, prevDiff.deltaYaw",
            "calculateAcceleration(diff.deltaPitch, prevDiff.deltaPitch",
            "prevDiff.deltaYaw + yawAccel + yawErrorProvider.getError(yawAccel)",
            "prevDiff.deltaPitch + pitchAccel + pitchErrorProvider.getError(pitchAccel)",
        )
    }

    private fun assertOrdered(source: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previousIndex + 1)
            assertTrue(index > previousIndex, "Expected `$marker` after index $previousIndex")
            previousIndex = index
        }
    }
}
