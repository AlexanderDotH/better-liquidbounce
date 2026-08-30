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
package net.ccbluex.liquidbounce.common.interop

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

class ScoreboardEntryOrderTest {

    @AfterEach
    fun resetProvider() = ScoreboardEntryOrder.resetForTests()

    @Test
    fun `bridge fails closed until the hud comparator is installed`() {
        assertThrows(IllegalStateException::class.java, ScoreboardEntryOrder::comparator)
    }

    @Test
    fun `bridge returns the exact comparator supplied by hud wiring`() {
        val comparator = Comparator<net.minecraft.world.scores.PlayerScoreEntry> { _, _ -> 0 }

        ScoreboardEntryOrder.install { comparator }

        assertSame(comparator, ScoreboardEntryOrder.comparator())
    }

    @Test
    fun `scoreboard mapping retains vanilla filter order limit and sanitation`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/interop/protocol/rest/v1/game/ScoreboardData.kt"
        ))
        val operations = listOf(
            ".filter { score: PlayerScoreEntry -> !score.isHidden }",
            ".sortedWith(ScoreboardEntryOrder.comparator())",
            ".take(15)",
            ".mapToArray { entry -> entry.toSidebarEntry(this, numberFormat) }",
        )

        assertTrue(operations.all(source::contains))
        assertTrue(operations.zipWithNext().all { (first, second) -> source.indexOf(first) < source.indexOf(second) })
        assertTrue(source.contains("PlayerTeam.formatNameForTeam(team, ownerName()).sanitizeForeignInput()"))
        assertTrue(source.contains("formatValue(numberFormat).sanitizeForeignInput()"))
    }
}
