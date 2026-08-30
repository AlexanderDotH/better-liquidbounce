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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BaseFinderRenderProjectionTest {
    private val scope = BaseFinderRenderScope("server", "minecraft:overworld", worldEpoch = 7L)
    private val settings = BaseFinderRenderPlanSettings(
        minimumConfidence = 65,
        maximumDistance = 512.0,
        renderLimit = 32,
        boxRadius = 4.0,
        boxHeight = 6.0,
        lowConfidenceColor = Color4b(0xFF, 0xBA, 0x20),
        highConfidenceColor = Color4b(0xFF, 0x3C, 0xB4),
        showLabels = true,
        maxLabels = 8,
    )

    @Test
    fun `all mismatch kinds remain renderable and score details cannot alter their overlay`() {
        val overlaySettings = SeedMismatchRenderSettings(
            32.0, 8, Color4b(1, 2, 3), Color4b(4, 5, 6), Color4b(7, 8, 9), Color4b(10, 11, 12),
        )
        val cells = SeedMismatchKind.entries.mapIndexed { index, kind ->
            SeedMismatchCell(BaseCoordinate(index, 64, 0), kind)
        }
        val camera = Vec3(0.5, 64.5, 0.5)
        val baseline = BaseFinderSeedMismatchRenderPlanner.plan(cells, camera, overlaySettings)
        BaseFinderRenderPlanner.plan(request(scoredMarker()))
        val afterScoringProjection = BaseFinderSeedMismatchRenderPlanner.plan(cells, camera, overlaySettings)

        assertEquals(baseline, afterScoringProjection)
        assertEquals(SeedMismatchKind.entries, baseline.entries.map { it.cell.kind })
        assertEquals(
            listOf(
                overlaySettings.missingSolidColor,
                overlaySettings.unexpectedSolidColor,
                overlaySettings.utilityMismatchColor,
                overlaySettings.materialSwapColor,
            ),
            baseline.entries.map { it.color },
        )
    }

    @Test
    fun `domain snapshot converts coordinates and scope without sharing marker lists`() {
        val labelContributions = mutableListOf(BaseFinderLabelContribution("Unexpected solid", 40, "143 blocks"))
        val markers = mutableListOf(domainMarker(labelContributions))
        val snapshot = BaseFinderRenderSnapshot(
            9L, "snapshot-server", "minecraft:the_end", 3L, markers,
        )
        val request = BaseFinderRenderRequest.fromSnapshot(snapshot, Vec3.ZERO, settings, nowMillis = 789L)
        markers.clear()
        labelContributions.clear()

        assertEquals(BaseFinderRenderScope("snapshot-server", "minecraft:the_end", 9L, 3L), request.scope)
        assertEquals(Vec3(12.0, 70.0, -4.0), request.markers.single().anchor)
        assertEquals(91, request.markers.single().confidence)
        assertEquals(expectedEvidence(), request.markers.single().evidenceDetails)
        assertEquals(
            BaseFinderBounds(BaseCoordinate(8, 68, -7), BaseCoordinate(14, 72, -1)),
            request.markers.single().bounds,
        )
        assertEquals(789L, request.nowMillis)
    }

    private fun request(marker: BaseFinderRenderMarker) = BaseFinderRenderRequest(
        scope, Vec3(0.0, 64.0, 0.0), settings, listOf(marker), 1_000L,
    )

    private fun scoredMarker() = BaseFinderRenderMarker(
        "scored", scope.serverKey, scope.dimensionKey, scope.worldEpoch, Vec3(0.0, 64.0, 0.0), 80,
        listOf("Storage", "Automation", "Entities"), 123L,
        evidenceDetails = listOf(
            BaseFinderRenderEvidence(
                "Seed mismatch", 89,
                contributions = listOf(BaseFinderRenderContribution("Unexpected solid", 40, "64 blocks")),
            ),
        ),
        revision = scope.revision,
    )

    private fun domainMarker(contributions: List<BaseFinderLabelContribution>) = BaseFinderMarker(
        id = "domain",
        anchor = BaseCoordinate(12, 70, -4),
        confidence = 91,
        topEvidenceKeys = listOf("Storage"),
        updatedAtMillis = 456L,
        evidenceDetails = listOf(BaseFinderLabelEvidence("Seed mismatch", 85, emptyList(), contributions)),
        bounds = BaseFinderBounds(BaseCoordinate(8, 68, -7), BaseCoordinate(14, 72, -1)),
    )

    private fun expectedEvidence() = listOf(
        BaseFinderRenderEvidence(
            "Seed mismatch", 85,
            contributions = listOf(BaseFinderRenderContribution("Unexpected solid", 40, "143 blocks")),
        ),
    )
}
