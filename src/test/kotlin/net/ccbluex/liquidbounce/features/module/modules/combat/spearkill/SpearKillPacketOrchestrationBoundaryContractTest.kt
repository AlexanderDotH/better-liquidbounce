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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class SpearKillPacketOrchestrationBoundaryContractTest {

    @Test
    fun `packet orchestration lives at the integration boundary`() {
        ORCHESTRATION_FILES.forEach { (fileName, integrationPath) ->
            assertFalse(Files.exists(PACKET_ROOT.resolve(fileName)), "$fileName remains packet-owned")

            val source = Files.readString(INTEGRATION_ROOT.resolve(integrationPath).resolve(fileName))
            assertTrue(INTEGRATION_PACKAGE in source, "$fileName is not integration-owned")
        }
    }

    @Test
    fun `packet package retains only inward packet primitives`() {
        val packetSources = Files.list(PACKET_ROOT).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }

        val packetFileNames = packetSources.map { it.fileName.toString() }.toSet()
        assertTrue(
            packetFileNames.containsAll(REQUIRED_PACKET_PRIMITIVE_FILES),
            "Packet package lost required primitives: ${REQUIRED_PACKET_PRIMITIVE_FILES - packetFileNames}",
        )

        val forbiddenImports = packetSources.flatMap { sourcePath ->
            Files.readAllLines(sourcePath).mapIndexedNotNull { index, line ->
                line.takeIf { it.isForbiddenPacketDependency() }?.let {
                    "${sourcePath.fileName}:${index + 1}: ${it.trim()}"
                }
            }
        }

        assertTrue(
            forbiddenImports.isEmpty(),
            "Packet primitives must not depend on higher SpearKill packages:\n" +
                forbiddenImports.joinToString(separator = "\n"),
        )
    }

    private fun String.isForbiddenPacketDependency(): Boolean {
        val importLine = trim()
        if (!importLine.startsWith("import $SPEAR_KILL_PACKAGE.")) {
            return false
        }

        return FORBIDDEN_DEPENDENCY_PACKAGES.any { importLine.startsWith("import $it.") } ||
            importLine.startsWith("import $SESSION_PACKAGE.") &&
            !importLine.startsWith("import $PACKET_PACKAGE.")
    }

    private companion object {
        val SPEAR_KILL_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/spearkill",
        )
        val PACKET_ROOT: Path = SPEAR_KILL_ROOT.resolve("session/packet")
        val INTEGRATION_ROOT: Path = SPEAR_KILL_ROOT.resolve("integration")
        const val SPEAR_KILL_PACKAGE =
            "net.ccbluex.liquidbounce.features.module.modules.combat.spearkill"
        const val SESSION_PACKAGE = "$SPEAR_KILL_PACKAGE.session"
        const val PACKET_PACKAGE = "$SESSION_PACKAGE.packet"
        const val INTEGRATION_PACKAGE = "package $SPEAR_KILL_PACKAGE.integration"
        val FORBIDDEN_DEPENDENCY_PACKAGES = listOf(
            "$SPEAR_KILL_PACKAGE.integration",
            "$SPEAR_KILL_PACKAGE.orchestration",
            "$SPEAR_KILL_PACKAGE.runtime",
            "$SPEAR_KILL_PACKAGE.target",
        )
        val ORCHESTRATION_FILES = mapOf(
            "ConfirmRemoteSpearKillPacketStepOperations.kt" to "delivery/terminal",
            "DeliverInstantFinalMovementOperations.kt" to "delivery/terminal",
            "DeliverSpearKillTerminalBurstPrefixOperations.kt" to "delivery/terminal",
            "HandlePendingTerminalCommitOperations.kt" to "delivery/terminal",
            "ReadyPrimedPendingStepOperations.kt" to "delivery/terminal",
            "ResegmentPendingMotionRouteOperations.kt" to "planning",
            "ValidatePendingSpearKillPacketStepOperations.kt" to "delivery/terminal",
            "ValidatePendingSpearKillTerminalBurstOperations.kt" to "delivery/terminal",
        )
        val REQUIRED_PACKET_PRIMITIVE_FILES = setOf(
            "SpearKillPacketSessionDelivery.kt",
            "SpearKillPacketSessionPortAdapter.kt",
            "SpearKillPacketSessionRecovery.kt",
            "SpearKillPacketSessionReplan.kt",
            "SpearKillPacketSessionStart.kt",
            "SpearKillPacketSessionState.kt",
        )
    }
}
