/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.common

import net.ccbluex.liquidbounce.common.debug.DebuggedOwner
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.TextColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class CoreBoundaryDependencyContractTest {

    @Test
    fun `api scopes do not retain unused high-level imports`() {
        val source = source(API_SCOPES)

        UNUSED_API_SCOPE_IMPORTS.forEach { forbiddenImport ->
            assertFalse(forbiddenImport in source, forbiddenImport)
        }
    }

    @Test
    fun `api response parsing does not depend on the gson utility package`() {
        val source = source(API_CONTRACTS)

        assertFalse("net.ccbluex.liquidbounce.config.gson.util" in source)
        assertTrue("TypeToken<T>()" in source)
    }

    @Test
    fun `default debug identity stays blue without the text utility package`() {
        val displayName = ExampleDebugOwner.debugDisplayName

        assertEquals("ExampleDebugOwner", displayName.string)
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.BLUE), displayName.style.color)
        assertFalse("net.ccbluex.liquidbounce.utils.text" in source(DEBUGGED_OWNER))
    }

    @Test
    fun `training overlay uses the Minecraft runtime directly`() {
        val source = source(TRAINING_OVERLAY)

        assertFalse("net.ccbluex.liquidbounce.utils.client.mc" in source)
        assertTrue("Minecraft.getInstance()" in source)
    }

    private object ExampleDebugOwner : DebuggedOwner

    private fun source(path: String): String = Path.of(path).readText()

    private companion object {
        const val API_SCOPES =
            "src/main/kotlin/net/ccbluex/liquidbounce/api/core/HttpClientScopes.kt"
        const val API_CONTRACTS =
            "src/main/kotlin/net/ccbluex/liquidbounce/api/core/HttpClientContracts.kt"
        const val DEBUGGED_OWNER =
            "src/main/kotlin/net/ccbluex/liquidbounce/common/debug/DebuggedOwner.kt"
        const val TRAINING_OVERLAY =
            "src/main/kotlin/net/ccbluex/liquidbounce/deeplearn/listener/OverlayTrainingListener.kt"

        val UNUSED_API_SCOPE_IMPORTS = listOf(
            "net.ccbluex.liquidbounce.config.gson",
            "net.ccbluex.liquidbounce.utils.client.logger",
            "net.ccbluex.liquidbounce.utils.client.mc",
            "net.ccbluex.liquidbounce.utils.render",
        )
    }
}
