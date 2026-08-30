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
package net.ccbluex.liquidbounce.injection

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class InjectionRootBoundaryContractTest {

    @Test
    fun `injection packages use neutral lifecycle and build contracts instead of the root facade`() {
        SOURCE_ROOTS.flatMap(::sourceFiles).forEach { path ->
            assertFalse(
                Files.readString(path).contains(ROOT_FACADE_IMPORT),
                "$path must not depend on the root facade",
            )
        }

        val clickGuiAdapter = read("src/main/kotlin/net/ccbluex/liquidbounce/injection/ClickGuiRuntimeInjectionAdapter.kt")
        val gui = read("src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinGui.java")
        val screen = read("src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/gui/MixinScreen.java")
        val minecraft = read("src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinMinecraft.java")
        val minecraftTitleHook = read(
            "src/main/java/net/ccbluex/liquidbounce/injection/hooks/MinecraftTitleHook.java",
        )
        val levelRenderer = read(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/MixinLevelRenderer.java",
        )

        assertTrue("ClientLifecycleState.isInitialized" in clickGuiAdapter)
        assertTrue("ClientLifecycleState.INSTANCE.isInitialized()" in gui)
        assertTrue("ClientLifecycleState.INSTANCE.isInitialized()" in screen)
        assertTrue("MinecraftTitleHook.buildTitle(" in minecraft)
        assertTrue("ClientUtilsKt.getLogger().debug(\"Modifying window title\")" in minecraftTitleHook)
        assertTrue("ClientBuildMetadata.NAME" in minecraftTitleHook)
        assertTrue("ClientBuildMetadata.INSTANCE.getVersion()" in minecraftTitleHook)
        assertTrue("ClientBuildMetadata.INSTANCE.getCommit()" in minecraftTitleHook)
        assertTrue("ClientBuildMetadata.NAME" in levelRenderer)
    }

    private fun sourceFiles(root: String): List<Path> = Files.walk(Path.of(root)).use { paths ->
        paths.filter { Files.isRegularFile(it) && (it.toString().endsWith(".java") || it.toString().endsWith(".kt")) }
            .toList()
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val ROOT_FACADE_IMPORT = "import net.ccbluex.liquidbounce.LiquidBounce"
        val SOURCE_ROOTS = listOf(
            "src/main/kotlin/net/ccbluex/liquidbounce/injection",
            "src/main/java/net/ccbluex/liquidbounce/injection/hooks",
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client",
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/gui",
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render",
        )
    }
}
