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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

class BaseFinderRendererTest {

    private val scope = BaseFinderRenderScope("server", "minecraft:overworld", worldEpoch = 7L)
    private val settings = BaseFinderRenderSettings(
        minimumConfidence = 65,
        maximumDistance = 512.0,
        renderLimit = 32,
        boxRadius = 4.0,
        boxHeight = 6.0,
        lowConfidenceColor = Color4b(0xFF, 0xBA, 0x20),
        highConfidenceColor = Color4b(0xFF, 0x3C, 0xB4),
        showLabels = true,
        maxLabels = 8,
        pulse = true,
        pulseSpeedHz = 0.8,
        pulseAmount = 0.15,
    )

    @Test
    fun `confidence threshold is inclusive and scope must match completely`() {
        val request = request(
            marker("included", confidence = 65),
            marker("below", confidence = 64),
            marker("wrong-server", confidence = 100, serverKey = "other"),
            marker("wrong-dimension", confidence = 100, dimensionKey = "minecraft:the_nether"),
            marker("old-world", confidence = 100, worldEpoch = 6L),
            marker("old-revision", confidence = 100, revision = 6L),
        )

        val batch = BaseFinderRenderPlanner.plan(request)

        assertEquals(listOf("included"), batch.entries.map { it.marker.id })
    }

    @Test
    fun `markers are distance filtered nearest first and deterministically limited`() {
        val request = request(
            marker("far", x = 30.0),
            marker("tie-b", x = 10.0),
            marker("outside", x = 513.0),
            marker("tie-a", x = -10.0),
        ).copy(settings = settings.copy(maximumDistance = 512.0, renderLimit = 2))

        val batch = BaseFinderRenderPlanner.plan(request)

        assertEquals(listOf("tie-a", "tie-b"), batch.entries.map { it.marker.id })
        assertEquals(listOf(10.0, 10.0), batch.entries.map { it.distance })
    }

    @Test
    fun `world and camera relative boxes use the exact configured bounds`() {
        val request = request(marker("box", x = 100.0, y = 64.0, z = -200.0))
            .copy(cameraPosition = Vec3(90.0, 60.0, -190.0))

        val entry = BaseFinderRenderPlanner.plan(request).entries.single()

        assertBox(AABB(96.5, 63.75, -203.5, 104.5, 70.0, -195.5), entry.worldBox)
        assertBox(AABB(6.5, 3.75, -13.5, 14.5, 10.0, -5.5), entry.cameraRelativeBox)
        assertEquals(Vec3(100.5, 70.25, -199.5), entry.labelPosition)
    }

    @Test
    fun `confidence gradient reaches both colors and handles a threshold of one hundred`() {
        val low = BaseFinderRenderPlanner.plan(request(marker("low", confidence = 65))).entries.single()
        val high = BaseFinderRenderPlanner.plan(request(marker("high", confidence = 100))).entries.single()
        val only = BaseFinderRenderPlanner.plan(
            request(marker("only", confidence = 100)).copy(settings = settings.copy(minimumConfidence = 100))
        ).entries.single()

        assertEquals(Color4b(0xFF, 0xBA, 0x20), low.color)
        assertEquals(Color4b(0xFF, 0x3C, 0xB4), high.color)
        assertEquals(Color4b(0xFF, 0x3C, 0xB4), only.color)
    }

    @Test
    fun `stable pulse remains between eighty five and one hundred percent`() {
        val first = BaseFinderRenderPlanner.pulseMultiplier("alpha", 1_234L, speedHz = 0.8, amount = 0.15)
        val repeated = BaseFinderRenderPlanner.pulseMultiplier("alpha", 1_234L, speedHz = 0.8, amount = 0.15)
        val anotherPhase = BaseFinderRenderPlanner.pulseMultiplier("beta", 1_234L, speedHz = 0.8, amount = 0.15)

        assertEquals(first, repeated)
        assertTrue(first in 0.85..1.0)
        assertTrue(anotherPhase in 0.85..1.0)
        assertNotEquals(first, anotherPhase)
    }

    @Test
    fun `direct colors and glow mask share confidence color with intended alpha`() {
        val entry = BaseFinderRenderPlanner.plan(request(marker("color"), nowMillis = 0L)).entries.single()

        assertEquals(entry.color.with(a = (24 * entry.pulseMultiplier).roundToInt()), entry.faceColor)
        assertEquals(entry.color.with(a = (150 * entry.pulseMultiplier).roundToInt()), entry.outlineColor)
        assertEquals(entry.color.with(a = (255 * entry.pulseMultiplier).roundToInt()), entry.glowMaskColor)
    }

    @Test
    fun `labels are limited independently and contain score distance coordinates and top evidence`() {
        val batch = BaseFinderRenderPlanner.plan(
            request(
                marker("near", x = 3.0, y = 68.0, z = -7.0, confidence = 87),
                marker("far", x = 80.0, confidence = 80),
            ).copy(settings = settings.copy(maxLabels = 1))
        )

        assertEquals(1, batch.labels.size)
        assertEquals("Base 87% • 9m", batch.labels.single().headline)
        assertEquals("3 68 -7 • Storage + Automation", batch.labels.single().details)
    }

    @Test
    fun `labels use localized planner strings`() {
        val localized = settings.copy(
            baseLabel = "Basis",
            unknownEvidenceLabel = "Unbekannt",
            distanceSuffix = " Blöcke",
        )
        val marker = marker("localized", x = 3.0, confidence = 87).copy(topEvidenceKeys = emptyList())

        val label = BaseFinderRenderPlanner.plan(request(marker).copy(settings = localized)).labels.single()

        assertEquals("Basis 87% • 3 Blöcke", label.headline)
        assertEquals("3 64 0 • Unbekannt", label.details)
    }

    @Test
    fun `empty batch contributes zero times and a populated batch exactly once`() {
        var emptyContributions = 0
        val empty = BaseFinderRenderPlanner.plan(request(marker("below", confidence = 64)))
        val emptyResult = empty.contributeGlowIfPresent { emptyContributions++ }

        var fullContributions = 0
        val full = BaseFinderRenderPlanner.plan(request(marker("one"), marker("two", x = 2.0)))
        val fullResult = full.contributeGlowIfPresent { fullContributions++ }

        assertFalse(emptyResult)
        assertEquals(0, emptyContributions)
        assertTrue(fullResult)
        assertEquals(1, fullContributions)
    }

    @Test
    fun `render batch keeps an immutable evidence snapshot`() {
        val evidence = mutableListOf("Storage", "Automation")
        val source = marker("snapshot").copy(topEvidenceKeys = evidence)

        val batch = BaseFinderRenderPlanner.plan(request(source))
        evidence.clear()

        assertEquals(listOf("Storage", "Automation"), batch.entries.single().marker.topEvidenceKeys)
        assertEquals("0 64 0 • Storage + Automation", batch.labels.single().details)
    }

    @Test
    fun `domain snapshot converts coordinates and scope without sharing marker lists`() {
        val markers = mutableListOf(
            BaseFinderMarker(
                id = "domain",
                anchor = BaseCoordinate(12, 70, -4),
                confidence = 91,
                topEvidenceKeys = listOf("Storage"),
                updatedAtMillis = 456L,
            )
        )
        val snapshot = BaseFinderRenderSnapshot(
            worldEpoch = 9L,
            serverKey = "snapshot-server",
            dimensionKey = "minecraft:the_end",
            revision = 3L,
            markers = markers,
        )

        val request = BaseFinderRenderRequest.fromSnapshot(snapshot, Vec3.ZERO, settings, nowMillis = 789L)
        markers.clear()

        assertEquals(BaseFinderRenderScope("snapshot-server", "minecraft:the_end", 9L, 3L), request.scope)
        assertEquals(Vec3(12.0, 70.0, -4.0), request.markers.single().anchor)
        assertEquals(91, request.markers.single().confidence)
        assertEquals(789L, request.nowMillis)
    }

    private fun request(
        vararg markers: BaseFinderRenderMarker,
        nowMillis: Long = 1_000L,
    ) = BaseFinderRenderRequest(
        scope = scope,
        cameraPosition = Vec3(0.0, 64.0, 0.0),
        settings = settings,
        markers = markers.toList(),
        nowMillis = nowMillis,
    )

    private fun marker(
        id: String,
        x: Double = 0.0,
        y: Double = 64.0,
        z: Double = 0.0,
        confidence: Int = 80,
        serverKey: String = scope.serverKey,
        dimensionKey: String = scope.dimensionKey,
        worldEpoch: Long = scope.worldEpoch,
        revision: Long = scope.revision,
    ) = BaseFinderRenderMarker(
        id = id,
        serverKey = serverKey,
        dimensionKey = dimensionKey,
        worldEpoch = worldEpoch,
        anchor = Vec3(x, y, z),
        confidence = confidence,
        topEvidenceKeys = listOf("Storage", "Automation", "Entities"),
        updatedAtMillis = 123L,
        revision = revision,
    )

    private fun assertBox(expected: AABB, actual: AABB) {
        assertEquals(expected.minX, actual.minX)
        assertEquals(expected.minY, actual.minY)
        assertEquals(expected.minZ, actual.minZ)
        assertEquals(expected.maxX, actual.maxX)
        assertEquals(expected.maxY, actual.maxY)
        assertEquals(expected.maxZ, actual.maxZ)
    }
}
