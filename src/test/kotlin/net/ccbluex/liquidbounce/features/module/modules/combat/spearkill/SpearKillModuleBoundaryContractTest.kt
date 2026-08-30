/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class SpearKillModuleBoundaryContractTest {

    @Test
    fun `SpearKill root consumes KillAura state through its module state port`() {
        val rootSources = Files.list(SPEAR_KILL_ROOT).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }
        assertRootImportsUseModuleStatePort(rootSources)
        assertModuleStateFacadeContract()
        assertSetbackFacadeContract()
    }

    private fun assertRootImportsUseModuleStatePort(rootSources: List<Path>) {
        rootSources.forEach { sourcePath ->
            Files.readAllLines(sourcePath)
                .filter { it.startsWith("import ") }
                .forEach { importLine ->
                    val importsSessionImplementation = importLine.startsWith(SESSION_IMPORT) &&
                        !importLine.startsWith(PACKET_SESSION_IMPORT)
                    assertFalse(
                        FORBIDDEN_ROOT_IMPORTS.any(importLine::startsWith) || importsSessionImplementation,
                        "${sourcePath.fileName} bypasses the module state port via $importLine",
                    )
                }
        }
    }

    private fun assertModuleStateFacadeContract() {
        val stateSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("orchestration/session/SpearKillModuleState.kt"),
        )
        assertTrue("internal abstract val killAuraRunning: Boolean" in stateSource)
        assertTrue("internal abstract val debugEnabled: Boolean" in stateSource)
        assertTrue("enabled = { debugEnabled }" in stateSource)

        val ownershipSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("orchestration/session/SpearKillAttemptOwnership.kt"),
        )
        assertTrue("GlobalSettingsCombat.delegateKillAuraAttacks && killAuraRunning" in ownershipSource)
        assertFalse("ModuleKillAura" in ownershipSource)

        val movementStateSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("orchestration/session/SpearKillMovementState.kt"),
        )
        assertFalse("planner.currentSpeedProfile" in movementStateSource)

        val recoverySource = Files.readString(
            SPEAR_KILL_ROOT.resolve("session/recovery/CreateCollisionSafeSetbackRecoveryOperations.kt"),
        )
        assertTrue("internal val SpearKillModuleState.recoveryPlanningStepLimit" in recoverySource)
        assertTrue("currentSpeedProfile(activeSpeedStepDistance).maximumStepLimit" in recoverySource)

        val facadeSource = Files.readString(COMBAT_ROOT.resolve("ModuleSpearKill.kt"))
        assertTrue("override val killAuraRunning: Boolean" in facadeSource)
        assertTrue("get() = ModuleKillAura.running" in facadeSource)
        assertTrue("override val debugEnabled: Boolean" in facadeSource)
        assertTrue("get() = ModuleDebug.running" in facadeSource)
        assertTrue("fun prepareSpearKillSetbackCorrection" in facadeSource)
        assertTrue("fun finishSpearKillSetbackCorrection" in facadeSource)
        assertTrue("fun clearSpearKillAttack" in facadeSource)

        val bridgeSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("facade/SpearKillFacadeBridge.kt"),
        )
        assertInOrder(
            bridgeSource,
            "SpearKillSetbackHook.install(SpearKillSetbackCallbacks(",
            "beforeCorrection = module::prepareFacadeSetbackCorrection",
            "afterCorrection = module::finishFacadeSetbackCorrection",
            "registerRouteRotationHandler()",
        )
    }

    private fun assertSetbackFacadeContract() {
        val setbackSource = Files.readString(SPEAR_KILL_ROOT.resolve("SpearKillSetbackRollback.kt"))
        assertFalse("ModuleSpearKill" in setbackSource)
        assertTrue("private val callbacks = AtomicReference<SpearKillSetbackCallbacks<P, T>?>(null)" in setbackSource)
        assertTrue("check(this.callbacks.compareAndSet(null, callbacks))" in setbackSource)
        assertInOrder(
            setbackSource,
            "fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {",
            "callbackBinding.beforeCorrection(packet, player)",
            "RemoteKillSetbackRegistry.beforeCorrection(packet, player)",
            "fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {",
            "callbackBinding.afterCorrection(packet, player)",
            "RemoteKillSetbackRegistry.afterCorrection(packet, player)",
        )
    }

    @Test
    fun `target planning consumes combat ownership through the integration adapter`() {
        val targetSources = Files.list(SPEAR_KILL_ROOT.resolve("target")).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }
        val forbiddenImports = listOf(
            "import net.ccbluex.liquidbounce.features.combat.runtime.",
            "import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.",
        )

        targetSources.forEach { sourcePath ->
            Files.readAllLines(sourcePath)
                .filter { it.startsWith("import ") }
                .forEach { importLine ->
                    assertFalse(
                        forbiddenImports.any(importLine::startsWith),
                        "${sourcePath.fileName} bypasses the integration adapter via $importLine",
                    )
                }
        }

        val targetStateSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("orchestration/target/SpearKillTargetStateOperations.kt"),
        )
        assertTrue("isSafeSpearKillCombatTarget" in targetStateSource)
        assertTrue("shouldBeAttacked()" in targetStateSource)
        assertTrue("killAuraDelegatedTarget" in targetStateSource)
        assertTrue("delegatedKillAuraTarget()" in targetStateSource)

        val adapterSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("integration/facade/SpearKillFacadeRequests.kt"),
        )
        assertTrue("acceptsKillAuraTarget" in adapterSource)
        assertTrue("isSafeSpearKillCombatTarget" in adapterSource)
    }

    @Test
    fun `integration consumes KillAura through the module state port`() {
        val integrationSources = Files.walk(SPEAR_KILL_ROOT.resolve("integration")).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
        }

        integrationSources.forEach { sourcePath ->
            Files.readAllLines(sourcePath)
                .filter { it.startsWith("import ") }
                .forEach { importLine ->
                    assertFalse(
                        importLine.startsWith(KILL_AURA_IMPORT) || importLine == MODULE_FIGHT_BOT_IMPORT,
                        "${sourcePath.fileName} bypasses the module state port via $importLine",
                    )
                }
        }

        val stateSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("orchestration/session/SpearKillModuleState.kt"),
        )
        assertTrue("abstract fun delegatedKillAuraTarget" in stateSource)
        assertTrue("abstract fun shouldPrechargeDelegatedKillAura" in stateSource)
        assertTrue("abstract fun stopDelegatedKillAuraBlocking" in stateSource)
        assertTrue("abstract val fightBotSpearAutomation" in stateSource)

        val facadeSource = Files.readString(COMBAT_ROOT.resolve("ModuleSpearKill.kt"))
        assertTrue("ModuleKillAura.targetForSpearKill()" in facadeSource)
        assertTrue("ModuleKillAura.shouldPrechargeForSpearKill()" in facadeSource)
        assertTrue("KillAuraAutoBlock.stopBlocking(pauses = true)" in facadeSource)
        assertTrue("get() = ModuleFightBot.configuredSpearAutomation" in facadeSource)

        val fightBotAdapterSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("integration/tick/PrepareFightBotSpearUseOperations.kt"),
        )
        assertFalse("ModuleFightBot" in fightBotAdapterSource)
        assertTrue("fightBotSpearAutomation" in fightBotAdapterSource)
    }

    @Test
    fun `runtime and session consume target ownership through module state`() {
        val stateSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("orchestration/session/SpearKillModuleState.kt"),
        )
        assertTrue("internal fun clearAStarTargetLock()" in stateSource)
        assertTrue("internal fun rejectSpearKillTarget(target: LivingEntity)" in stateSource)
        assertTrue("internal fun isSpearKillTargetRejected(target: LivingEntity)" in stateSource)

        val contractSource = Files.readString(
            SPEAR_KILL_ROOT.resolve("target/SpearKillTargetStateContracts.kt"),
        )
        assertTrue("internal typealias SpearKillTargetCandidate" in contractSource)
        assertTrue("internal data class SpearKillTickTargetContext" in contractSource)

        listOf("runtime", "session").forEach { packageName ->
            Files.walk(SPEAR_KILL_ROOT.resolve(packageName)).use { paths ->
                paths.filter { it.isRegularFile() && it.extension == "kt" }.forEach { sourcePath ->
                    Files.readAllLines(sourcePath)
                        .filter { it.startsWith("import ") }
                        .forEach { importLine ->
                            assertFalse(
                                importLine.startsWith(TARGET_OPERATIONS_IMPORT),
                                "${sourcePath.fileName} bypasses module target state via $importLine",
                            )
                        }
                }
            }
        }
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
        val COMBAT_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat",
        )
        val SPEAR_KILL_ROOT: Path = COMBAT_ROOT.resolve("spearkill")
        val FORBIDDEN_ROOT_IMPORTS = listOf(
            "import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.",
            "import net.ccbluex.liquidbounce.features.module.modules.render.",
        )
        const val SESSION_IMPORT =
            "import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session."
        const val PACKET_SESSION_IMPORT = "${SESSION_IMPORT}packet."
        const val TARGET_OPERATIONS_IMPORT =
            "import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.target."
        const val KILL_AURA_IMPORT =
            "import net.ccbluex.liquidbounce.features.module.modules.combat.killaura."
        const val MODULE_FIGHT_BOT_IMPORT =
            "import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleFightBot"
    }
}
