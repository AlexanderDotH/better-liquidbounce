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
package net.ccbluex.liquidbounce.features.module.modules.world.autotimestamp

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object AutoTimestampLines {

    private const val DATE_LINE = 2
    private const val SIGNATURE_LINE = 3
    private const val SIGN_LINE_COUNT = 4

    private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ROOT)

    fun append(
        lines: Array<String>,
        date: LocalDate,
        playerName: String,
        fitLine: (String) -> String,
    ): Boolean {
        require(lines.size == SIGN_LINE_COUNT) { "A sign must contain exactly four lines" }

        if (lines[DATE_LINE].isNotEmpty() || lines[SIGNATURE_LINE].isNotEmpty()) {
            return false
        }

        lines[DATE_LINE] = fitLine(date.format(dateFormatter))
        lines[SIGNATURE_LINE] = fitLine("- $playerName")
        return true
    }
}
