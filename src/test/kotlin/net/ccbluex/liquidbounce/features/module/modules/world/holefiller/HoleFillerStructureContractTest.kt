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
package net.ccbluex.liquidbounce.features.module.modules.world.holefiller

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class HoleFillerStructureContractTest {

    @Test
    fun `configuration names defaults and placement lifecycle stay stable`() {
        assertInOrder(
            facade,
            "multiEnumChoice(\"Features\"",
            "int(\"Area\", 2, 1..5)",
            "enumChoice(\"Filter\"",
            "blocks(\"Blocks\"",
            "BlockPlacer(",
            "\"Placing\"",
        )
        assertContainsAll(corpus, "HoleManager.subscribe(this)", "HoleManager.unsubscribe(this)", "placer.disable()")
    }

    @Test
    fun `simple and smart targeting decisions retain their conditions`() {
        assertContainsAll(
            corpus,
            "ONLY_ONE_BY_ONE",
            "ONLY_WHEN_SELF_IN_HOLE",
            "SMART !in features",
            "availableItems == 0",
            "PREVENT_SELF_FILL",
            "entity.shouldBeAttacked()",
            "remainingItems1 -= holeSize",
            "return remainingItems",
            "CHECK_MOVEMENT",
            "angle >= 0.866",
        )
    }

    private fun assertContainsAll(source: String, vararg markers: String) {
        markers.forEach { marker -> assertTrue(marker in source, "Missing `$marker`") }
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "Expected `$marker` after index $previous")
            previous = index
        }
    }

    private companion object {
        val root: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world")
        val facade: String = Files.readString(root.resolve("ModuleHoleFiller.kt"))
        val corpus: String = featureCorpus(facade, root.resolve("holefiller"))

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
