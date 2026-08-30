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
package net.ccbluex.liquidbounce.render.target

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TargetRendererTrackerBoundaryTest {

    @Test
    fun `renderer owns only the existing entity supplier contract`() {
        val source = read(TARGET_RENDERER)

        assertTrue(source.contains("val target: () -> Entity?"))
        assertFalse(source.contains("features.combat.runtime.TargetTracker"))
        assertFalse(source.contains("targetTracker: TargetTracker"))
    }

    @Test
    fun `tracker consumers bind the same live target getter at their feature boundary`() {
        TARGET_TRACKER_CONSUMERS.forEach { path ->
            val source = read(path)
            assertTrue(source.contains("targetTracker::target"), "$path must bind the live target getter")
            assertFalse(source.contains(", targetTracker)"), "$path still passes the concrete tracker")
        }
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val TARGET_RENDERER = "src/main/kotlin/net/ccbluex/liquidbounce/render/target/TargetRenderer.kt"
        val TARGET_TRACKER_CONSUMERS = listOf(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/ModuleAimbot.kt",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/ModuleAutoRod.kt",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/ModuleAutoShoot.kt",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/aimbot/autobow/" +
                "AutoBowAimbotFeature.kt",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/crystalaura/ModuleCrystalAura.kt",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/elytratarget/ModuleElytraTarget.kt",
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/ModuleBlockTrap.kt",
        )
    }
}
