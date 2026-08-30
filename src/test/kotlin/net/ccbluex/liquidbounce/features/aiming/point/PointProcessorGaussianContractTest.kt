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

class PointProcessorGaussianContractTest {

    @Test
    fun `gaussian settings and focused update responsibilities remain explicit`() {
        val source = Files.readString(SOURCE)

        listOf(
            "\"YawOffset\"",
            "\"PitchOffset\"",
            "\"Chance\"",
            "\"Speed\"",
            "\"Tolerance\"",
            "\"Dynamic\"",
            "sampleYawFactor",
            "samplePitchFactor",
            "advanceOffset",
        ).forEach { contract ->
            assertTrue(contract in source, "Missing Gaussian point contract: $contract")
        }
        assertFalse("@Suppress(\"CognitiveComplexMethod\")" in source)
    }

    private companion object {
        val SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/aiming/point/PointProcessorGaussian.kt"
        )
    }
}
