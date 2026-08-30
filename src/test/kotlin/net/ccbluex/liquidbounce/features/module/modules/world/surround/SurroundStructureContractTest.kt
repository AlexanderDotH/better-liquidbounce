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
package net.ccbluex.liquidbounce.features.module.modules.world.surround

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SurroundStructureContractTest {

    @Test
    fun `configuration and lifecycle retain their public contract`() {
        assertContainsAll(
            corpus,
            "multiEnumChoice(\"Features\"",
            "multiEnumChoice(\"DisableOn\"",
            "boolean(\"Instant\", true)",
            "bind(\"AddExtraLayer\")",
            "\"Protect\", true",
            "int(\"MinDestroyProgress\", 4, 0..9, \"stage\")",
            "\"ExtraLayer\", true",
            "boolean(\"Corners\", false)",
            "placer.disable()",
        )
    }

    @Test
    fun `protection and placement decisions keep their exact triggers`() {
        assertContainsAll(
            corpus,
            "entry.booleanValue",
            "breakingInfo.progress",
            "stage < minDestroyProgress",
            "isBlockedByEntitiesReturnCrystal()",
            "currentTarget = crystal",
            "NO_WASTE",
            "DIRECTIONS_EXCLUDING_UP",
            "addExtraLayerBlocks",
            "corners",
            "EXTEND",
            "placeInstantOnBlockUpdate",
        )
    }

    private fun assertContainsAll(source: String, vararg markers: String) {
        markers.forEach { marker -> assertTrue(marker in source, "Missing `$marker`") }
    }

    private companion object {
        val root: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world")
        val facade: String = Files.readString(root.resolve("ModuleSurround.kt"))
        val corpus: String = featureCorpus(facade, root.resolve("surround"))

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
