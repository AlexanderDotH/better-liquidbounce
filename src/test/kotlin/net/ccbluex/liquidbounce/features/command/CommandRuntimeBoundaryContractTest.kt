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
package net.ccbluex.liquidbounce.features.command

import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.runtime.CommandExecutor
import net.ccbluex.liquidbounce.features.command.runtime.CommandExecutor.suspendHandler
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class CommandRuntimeBoundaryContractTest {

    @Test
    fun `runtime depends on the handler contract instead of the concrete builder`() {
        val runtime = RUNTIME_SOURCE.readText()

        assertFalse(runtime.contains("features.command.builder.CommandBuilder"))
        assertTrue(runtime.contains("CommandHandlerBuilder"))
    }

    @Test
    fun `runtime preserves command execution cancellation error handling and history order`() {
        val runtime = RUNTIME_SOURCE.readText()
        val execute = runtime.indexOf("CommandManager.execute(commandBody)")
        val handleError = runtime.indexOf("handleExceptions(e)", execute)
        val cancel = runtime.indexOf("it.cancelEvent()", handleError)
        val history = runtime.indexOf("ClientCommandHistory.append(commandBody)", cancel)

        assertTrue(execute >= 0)
        assertTrue(execute < handleError)
        assertTrue(handleError < cancel)
        assertTrue(cancel < history)
        assertTrue(runtime.contains("is CommandException"))
    }

    @Test
    fun `legacy runtime entry point delegates to the command owned implementation`() {
        val facade = LEGACY_RUNTIME_SOURCE.readText()

        assertTrue(facade.contains("object CommandExecutor"))
        assertTrue(facade.contains("CommandRuntime"))
    }

    @Suppress("unused")
    private fun compileSuspendHandlerShape(builder: CommandBuilder): CommandBuilder = with(CommandExecutor) {
        builder.suspendHandler {
            command.name
        }
    }

    private companion object {
        val RUNTIME_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/command/CommandRuntime.kt"
        )
        val LEGACY_RUNTIME_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/command/runtime/CommandExecutor.kt"
        )
    }
}
