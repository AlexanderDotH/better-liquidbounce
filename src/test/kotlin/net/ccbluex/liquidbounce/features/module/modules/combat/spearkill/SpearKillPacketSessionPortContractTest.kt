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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class SpearKillPacketSessionPortContractTest {

    @Test
    fun `SpearKill root owns its packet session port without importing the implementation package`() {
        val rootSources = Files.list(SPEAR_KILL_ROOT).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }
        val forbiddenImports = rootSources.flatMap { sourcePath ->
            Files.readAllLines(sourcePath).mapIndexedNotNull { index, line ->
                line.takeIf { it.trim().startsWith(FORBIDDEN_PACKET_IMPORT) }?.let {
                    "${sourcePath.fileName}:${index + 1}: ${it.trim()}"
                }
            }
        }

        assertTrue(
            forbiddenImports.isEmpty(),
            "SpearKill root must depend on its packet port, not session.packet:\n" +
                forbiddenImports.joinToString(separator = "\n"),
        )

        val portPath = SPEAR_KILL_ROOT.resolve("SpearKillPacketSessionPort.kt")
        assertTrue(portPath.isRegularFile(), "Missing root-owned SpearKillPacketSessionPort")
        val portSource = Files.readString(portPath).normalizedWhitespace()
        assertTrue(
            "interface SpearKillPacketSessionPort : RemoteKillRouteSession" in portSource,
            "Packet port must preserve the shared RemoteKillRouteSession contract",
        )
        assertTrue(
            "val pathHeading: Rotation?" in portSource,
            "Packet port must expose the active route heading without a session.packet extension",
        )

        val bootSessionSource = Files.readString(SPEAR_KILL_ROOT.resolve("SpearKillPacketBootSession.kt"))
            .normalizedWhitespace()
        assertTrue(
            "internal val state: SpearKillPacketSessionPort" in bootSessionSource &&
                "RemoteKillRouteSession by state" in bootSessionSource,
            "PacketBootSession must transparently delegate the shared route-session contract",
        )

        val moduleStateSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("orchestration/session/SpearKillModuleState.kt"),
        )
            .normalizedWhitespace()
        assertTrue(
            "SpearKillPacketBootSession()" !in moduleStateSource,
            "SpearKillModuleState must receive packet-session wiring instead of locating its implementation",
        )
    }

    private fun String.normalizedWhitespace(): String = replace(Regex("\\s+"), " ")

    private companion object {
        val SPEAR_KILL_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/spearkill",
        )
        const val FORBIDDEN_PACKET_IMPORT =
            "import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet."
    }
}
