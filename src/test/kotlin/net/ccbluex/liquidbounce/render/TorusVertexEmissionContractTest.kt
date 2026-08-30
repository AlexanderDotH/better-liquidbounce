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
package net.ccbluex.liquidbounce.render

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TorusVertexEmissionContractTest {

    @Test
    fun `torus quad retains counter-clockwise face vertex order`() {
        assertInOrder(
            torusSource,
            "addTorusVertex(pose, nextMainSin, nextMainCos, curTubeY, p3Radius, color)",
            "addTorusVertex(pose, curMainSin, curMainCos, curTubeY, p1Radius, color)",
            "addTorusVertex(pose, curMainSin, curMainCos, nextTubeY, p2Radius, color)",
            "addTorusVertex(pose, nextMainSin, nextMainCos, nextTubeY, p4Radius, color)",
        )
    }

    @Test
    fun `emitter retains radial coordinate projection and color assignment`() {
        assertInOrder(
            emitterSource,
            "radius * mainSin",
            "tubeY",
            "radius * mainCos",
            ".setColor(color)",
        )
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previousIndex + 1)
            assertTrue(index > previousIndex, "Expected `$marker` after index $previousIndex")
            previousIndex = index
        }
    }

    private companion object {
        val SOURCE_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/render")
        val torusSource: String = Files.readString(SOURCE_ROOT.resolve("VertexConsumerTorus.kt"))
        val emitterSource: String = Files.readString(SOURCE_ROOT.resolve("TorusVertexEmission.kt"))
    }
}
