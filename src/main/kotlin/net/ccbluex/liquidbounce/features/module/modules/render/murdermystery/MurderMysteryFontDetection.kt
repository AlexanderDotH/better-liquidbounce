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

package net.ccbluex.liquidbounce.features.module.modules.render.murdermystery

import net.ccbluex.liquidbounce.config.gson.util.readJson
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import java.awt.Color

object MurderMysteryFontDetection {

    private const val FILE_NAME = "hypixel_mm_letters.json"

    private val LETTER_MAP: Map<String, BooleanArray> = run {
        val stream =
            ModuleMurderMystery.javaClass.getResourceAsStream("/resources/liquidbounce/data/$FILE_NAME")

        checkNotNull(stream) { "Unable to find $FILE_NAME!" }

        // We should not use interface here
        stream.readJson<HashMap<String, BooleanArray>>()
    }

    fun readContractLine(mapData: MapItemSavedData): String {
        val contractLine = filterContractLine(extractBitmapFromMap(mapData))
        val output = StringBuilder()
        var lastNonEmptyScanline = -1
        var emptyScanlines = 0
        for (x in 0 until 128) {
            val isEmpty = isEmptyScanline(contractLine, x)
            if (isEmpty) {
                if (emptyScanlines > 3) {
                    output.append(' ')
                    emptyScanlines = 0
                } else {
                    emptyScanlines++
                }
            }
            if (lastNonEmptyScanline != -1 && isEmpty) {
                output.append(readLetter(contractLine, lastNonEmptyScanline, x))
                lastNonEmptyScanline = -1
            }
            if (!isEmpty && lastNonEmptyScanline == -1) {
                lastNonEmptyScanline = x
                emptyScanlines = 0
            }
        }
        return output.trim { it <= ' ' }.toString()
    }

    private fun isEmptyScanline(contractLine: IntArray, x: Int): Boolean =
        (0 until 7).none { y -> contractLine[128 * y + x] == -1 }

    private fun readLetter(contractLine: IntArray, startX: Int, endX: Int): String {
        val width = endX - startX
        val height = 7
        val fingerprint = BooleanArray(width * height)
        for (y in 0 until height) {
            val sourceOffset = 128 * y + startX
            for (x in 0 until width) {
                fingerprint[y * width + x] = contractLine[sourceOffset + x] == -1
            }
        }
        return LETTER_MAP.entries.firstOrNull { (_, expected) ->
            expected.contentEquals(fingerprint)
        }?.key ?: "?"
    }

    private fun filterContractLine(rgb: IntArray): IntArray {
        val contractLine = IntArray(128 * 7)

        for (y in 0 until 7) {
            for (x in 0 until 128) {
                var newRGB = rgb[128 * 105 + y * 128 + x]

                newRGB =
                    if (newRGB == Color(123, 102, 62).rgb || newRGB == Color(143, 119, 72).rgb) {
                        0
                    } else {
                        -1
                    }

                contractLine[128 * y + x] = newRGB
            }
        }
        return contractLine
    }

    private fun extractBitmapFromMap(mapData: MapItemSavedData): IntArray {
        val rgb = IntArray(128 * 128)

        for (i in rgb.indices) {
            val color = MapColor.getColorFromPackedId(mapData.colors[i].toInt())

            val r = color and 0xFF
            val g = (color ushr 8) and 0xFF
            val b = (color ushr 16) and 0xFF

            rgb[i] = Color(r, g, b).rgb
        }
        return rgb
    }
}
