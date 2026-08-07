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
package net.ccbluex.liquidbounce.integration.theme.component.components.seedcracker

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceId
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CandidateSource
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CandidateVerification
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackScope
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCandidate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerConflictReport
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.StructureType
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.utils.render.Alignment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeedCrackerHudComponentTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `component has stable dimensions and a non-overlapping bottom-left default`() {
        assertEquals(210f, SeedCrackerHudLayout.WIDTH)
        assertEquals(54f, SeedCrackerHudLayout.HEIGHT)
        assertEquals(Alignment.ScreenAxisX.LEFT, SeedCrackerHudLayout.HORIZONTAL_ALIGNMENT)
        assertEquals(Alignment.ScreenAxisY.BOTTOM, SeedCrackerHudLayout.VERTICAL_ALIGNMENT)
        assertEquals(16, SeedCrackerHudLayout.HORIZONTAL_OFFSET)
        assertEquals(16, SeedCrackerHudLayout.VERTICAL_OFFSET)
    }

    @Test
    fun `native widget renders only while both HUD and SeedCracker are visible`() {
        assertTrue(shouldRenderSeedCrackerHud(true, false, false, true))
        assertFalse(shouldRenderSeedCrackerHud(false, false, false, true))
        assertFalse(shouldRenderSeedCrackerHud(true, true, false, true))
        assertFalse(shouldRenderSeedCrackerHud(true, false, true, true))
        assertFalse(shouldRenderSeedCrackerHud(true, false, false, false))
    }

    @Test
    fun `progress bar has breathing room below the text and above the card edge`() {
        val geometry = seedCrackerHudProgressGeometry(
            width = SeedCrackerHudLayout.WIDTH,
            height = SeedCrackerHudLayout.HEIGHT,
        )

        assertEquals(7f, geometry.left)
        assertEquals(49f, geometry.top)
        assertEquals(203f, geometry.right)
        assertEquals(51f, geometry.bottom)
        assertTrue(SeedCrackerHudLayout.LAST_LINE_BOTTOM + 2f <= geometry.top)
        assertTrue(geometry.bottom + 2f <= SeedCrackerHudLayout.HEIGHT)
    }

    @Test
    fun `progress fill is clamped and scales across the complete track`() {
        val geometry = seedCrackerHudProgressGeometry(width = 210f, height = 54f)

        assertEquals(7f, geometry.fillRight(-1f))
        assertEquals(105f, geometry.fillRight(0.5f))
        assertEquals(203f, geometry.fillRight(1f))
        assertEquals(203f, geometry.fillRight(2f))
    }

    @Test
    fun `progress normalization handles evidence ratios and invalid solver values`() {
        assertEquals(0f, normalizedSeedCrackerHudProgress(0.0, 100.0))
        assertEquals(0.5f, normalizedSeedCrackerHudProgress(50.0, 100.0))
        assertEquals(1f, normalizedSeedCrackerHudProgress(125.0, 100.0))
        assertEquals(0f, normalizedSeedCrackerHudProgress(Double.NaN, 100.0))
        assertEquals(0f, normalizedSeedCrackerHudProgress(1.0, 0.0))
    }

    @Test
    fun `verified candidate plan keeps the complete signed seed separate from compact source metadata`() {
        val candidate = SeedCandidate(
            scope = CrackScope("server", "minecraft:the_nether"),
            seed = Long.MIN_VALUE,
            source = CandidateSource.NETHER_BEDROCK,
            evidenceIds = setOf(EvidenceId("source")),
            verificationEvidenceIds = setOf(EvidenceId("held-out")),
            verification = CandidateVerification.VERIFIED,
        )

        val lines = seedCrackerHudCandidateLinePlan(candidate)

        assertEquals(listOf(SeedCrackerHudLineRole.RESULT, SeedCrackerHudLineRole.ACTION), lines.map { it.role })
        assertEquals("overlay.candidate.worldSeed", lines[0].translationKey)
        assertEquals(listOf(Long.MIN_VALUE.toString()), lines[0].arguments)
        assertEquals("overlay.candidate.verified.netherBedrock", lines[1].translationKey)
        assertEquals(emptyList<String>(), lines[1].arguments)
    }

    @Test
    fun `conflict labels name structure and chunk while preserving the copyable full id`() {
        val report = SeedCrackerConflictReport.inconsistentStructures(
            detail = "No common seed",
            evidence = listOf(
                SeedCrackerConflictReport.StructureEvidence(
                    id = EvidenceId("shipwreck:-104:193:-3136749755318404772"),
                    type = StructureType.SHIPWRECK,
                    chunkX = -104,
                    chunkZ = 193,
                ),
            ),
        )

        assertEquals("shipwreck @ -104, 193", report.evidence.single().displayLabel)
        assertEquals("shipwreck:-104:193:-3136749755318404772", report.evidence.single().id.value)
    }
}
