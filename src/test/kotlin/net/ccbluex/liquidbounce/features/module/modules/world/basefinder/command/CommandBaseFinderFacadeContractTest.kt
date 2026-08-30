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

package net.ccbluex.liquidbounce.features.module.modules.world.basefinder.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CommandBaseFinderFacadeContractTest {

    @Test
    fun `basefinder facade keeps fqcn and public command tree`() {
        assertEquals(
            "net.ccbluex.liquidbounce.features.module.modules.world.basefinder.command.CommandBaseFinder",
            CommandBaseFinder::class.qualifiedName,
        )

        val command = CommandBaseFinder.createCommand()
        assertEquals("basefinder", command.name)
        assertEquals(listOf("list", "report", "export", "clear"), command.subcommands.map { it.name })
    }

    @Test
    fun `pagination constant stays in its presentation multifile part`() {
        val presentation = Files.readString(SOURCE_ROOT.resolve("BaseFinderCommandPresentation.kt"))
        val formerOwner = Files.readString(SOURCE_ROOT.resolve("CommandBaseFinderPart1.kt"))

        assertTrue("private const val PAGE_SIZE = 8" in presentation)
        assertFalse(Regex("""\bconst val PAGE_SIZE\b""").containsMatchIn(formerOwner))
    }

    private companion object {
        val SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/basefinder/command"
        )
    }
}
