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
package net.ccbluex.liquidbounce.features.module.modules.world.autofarm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AutoFarmStructureContractTest {

    @Test
    fun `settings and runtime lifecycle stay stable`() {
        assertContainsAll(
            corpus,
            "float(\"Range\", 5F, 1F..6F)",
            "float(\"WallRange\", 0f, 0F..6F)",
            "intRange(\"InteractDelay\", 2..3, 1..15, \"ticks\")",
            "boolean(\"DisableOnFullInventory\", false)",
            "\"AutoPlant\", true",
            "\"AutoUseBoneMeal\", false",
            "boolean(\"UseFortune\", true)",
            "ChunkScanner.subscribe(AutoFarmBlockTracker)",
            "ChunkScanner.unsubscribe(AutoFarmBlockTracker)",
        )
    }

    @Test
    fun `harvest plant and fertilize targeting retain priority and reach checks`() {
        assertContainsAll(
            corpus,
            "updateTargetToHarvest",
            "AutoPlaceCrops.enabled",
            "updateTargetToPlantable",
            "AutoUseBoneMeal.enabled",
            "updateTargetToFertilizable",
            "findPlantableSides",
            "getNearestPointOnSide",
            "calculateAngleToPlayerEyeCosine",
            "raytraceBlockSide",
            "RotationManager.setRotationTarget",
        )
    }

    @Test
    fun `planner owns its target policy boundary without importing feature runtime`() {
        val reverseImports = plannerCorpus.lineSequence()
            .filter { it.startsWith("import $featurePackage.") }
            .filterNot { it.startsWith("import $featurePackage.planner.") }
            .toList()

        assertTrue(reverseImports.isEmpty(), "Planner imports feature runtime: $reverseImports")
        assertContainsAll(plannerCorpus, "TargetSelectionPolicy", "PlantingPolicy")
    }

    private fun assertContainsAll(source: String, vararg markers: String) {
        markers.forEach { marker -> assertTrue(marker in source, "Missing `$marker`") }
    }

    private companion object {
        val root: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/autofarm",
        )
        const val featurePackage = "net.ccbluex.liquidbounce.features.module.modules.world.autofarm"
        val facade: String = Files.readString(root.resolve("ModuleAutoFarm.kt"))
        val corpus: String = featureCorpus(facade, root)
        val plannerCorpus: String = featureCorpus("", root.resolve("planner"))

        fun featureCorpus(facade: String, featureRoot: Path): String = buildList {
            add(facade)
            Files.walk(featureRoot).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                    .filter { it.fileName.toString() != "ModuleAutoFarm.kt" }
                    .sorted()
                    .forEach { add(Files.readString(it)) }
            }
        }.joinToString("\n")
    }
}
