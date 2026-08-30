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

package net.ccbluex.liquidbounce.utils.client.error

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ErrorHandlerContractTest {

    @Test
    fun `message construction remains complete before output selection`() {
        val source = Files.readString(Path.of(ERROR_HANDLER_SOURCE))
        val buildMessage = source.function("private fun buildMessage()")

        buildMessage.assertInOrder(
            "header()",
            "if (quickFix != null)",
            "quickFix()",
            "if (needToReport)",
            "reportMessage()",
            "systemSpecs()",
            "error()",
            "return builder.toString().replace(\"\\\"\", \"\").replace(\"'\", \"\")",
        )

        val buildAndShowMessage = source.function("fun buildAndShowMessage()")
        buildAndShowMessage.assertInOrder("return showMessage(buildMessage())")
    }

    @Test
    fun `output selection retains CI report and non-report semantics`() {
        val source = Files.readString(Path.of(ERROR_HANDLER_SOURCE))
        val showMessage = source.function("private fun showMessage(message: String)")

        showMessage.assertInOrder(
            "!System.getenv(\"CI\").isNullOrEmpty()",
            "logger.error(message)",
            "false",
            "needToReport",
            "TinyFileDialogs.tinyfd_messageBox(",
            "title",
            "message",
            "\"yesno\"",
            "\"error\"",
            "1",
            ") == 1",
            "else",
            "TinyFileDialogs.tinyfd_messageBox(",
            "title",
            "message",
            "\"ok\"",
            "\"error\"",
            "1",
            "false",
        )
    }

    private fun String.function(signature: String): String {
        val start = indexOf(signature)
        assertTrue(start >= 0, "$signature is missing")

        val bodyStart = indexOf('{', start)
        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return substring(start, index + 1)
            }
        }

        error("$signature has no complete body")
    }

    private fun String.assertInOrder(vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private companion object {
        const val ERROR_HANDLER_SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/client/error/ErrorHandler.kt"
    }
}
