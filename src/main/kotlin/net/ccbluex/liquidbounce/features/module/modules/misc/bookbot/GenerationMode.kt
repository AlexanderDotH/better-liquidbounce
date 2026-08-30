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

package net.ccbluex.liquidbounce.features.module.modules.misc.bookbot

import it.unimi.dsi.fastutil.ints.IntArrayList
import net.ccbluex.liquidbounce.config.types.group.Mode
import okio.buffer
import okio.source
import java.util.Random
import java.util.stream.IntStream

internal sealed class GenerationMode(name: String) : Mode(name) {
    internal val random = Random()
    val pages by int("Pages", 50, 0..100)
    abstract fun generate(): IntStream

    object Random : GenerationMode("Random") {
        private val asciiOnly by boolean("AsciiOnly", false)
        private val allowSpace by boolean("AllowSpace", true)

        override fun generate(): IntStream {
            val origin = if (asciiOnly) 0x21 else 0x0800
            val bound = if (asciiOnly) 0x7E else 0x10FFFF
            return random.ints(origin, bound).filter { allowSpace || !Character.isWhitespace(it) }
        }
    }

    object File : GenerationMode("File") {
        private const val MAX_CODE_POINTS: Long = 64 * 1024 * 1024
        private val cyclic by boolean("Cyclic", true)
        private val source = file("Source", supportedExtensions = setOf("txt"))

        override fun generate(): IntStream {
            val file = source.absoluteFile.takeIf {
                it.exists() && it.isFile && it.canRead() && it.length() != 0L
            } ?: return IntStream.empty()
            val codePoints = IntArrayList(minOf(MAX_CODE_POINTS, file.length()).toInt() / 3)
            file.source().buffer().use {
                while (!it.exhausted() && codePoints.size < MAX_CODE_POINTS) {
                    codePoints.add(it.readUtf8CodePoint())
                }
            }
            return if (cyclic && codePoints.isNotEmpty()) cyclicStream(codePoints) else codePoints.intStream()
        }

        private fun cyclicStream(codePoints: IntArrayList): IntStream {
            var index = 0
            return IntStream.generate {
                val value = codePoints.getInt(index)
                index = (index + 1) % codePoints.size
                value
            }
        }
    }
}
