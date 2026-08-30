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
package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TargetStrafeRuntimeArchitectureContractTest {

    @Test
    fun `target strafe runtime dispatches through the planner port`() {
        val runtime = read(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/targetstrafe/runtime/" +
                "TargetStrafeModes.kt",
        )
        val contract = read(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/targetstrafe/contract/" +
                "TargetStrafePlannerPort.kt",
        )
        val planner = read(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/targetstrafe/planner/" +
                "TargetStrafePlanner.kt",
        )
        val module = read(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/ModuleTargetStrafe.kt",
        )

        assertFalse(runtime.contains("targetstrafe.planner"))
        assertTrue(runtime.contains("TargetStrafePlannerDispatcher.handleMotion"))
        assertTrue(runtime.contains("TargetStrafePlannerDispatcher.handleInput"))
        assertTrue(contract.contains("interface TargetStrafePlannerPort"))
        assertTrue(planner.contains("TargetStrafePlannerPort"))
        assertTrue(module.contains("TargetStrafePlannerDispatcher.bind(TargetStrafePlanner)"))
    }

    @Test
    fun `target strafe consumes validation and speed state through its contracts`() {
        val root = "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/"
        val feature = "${root}targetstrafe/"
        val settings = read("${feature}config/TargetStrafeSettings.kt")
        val cubeCraft = read("${feature}cubecraft/CubeCraftTargetStrafeMode.kt")
        val planner = read("${feature}planner/TargetStrafePlanner.kt")
        val module = read("${root}ModuleTargetStrafe.kt")
        val speed = read("${root}speed/ModuleSpeed.kt")
        val lowHop = read("${root}speed/modes/watchdog/SpeedHypixelLowHop.kt")

        assertFalse("ModuleKillAura" in settings)
        assertFalse("ModuleSpeed" in settings)
        assertFalse("clickTpStandingCollisionBox" in settings)
        assertFalse("targetstrafe.config" in cubeCraft)
        assertFalse("targetstrafe.planner.TargetStrafePlanner" in cubeCraft)
        assertFalse("movement.speed.ModuleSpeed" in planner)
        assertFalse("SpeedHypixelLowHop" in planner)

        assertTrue("TargetStrafePointValidation.bind(Validation::validatePoint)" in settings)
        assertTrue("TargetStrafePointValidation.validatePoint" in cubeCraft)
        assertTrue("TargetStrafePlannerDispatcher.handleInput" in cubeCraft)
        assertTrue("TargetStrafePointValidation.validatePoint" in planner)
        assertTrue("TargetStrafeEnvironment.bindKillAuraRunning" in module)
        assertTrue("TargetStrafeEnvironment.bindSpeedRunning" in speed)
        assertTrue("TargetStrafeEnvironment.bindLowHopShouldStrafe" in lowHop)
    }

    private fun read(path: String): String = Files.readString(Path.of(path))
}
