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

internal object ServerAntiCheatClassifier {

    fun classify(address: String?, transactions: List<Int>): String? {
        if (transactions.size < MINIMUM_TRANSACTION_COUNT) {
            return null
        }
        if (address?.endsWith("hypixel.net", true) == true) {
            return "Watchdog"
        }

        val differences = transactions.windowed(2) { it[1] - it[0] }
        val first = transactions.first()
        if (differences.all { it == differences.first() }) {
            return classifyConstantStep(first, differences.first())
        }

        return classifyIrregular(transactions, differences)
    }

    private fun classifyConstantStep(first: Int, step: Int): String? = when (step) {
        1 -> classifyAscending(first)
        -1 -> classifyDescending(first)
        else -> null
    }

    private fun classifyAscending(first: Int): String = when (first) {
        in -23772..-23762 -> "Vulcan"
        in 95..105, in -20005..-19995 -> "Matrix"
        in -32773..-32762 -> "Grizzly"
        else -> "Verus"
    }

    private fun classifyDescending(first: Int): String = when {
        first in -8287..-8280 -> "Errata"
        first < -3000 -> "Intave"
        first in -5..0 -> "Grim"
        first in -3000..-2995 -> "Karhu"
        else -> "Polar"
    }

    private fun classifyIrregular(transactions: List<Int>, differences: List<Int>): String = when {
        isDuplicateThenIncrementing(transactions) -> "Verus"
        isLargeThenDescending(differences) -> "Polar"
        transactions.first() < -3000 && transactions.contains(0) -> "Intave"
        isOldVulcan(transactions) -> "Old Vulcan"
        else -> "Unknown"
    }

    private fun isDuplicateThenIncrementing(transactions: List<Int>) =
        transactions.take(2).let { it[0] == it[1] } &&
            transactions.drop(2).windowed(2).all { it[1] - it[0] == 1 }

    private fun isLargeThenDescending(differences: List<Int>) =
        differences.take(2).let { it[0] >= 100 && it[1] == -1 } &&
            differences.drop(2).all { it == -1 }

    private fun isOldVulcan(transactions: List<Int>) =
        transactions.take(3) == listOf(-30767, -30766, -25767) &&
            transactions.drop(3).windowed(2).all { it[1] - it[0] == 1 }

    private const val MINIMUM_TRANSACTION_COUNT = 5
}
