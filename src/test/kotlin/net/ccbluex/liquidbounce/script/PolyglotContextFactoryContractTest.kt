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
package net.ccbluex.liquidbounce.script

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class PolyglotContextFactoryContractTest {

    @Test
    fun `context builder preserves security feature and debug ordering`() {
        val source = Files.readString(Path.of(SOURCE))

        assertAppearsInOrder(
            source,
            "Context.newBuilder(language)",
            ".allowHostAccess(HostAccess.ALL)",
            ".allowHostClassLookup { true }",
            ".currentWorkingDirectory(file.parentFile.toPath())",
            ".allowIO(IOAccess.ALL)",
            ".allowCreateProcess(false)",
            ".allowCreateThread(true)",
            ".allowNativeAccess(false)",
            ".allowExperimentalOptions(true)",
            ".applyJsFeatures(language, file)",
            ".applyScriptDebugOptions(file, debugOptions)",
            ".build()",
        )
    }

    @Test
    fun `javascript options retain exact values and order`() {
        val source = Files.readString(Path.of(SOURCE))

        assertAppearsInOrder(
            source,
            "option(\"js.nashorn-compat\", \"true\")",
            "option(\"js.ecmascript-version\", \"2023\")",
            "option(\"js.commonjs-require\", \"true\")",
            "option(\"js.commonjs-require-cwd\", file.parentFile.absolutePath)",
        )
    }

    @Test
    fun `bindings install the context before the registration function`() {
        val source = Files.readString(Path.of(SOURCE))

        assertAppearsInOrder(
            source,
            "val bindings = context.getBindings(language)",
            "context.setupContext(language, bindings)",
            "bindings.putMember(\"registerScript\", scriptMetadataRegistrar)",
        )
    }

    private fun assertAppearsInOrder(source: String, vararg fragments: String) {
        var cursor = -1
        fragments.forEach { fragment ->
            val next = source.indexOf(fragment, cursor + 1)
            assertTrue(next > cursor, "Expected '$fragment' after source offset $cursor")
            cursor = next
        }
    }

    private companion object {
        const val SOURCE = "src/main/kotlin/net/ccbluex/liquidbounce/script/PolyglotContextFactory.kt"
    }
}
