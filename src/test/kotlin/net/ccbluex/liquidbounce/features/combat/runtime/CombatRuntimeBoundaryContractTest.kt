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
package net.ccbluex.liquidbounce.features.combat.runtime

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatRuntimeBoundaryContractTest {

    @Test
    fun `combat runtime depends on lower owned environment contracts`() {
        val actions = source("features/combat/runtime/CombatActions.kt")
        val manager = source("features/combat/runtime/CombatManager.kt")
        val selection = source("features/combat/runtime/EntityTargetSelection.kt")

        assertFalse("features.module.modules.combat.criticals" in actions)
        assertFalse("features.module.modules.combat.killaura" in manager)
        assertFalse("features.module.modules.render" in selection)
        assertTrue("CombatRuntimeEnvironment.wouldDoCriticalHit(true)" in actions)
        assertTrue("CombatRuntimeEnvironment.hasActiveKillAuraTarget()" in manager)
        assertTrue("CombatRuntimeEnvironment.isDetachedViewEnabled()" in selection)
    }

    @Test
    fun `module facades bind the exact live states`() {
        val criticals = source("features/module/modules/combat/criticals/ModuleCriticals.kt")
        val killAura = source("features/module/modules/combat/killaura/ModuleKillAura.kt")
        val freeCam = source("features/module/modules/render/ModuleFreeCam.kt")
        val freeLook = source("features/module/modules/render/ModuleFreeLook.kt")
        val manager = source("features/combat/runtime/CombatManager.kt")

        assertTrue(
            "CombatRuntimeEnvironment.bindCriticalHit { ignoreSprint -> wouldDoCriticalHit(ignoreSprint) }" in criticals
        )
        assertTrue(
            "CombatRuntimeEnvironment.bindKillAuraTarget { running && targetTracker.target != null }" in killAura
        )
        assertTrue("CombatRuntimeEnvironment.bindFreeCam { enabled }" in freeCam)
        assertTrue("CombatRuntimeEnvironment.bindFreeLook { enabled }" in freeLook)
        assertTrue("CombatRuntimeEnvironment.bindRotationPaused { shouldPauseRotation }" in manager)
    }

    private fun source(relativePath: String): String = Files.readString(Path.of(SOURCE_ROOT, relativePath))

    private companion object {
        const val SOURCE_ROOT = "src/main/kotlin/net/ccbluex/liquidbounce"
    }
}
