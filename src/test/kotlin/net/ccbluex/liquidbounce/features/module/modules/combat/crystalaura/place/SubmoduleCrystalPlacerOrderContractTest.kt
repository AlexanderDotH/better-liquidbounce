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
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.place

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class SubmoduleCrystalPlacerOrderContractTest {

    @Test
    fun `placement tick preserves eligibility target rotation render and queue order`() {
        val source = Files.readString(Path.of(SOURCE))
        val tick = declaration(source, "fun tick(")

        assertInOrder(
            tick,
            "canAttemptPlacement()",
            "getSlot()",
            "CrystalAuraPlaceTargetFactory.updateTarget(excludeIds)",
            "removeFromRenderer()",
            "CrystalAuraPlaceTargetFactory.placementTarget",
            "resolvePlacementRotation(targetPos)",
            "prepareUnrotatedHitResult(rotation, targetPos)",
            "addToRenderer()",
            "updatePrevious(rotation)",
            "queuePlacing(rotation, targetPos, side)",
        )
    }

    @Test
    fun `rotation selection keeps only-above before crystal-aligned fallback`() {
        val source = Files.readString(Path.of(SOURCE))
        val selection = declaration(source, "private fun resolvePlacementRotation(")

        assertInOrder(selection, "if (onlyAbove)", "raytraceUpperBlockSide", "else", "findClosestPointOnBlock")
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private fun declaration(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        require(markerIndex >= 0) { "Missing declaration: $marker" }
        val openingBrace = source.indexOf('{', markerIndex)
        require(openingBrace >= 0) { "Missing declaration body: $marker" }
        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(markerIndex, index + 1)
            }
        }
        error("Unclosed declaration: $marker")
    }

    private companion object {
        const val SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/crystalaura/place/" +
                "SubmoduleCrystalPlacer.kt"
    }
}
