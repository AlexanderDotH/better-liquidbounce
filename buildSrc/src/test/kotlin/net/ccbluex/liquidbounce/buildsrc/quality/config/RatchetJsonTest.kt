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

package net.ccbluex.liquidbounce.buildsrc.quality.config

import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetBaseline
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetEntry
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RatchetJsonTest {

    @Test
    fun `round trip is deterministic and entries are sorted by fingerprint`() {
        val baseline = RatchetBaseline(
            1,
            "abc123",
            listOf(entry("z", 4, setOf("target.z", "target.a")), entry("a", 2)),
        )
        val file = Files.createTempFile("source-ratchet", ".json")

        RatchetJson.write(file, baseline)
        val first = Files.readString(file)
        RatchetJson.write(file, RatchetJson.read(file))

        assertEquals(first, Files.readString(file))
        assertEquals(listOf("a", "z"), RatchetJson.read(file).entries.map { it.fingerprint })
        assertEquals(setOf("target.a", "target.z"), RatchetJson.read(file).entries.last().targets)
    }

    private fun entry(fingerprint: String, maximum: Int, targets: Set<String> = emptySet()) = RatchetEntry(
        fingerprint,
        "LB-HYG-001",
        "src/$fingerprint.kt",
        fingerprint,
        maximum,
        targets,
    )
}
