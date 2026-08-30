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
package net.ccbluex.liquidbounce.features.rotation

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RotationFeatureBoundaryContractTest {

    @Test
    fun `adapter reads only lower owned environment contracts`() {
        val adapter = source("features/rotation/RotationFeatureAdapter.kt")

        assertFalse("features.blink.BlinkManager" in adapter)
        assertFalse("features.combat.runtime" in adapter)
        assertFalse("features.module.modules.combat.backtrack.ModuleBacktrack" in adapter)
        assertFalse("features.module.modules.movement.ModuleFreeze" in adapter)
        assertTrue("RotationLagState.isFakeLagging()" in adapter)
        assertTrue("FreezeStateHook.isRunning()" in adapter)
        assertTrue("CombatRuntimeEnvironment.shouldPauseRotation()" in adapter)
    }

    @Test
    fun `lag owners bind their exact live queue states`() {
        val blink = source("features/blink/BlinkManager.kt")
        val backtrack = source("features/module/modules/combat/backtrack/ModuleBacktrack.kt")

        assertTrue("RotationLagState.bindBlinkLag { isLagging }" in blink)
        assertTrue("RotationLagState.bindBacktrackLag { isLagging() }" in backtrack)
    }

    private fun source(relativePath: String): String = Files.readString(Path.of(SOURCE_ROOT, relativePath))

    private companion object {
        const val SOURCE_ROOT = "src/main/kotlin/net/ccbluex/liquidbounce"
    }
}
