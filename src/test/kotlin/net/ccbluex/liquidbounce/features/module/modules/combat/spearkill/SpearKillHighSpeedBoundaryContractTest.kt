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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class SpearKillHighSpeedBoundaryContractTest {

    @Test
    fun `high speed research remains an inward dependency with stable model identities`() {
        val sources = Files.list(HIGH_SPEED_ROOT).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }
        val forbiddenImports = listOf(
            "net.ccbluex.liquidbounce.features.combat.runtime.",
            "net.ccbluex.liquidbounce.features.module.modules.combat.",
            "net.ccbluex.liquidbounce.features.module.modules.render.",
        )

        sources.forEach { sourcePath ->
            Files.readAllLines(sourcePath)
                .filter { it.startsWith("import ") }
                .forEach { importLine ->
                    assertFalse(
                        forbiddenImports.any(importLine::contains),
                        "${sourcePath.fileName} creates a high-speed reverse dependency via $importLine",
                    )
                }
        }

        listOf(
            "SpearKillHighSpeedResearchModels.kt" to "internal enum class SpearKillHighSpeedResearchPacketType",
            "SpearKillHighSpeedResearchRuntime.kt" to "internal class SpearKillHighSpeedResearchRuntime",
            "SpearKillHighSpeedResearchJsonlWriter.kt" to "internal class SpearKillHighSpeedResearchJsonlWriter",
        ).forEach { (fileName, declaration) ->
            val source = Files.readString(HIGH_SPEED_ROOT.resolve(fileName))
            assertTrue(HIGH_SPEED_PACKAGE in source, "$fileName changed package identity")
            assertTrue(declaration in source, "$fileName lost $declaration")
        }
    }

    @Test
    fun `module aware high speed operations live at their owning boundaries without wrappers`() {
        MODULE_AWARE_OPERATIONS.forEach { (sourcePath, packageDeclaration) ->
            val fileName = sourcePath.fileName
            assertFalse(Files.exists(HIGH_SPEED_ROOT.resolve(fileName)), "$fileName remains in the core package")
            val source = Files.readString(sourcePath)
            assertTrue(packageDeclaration in source, "$fileName changed ownership")
        }
    }

    @Test
    fun `attempt evidence publishes the canonical lazy debug sequence through the neutral sink`() {
        val source = Files.readString(ATTEMPT_EVIDENCE_SOURCE)
        val labels = DEBUG_LABEL.findAll(source).map { it.groupValues[1] }.toList()

        assertEquals(EXPECTED_DEBUG_LABELS, labels)
        assertTrue("import net.ccbluex.liquidbounce.common.debug.DebugParameterSink" in source)
        assertFalse("features.module.modules.render.ModuleDebug" in source)
        assertTrue(
            source.indexOf("completeSpearKillAttempt(\"damage-window-expired\")") <
                source.indexOf("publishSpearKillAttemptDebug"),
            "damage-window completion must remain before debug publication",
        )
    }

    private companion object {
        val HIGH_SPEED_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/spearkill/research/highspeed",
        )
        val SPEAR_KILL_ROOT: Path = HIGH_SPEED_ROOT.resolve("../..").normalize()
        val INTEGRATION_ROOT: Path = SPEAR_KILL_ROOT.resolve("integration")
        val RUNTIME_ROOT: Path = SPEAR_KILL_ROOT.resolve("runtime")
        val ATTEMPT_EVIDENCE_SOURCE: Path = HIGH_SPEED_ROOT.resolve("../../runtime").normalize()
            .resolve("UpdateSpearKillAttemptEvidenceOperations.kt")
        const val HIGH_SPEED_PACKAGE =
            "package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed"
        const val DEBUG_PACKAGE =
            "package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug"
        const val TERMINAL_INTEGRATION_PACKAGE =
            "package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal"
        const val STARTUP_INTEGRATION_PACKAGE =
            "package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup"
        const val RUNTIME_LIFECYCLE_PACKAGE =
            "package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle"
        val DEBUG_LABEL = Regex("""DebugParameterSink\.publish\(this, "([^"]+)"\)""")
        val MODULE_AWARE_OPERATIONS = listOf(
            INTEGRATION_ROOT.resolve("delivery/terminal/BeginHighSpeedResearchBurstOperations.kt") to
                TERMINAL_INTEGRATION_PACKAGE,
            SPEAR_KILL_ROOT.resolve("debug/DebugSpearKillOperations.kt") to DEBUG_PACKAGE,
            RUNTIME_ROOT.resolve("lifecycle/LogCompletedSpearKillAttemptOperations.kt") to
                RUNTIME_LIFECYCLE_PACKAGE,
            INTEGRATION_ROOT.resolve("delivery/terminal/LogSpearKillPrimedBurstDecisionOperations.kt") to
                TERMINAL_INTEGRATION_PACKAGE,
            INTEGRATION_ROOT.resolve("startup/SpearKillHighSpeedResearchPacketTypeOperations.kt") to
                STARTUP_INTEGRATION_PACKAGE,
            INTEGRATION_ROOT.resolve("startup/StartExplicitHighSpeedResearchProbeOperations.kt") to
                STARTUP_INTEGRATION_PACKAGE,
        )
        val EXPECTED_DEBUG_LABELS = listOf(
            "Attempt Target",
            "Attempt Target Source",
            "Attempt Route",
            "Attempt Outbound Steps",
            "Attempt Predicted Hit Tick",
            "Attempt Charge Ticks",
            "Attempt Terminal Authorization Tick",
            "Attempt Setback",
            "Attempt Blocked Edge",
            "Attempt Recovery",
            "Attempt Target Defeated",
            "Attempt Target Removed",
            "Attempt Damage Evidence",
            "Attempt Outcome",
            "Target Speed",
            "Current Speed",
            "Acceleration",
            "Deceleration",
            "Step Distance",
            "Estimated Vanilla Budget",
            "Requested Displacement",
            "Delivered Displacement",
            "Owned Movement Packets Previous Tick",
            "Server Correction",
            "Look Vector",
            "Move Direction",
            "Estimated Attacker Kinetic Speed",
            "Estimated Relative Kinetic Speed",
            "Estimated Kinetic Bonus Damage",
            "Kinetic Requirements Met",
        )
    }
}
