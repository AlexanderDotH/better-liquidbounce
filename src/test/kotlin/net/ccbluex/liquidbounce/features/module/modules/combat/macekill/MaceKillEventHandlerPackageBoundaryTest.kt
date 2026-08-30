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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class MaceKillEventHandlerPackageBoundaryTest {

    @Test
    fun `runtime keeps external combat integrations behind its facade owned port`() {
        val runtimeRoot = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/macekill",
        )
        val directSources = Files.list(runtimeRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }
        assertTrue(directSources.isEmpty(), "The MaceKill root must not own implementation files")

        val orchestrationRoot = runtimeRoot.resolve("orchestration")
        val orchestrationSources = Files.list(orchestrationRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }
        assertEquals(31, orchestrationSources.size, "MaceKill orchestration ownership changed")
        orchestrationSources.forEach(::assertMaceKillRuntimeBoundary)

        val stateSource = Files.readString(orchestrationRoot.resolve("MaceKillModuleState.kt"))
        listOf(
            "internal abstract val integration: MaceKillIntegrationPort",
            "internal abstract val routeSession: MaceKillRouteSession",
            "internal val routeEngine by lazy(LazyThreadSafetyMode.NONE) {",
            "RemoteKillRouteEngine(",
            "internal var activeRouteTarget: LivingEntity? = null",
            "internal var researchExecution: MaceKillResearchExecution? = null",
        ).forEach { contract ->
            assertTrue(contract in stateSource, "MaceKill state ownership changed: $contract")
        }

        val facadeSource = Files.readString(
            Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/ModuleMaceKill.kt"),
        )
        listOf(
            "override val integration = object : MaceKillIntegrationPort",
            "override fun attackTarget(target: LivingEntity): AcceptedAttackResult =",
            "attackEntityWithResult(target, SwingMode.DO_NOT_HIDE, keepSprint = true)",
            "override fun shouldAttack(target: LivingEntity): Boolean = target.shouldBeAttacked()",
            "override val running: Boolean",
            "override fun onDisabled()",
        ).forEach { contract ->
            assertTrue(contract in facadeSource, "MaceKill facade contract changed: $contract")
        }
    }

    @Test
    fun `MaceClip responsibilities live in focused child packages`() {
        val maceClipRoot = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/macekill/maceclip",
        )
        val directSources = Files.list(maceClipRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }
        assertTrue(directSources.isEmpty(), "MaceClip root must not own implementation files")

        assertPackageSources(
            maceClipRoot.resolve("reach"),
            expectedPrefix = "MaceClipReach",
            expectedCount = 6,
        )
        assertPackageSources(
            maceClipRoot.resolve("research"),
            expectedPrefix = "MaceClipResearch",
            expectedCount = 10,
        )
    }

    private fun assertPackageSources(packageRoot: Path, expectedPrefix: String, expectedCount: Int) {
        val sources = Files.list(packageRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }
        assertEquals(expectedCount, sources.size, "$packageRoot owns an unexpected number of sources")
        assertTrue(
            sources.all { it.fileName.toString().startsWith(expectedPrefix) },
            "$packageRoot contains a source outside its responsibility",
        )
    }

    private fun assertMaceKillRuntimeBoundary(sourcePath: Path) {
        val source = Files.readString(sourcePath)
        val imports = source.lineSequence().filter { it.startsWith("import ") }.toList()
        FORBIDDEN_IMPORTS.forEach { forbiddenImport ->
            assertFalse(
                imports.any { forbiddenImport in it },
                "${sourcePath.fileName} bypasses MaceKillIntegrationPort via $forbiddenImport",
            )
        }
        assertFalse(
            imports.any(COMBAT_ROOT_IMPORT::matches),
            "${sourcePath.fileName} creates a reverse dependency to the combat facade package",
        )
        assertFalse(
            imports.any(SPEAR_KILL_ROOT_IMPORT::matches),
            "${sourcePath.fileName} creates a reverse dependency to the SpearKill runtime package",
        )
        assertFalse(
            imports.any { it.startsWith(SPEAR_KILL_PACKET_IMPORT) },
            "${sourcePath.fileName} creates a reverse dependency to the SpearKill packet session",
        )
        assertFalse(
            "ModuleMaceKill" in source,
            "${sourcePath.fileName} must receive MaceKillModuleState instead of the facade singleton",
        )
    }

    private companion object {
        val FORBIDDEN_IMPORTS = listOf(
            "net.ccbluex.liquidbounce.features.combat.runtime.",
            "net.ccbluex.liquidbounce.features.global.",
            "net.ccbluex.liquidbounce.features.module.modules.combat.killaura.",
            "net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.",
            "net.ccbluex.liquidbounce.features.module.modules.player.",
            "net.ccbluex.liquidbounce.features.module.modules.render.",
            "net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.",
        )
        val COMBAT_ROOT_IMPORT = Regex(
            """import net\.ccbluex\.liquidbounce\.features\.module\.modules\.combat\.(?:\*|[^.]+)""",
        )
        val SPEAR_KILL_ROOT_IMPORT = Regex(
            """import net\.ccbluex\.liquidbounce\.features\.module\.modules\.combat\.spearkill\.(?:\*|[^.]+)""",
        )
        const val SPEAR_KILL_PACKET_IMPORT =
            "import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet."
    }

    @Test
    fun `FightBot contracts live in a dependency-free MaceKill child package`() {
        val fightBotRoot = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/macekill/fightbot",
        )
        val fightBotSources = Files.list(fightBotRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }
        assertTrue(fightBotSources.isNotEmpty(), "MaceKill FightBot ownership needs an explicit package")
        fightBotSources.forEach { sourcePath ->
            val imports = Files.readAllLines(sourcePath).filter { it.startsWith("import ") }
            assertFalse(
                imports.any { "net.ccbluex.liquidbounce.features.module.modules.combat.ModuleMaceKill" in it },
                "${sourcePath.fileName} creates a reverse dependency to the MaceKill facade",
            )
            assertFalse(
                imports.any {
                    "net.ccbluex.liquidbounce.features.module.modules.combat.macekill." in it &&
                        ".macekill.fightbot." !in it
                },
                "${sourcePath.fileName} creates a reverse dependency to the flat MaceKill runtime",
            )
        }
    }

    @Test
    fun `event handlers stay with the owning runtime and retain registration order`() {
        val runtimeRoot = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/macekill",
        )
        val eventRoot = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/macekill/event",
        )
        val obsoleteIntegrationRoot = runtimeRoot.resolve("integration")
        val obsoleteSources = if (Files.exists(obsoleteIntegrationRoot)) {
            Files.walk(obsoleteIntegrationRoot).use { paths ->
                paths.filter { it.isRegularFile() && it.extension == "kt" }.toList()
            }
        } else {
            emptyList()
        }
        assertTrue(obsoleteSources.isEmpty(), obsoleteSources.joinToString())

        val handlerFiles = mapOf(
            "MaceKillTickEventHandler.kt" to "handler<GameTickEvent>",
            "MaceKillPacketSafetyEventHandler.kt" to "handler<PacketEvent>(priority = SAFETY_FEATURE)",
            "MaceKillPacketDeliveryEventHandler.kt" to "handler<PacketEvent>(priority = READ_FINAL_STATE)",
            "MaceKillRenderEventHandler.kt" to "handler<WorldRenderEvent>",
            "MaceKillWorldChangeEventHandler.kt" to "handler<WorldChangeEvent>",
            "MaceKillDisconnectEventHandler.kt" to "handler<DisconnectEvent>",
        )
        handlerFiles.forEach { (fileName, handlerSignature) ->
            val source = Files.readString(eventRoot.resolve(fileName))
            assertTrue("package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event\n" in source)
            assertFalse("package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.integration" in source)
            assertTrue(handlerSignature in source, "$fileName must retain $handlerSignature")
        }

        val facadeSource = Files.readString(
            Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/ModuleMaceKill.kt"),
        )
        val registrations = listOf(
            "registerMaceKillTickHandler()",
            "registerMaceKillPacketSafetyHandler()",
            "registerMaceKillPacketDeliveryHandler()",
            "registerMaceKillRenderHandler()",
            "registerMaceKillWorldChangeHandler()",
            "registerMaceKillDisconnectHandler()",
        )
        registrations.zipWithNext().forEach { (before, after) ->
            assertTrue(facadeSource.indexOf(before) < facadeSource.indexOf(after), "$before must precede $after")
        }
    }
}
