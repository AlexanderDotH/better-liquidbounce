/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerAntiCheatClassifierTest {

    @Test
    fun `constant transaction steps retain every established classification`() {
        val cases = listOf(
            Case("Watchdog", "MC.HYPIXEL.NET", sequence(42, 1)),
            Case("Vulcan", null, sequence(-23767, 1)),
            Case("Matrix", null, sequence(100, 1)),
            Case("Matrix", null, sequence(-20000, 1)),
            Case("Grizzly", null, sequence(-32768, 1)),
            Case("Verus", null, sequence(10, 1)),
            Case("Errata", null, sequence(-8285, -1)),
            Case("Intave", null, sequence(-4000, -1)),
            Case("Grim", null, sequence(-2, -1)),
            Case("Karhu", null, sequence(-2998, -1)),
            Case("Polar", null, sequence(20, -1)),
            Case(null, null, sequence(10, 2)),
        )

        cases.forEach(::assertClassification)
    }

    @Test
    fun `irregular transaction signatures retain priority and fallback behavior`() {
        val cases = listOf(
            Case(null, "mc.hypixel.net", listOf(1, 2, 3, 4)),
            Case("Verus", null, listOf(10, 10, 50, 51, 52)),
            Case("Polar", null, listOf(0, 100, 99, 98, 97)),
            Case("Intave", null, listOf(-4001, -2000, 0, 5, 7)),
            Case("Old Vulcan", null, listOf(-30767, -30766, -25767, -25766, -25765)),
            Case("Unknown", null, listOf(1, 3, 6, 10, 15)),
        )

        cases.forEach(::assertClassification)
    }

    private fun assertClassification(case: Case) {
        assertEquals(
            case.expected,
            ServerAntiCheatClassifier.classify(case.address, case.transactions),
            case.toString(),
        )
    }

    private fun sequence(first: Int, step: Int) = List(5) { index -> first + index * step }

    private data class Case(
        val expected: String?,
        val address: String?,
        val transactions: List<Int>,
    )
}
