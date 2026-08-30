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
package net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class StrongholdFinderStructureContractTest {

    @Test
    fun `settings and reset behavior retain their wire contract`() {
        assertContainsAll(
            corpus,
            "float(\"Sigma\", 0.03f, 0.005f..0.20f, \"°\")",
            "int(\"HypothesisCount\", 20000, 2000..100000)",
            "boolean(\"RequireSameStrongholdAcrossThrows\", true)",
            "int(\"SampleDelayTicks\", 2, 0..10)",
            "int(\"ShowTopCandidates\", 3, 1..10)",
            "boolean(\"ResetOnWorldChange\", true)",
            "resetState()",
        )
    }

    @Test
    fun `eye portal prediction and rendering order remain characterized`() {
        assertContainsAll(
            corpus,
            "Items.ENDER_EYE",
            "ClientboundAddEntityPacket",
            "ClientboundLevelChunkWithLightPacket",
            "sampleDelayTicks",
            "minEyeHorizontalSpeed",
            "StrongholdBayesianEstimator.estimate",
            "announcePrediction",
            "Blocks.END_PORTAL",
            "Blocks.END_PORTAL_FRAME",
            "detectedPortalBlocks.isNotEmpty()",
            "renderRays",
            "renderBestChunk",
            "renderTopChunks",
        )
    }

    private fun assertContainsAll(source: String, vararg markers: String) {
        markers.forEach { marker -> assertTrue(marker in source, "Missing `$marker`") }
    }

    private companion object {
        val root: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world")
        val facade: String = Files.readString(root.resolve("ModuleStrongholdFinder.kt"))
        val corpus: String = featureCorpus(facade, root.resolve("strongholdfinder"))

        fun featureCorpus(facade: String, featureRoot: Path): String = buildList {
            add(facade)
            if (Files.exists(featureRoot)) Files.walk(featureRoot).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                    .sorted()
                    .forEach { add(Files.readString(it)) }
            }
        }.joinToString("\n")
    }
}
