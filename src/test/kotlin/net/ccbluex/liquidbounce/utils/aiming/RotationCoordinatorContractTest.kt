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

package net.ccbluex.liquidbounce.utils.aiming

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RotationCoordinatorContractTest {
    @Test
    fun `game tick publishes rotation update before advancing managed rotation`() {
        val source = read(EVENT_COORDINATOR)
        val publish = source.indexOf("EventManager.callEvent(RotationUpdateEvent)")
        val update = source.indexOf("RotationManager.update()")

        assertTrue(publish >= 0)
        assertTrue(update > publish)
    }

    @Test
    fun `world change resets state and packet acceptance keeps the historical rule`() {
        val source = read(EVENT_COORDINATOR)

        assertTrue(source.contains("handler<WorldChangeEvent> { RotationManager.reset() }"))
        assertTrue(shouldCommitActualRotation(incoming = true, cancelled = true))
        assertTrue(shouldCommitActualRotation(incoming = false, cancelled = false))
        assertFalse(shouldCommitActualRotation(incoming = false, cancelled = true))
    }

    @Test
    fun `public facade no longer owns event or feature implementations`() {
        val source = read(ROTATION_MANAGER)

        FORBIDDEN_IMPORTS.forEach { import -> assertFalse(source.contains(import), import) }
        assertFalse(source.contains("@Suppress(\"CognitiveComplexMethod\", \"NestedBlockDepth\")"))
    }

    private fun read(path: String) = Files.readString(Path.of(path))

    private companion object {
        const val EVENT_COORDINATOR =
            "src/main/kotlin/net/ccbluex/liquidbounce/event/rotation/RotationEventCoordinator.kt"
        const val ROTATION_MANAGER =
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/aiming/RotationManager.kt"

        val FORBIDDEN_IMPORTS = listOf(
            "import net.ccbluex.liquidbounce.event.EventManager",
            "import net.ccbluex.liquidbounce.event.events.MouseRotationEvent",
            "import net.ccbluex.liquidbounce.event.events.PacketEvent",
            "import net.ccbluex.liquidbounce.event.events.PlayerVelocityStrafe",
            "import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent",
            "import net.ccbluex.liquidbounce.event.events.TransferOrigin",
            "import net.ccbluex.liquidbounce.features.blink.BlinkManager",
            "import net.ccbluex.liquidbounce.features.module.modules.combat.backtrack.ModuleBacktrack",
            "import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze",
            "import net.ccbluex.liquidbounce.features.combat.runtime.CombatManager",
        )
    }
}
