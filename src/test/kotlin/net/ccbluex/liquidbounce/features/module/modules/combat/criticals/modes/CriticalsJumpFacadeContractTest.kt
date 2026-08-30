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
package net.ccbluex.liquidbounce.features.module.modules.combat.criticals.modes

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class CriticalsJumpFacadeContractTest {

    @Test
    fun `jump mode keeps name settings handlers and public wait facade`() {
        val source = Files.readString(Path.of(SOURCE))

        assertTrue(source.contains("object CriticalsJump : Mode(\"Jump\")"))
        assertInOrder(
            source,
            "float(\"Height\", 0.42f, 0.1f..0.42f)",
            "float(\"Range\", 4f, 1f..6f)",
            "boolean(\"OptimizeForCooldown\", true)",
            "boolean(\"CheckKillaura\", false)",
            "boolean(\"CheckAutoClicker\", false)",
            "boolean(\"CanBeSeen\", true)",
            "handler<MovementInputEvent>",
            "handler<PlayerJumpEvent>",
            "fun shouldWaitForCrit(target: Entity, ignoreState: Boolean = false): Boolean",
        )
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private companion object {
        const val SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/criticals/modes/" +
                "CriticalsJump.kt"
    }
}
