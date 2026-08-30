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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.facade


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
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

class SpearKillIntegrationFacadeContractTest {

    @Test
    fun `neutral facade owns both orchestrators without combat module imports`() {
        assertFalse(Files.exists(SPEAR_KILL_ROOT.resolve("SpearKillFacadeBridge.kt")))
        assertFalse(Files.exists(SPEAR_KILL_ROOT.resolve("SpearKillKillAuraLifecycle.kt")))

        listOf(BRIDGE_PATH, KILL_AURA_LIFECYCLE_PATH).forEach { sourcePath ->
            val source = Files.readString(sourcePath)
            val imports = source.lineSequence().filter { it.startsWith("import ") }.toList()

            assertTrue("package $FACADE_PACKAGE" in source)
            FORBIDDEN_COMBAT_MODULE_IMPORTS.forEach { forbiddenImport ->
                assertFalse(
                    imports.any { it.startsWith(forbiddenImport) },
                    "${sourcePath.fileName} imports combat facade directly via $forbiddenImport",
                )
            }
            assertTrue(
                imports.any { it.startsWith("import $SPEAR_KILL_INTEGRATION_PACKAGE.") },
                "${sourcePath.fileName} no longer delegates through SpearKill integration",
            )
        }
    }

    @Test
    fun `bridge surface and orchestration order remain stable`() {
        val bridge = Files.readString(BRIDGE_PATH)
        val methodNames = BRIDGE_METHOD_PATTERN.findAll(bridge).map { it.groupValues[1] }.toList()
        val statePortMethods = BRIDGE_STATE_PORT_PATTERN.findAll(bridge).count()

        assertEquals(EXPECTED_BRIDGE_METHODS, methodNames)
        assertEquals(EXPECTED_BRIDGE_METHODS.size, statePortMethods)
        assertInOrder(
            bridge,
            "internal val newPacketSessionPort: SpearKillPacketSessionPort",
            "get() = SpearKillPacketSessionPortAdapter()",
            "fun initializePreview(module: SpearKillModuleState)",
        )
        assertInOrder(
            bridge,
            "module.tree(SpearKillPreview.bind(module))",
            "TargetGlowSourceRegistry.register(module::currentPreviewGlow)",
        )
        assertInOrder(
            bridge,
            "SpearKillSetbackHook.install(SpearKillSetbackCallbacks(",
            "beforeCorrection = module::prepareFacadeSetbackCorrection",
            "afterCorrection = module::finishFacadeSetbackCorrection",
            "registerRouteRotationHandler()",
        )
        assertInOrder(
            bridge,
            "registerRouteRotationHandler()",
            "registerMovementInputHandler()",
            "registerTickHandler()",
            "registerNetworkMovementHandler()",
            "registerServerSneakPacketHandler()",
            "registerPacketSafetyHandler()",
            "registerFallDamagePacketHandler()",
            "registerPacketDeliveryHandler()",
            "registerWorldChangeHandler()",
            "registerDisconnectHandler()",
            "registerRenderHandler()",
        )
        assertInOrder(
            bridge,
            "failureNotificationGate.clear()",
            "networkOptimizer.reset()",
            "holdUseLaunchTarget = null",
            "module.clearFacadeAttack(\"disabled\")",
            "rejectedTargets.clear()",
            "highSpeedResearch.close()",
            "if (debugConsole.isInitialized()) debugConsole.value.clearTransitions()",
        )
    }

    @Test
    fun `KillAura release evaluation and actions retain their established order`() {
        val lifecycle = Files.readString(KILL_AURA_LIFECYCLE_PATH)

        assertTrue("internal fun SpearKillModuleState.onKillAuraDisabled()" in lifecycle)
        assertInOrder(
            lifecycle,
            "killAuraOwnsAttempt = killAuraOwnsAttempt",
            "killAuraPreparationActive = packetRoutePreparationActive &&",
            "inheritedUseActive = hasKillAuraSpearUseRequest || killAuraStartedSpearUse",
        )
        assertInOrder(
            lifecycle,
            "SpearKillKillAuraReleaseAction.NONE -> return",
            "SpearKillKillAuraReleaseAction.RELEASE_INHERITED_USE -> clearKillAuraSpearUse()",
            "SpearKillKillAuraReleaseAction.CANCEL_INHERITED_PREPARATION -> {",
            "cancelKillAuraPreparation()",
            "clearKillAuraSpearUse()",
            "SpearKillKillAuraReleaseAction.CANCEL_INHERITED_ROUTE -> beginKillAuraOwnedReturn()",
        )
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    companion object {
        private const val FACADE_PACKAGE =
            "net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.facade"
        private const val SPEAR_KILL_INTEGRATION_PACKAGE =
            "net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration"
        private val SPEAR_KILL_ROOT = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/spearkill",
        )
        private val FACADE_ROOT = SPEAR_KILL_ROOT.resolve("facade")
        private val BRIDGE_PATH = FACADE_ROOT.resolve("SpearKillFacadeBridge.kt")
        private val KILL_AURA_LIFECYCLE_PATH = FACADE_ROOT.resolve("SpearKillKillAuraLifecycle.kt")
        private val FORBIDDEN_COMBAT_MODULE_IMPORTS = listOf(
            "import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill",
            "import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleFightBot",
            "import net.ccbluex.liquidbounce.features.module.modules.combat.*",
        )
        private val EXPECTED_BRIDGE_METHODS = listOf(
            "initializePreview",
            "currentAttackVelocity",
            "currentAttackDirection",
            "controlsSpearUse",
            "prepareSetbackCorrection",
            "finishSetbackCorrection",
            "clearAttack",
            "fightBotStateFor",
            "reservesFightBotSpearUse",
            "requestFightBotSpearUse",
            "releaseFightBotSpearUse",
            "tryStartPacketChain",
            "canAcceptKillAuraTarget",
            "startHighSpeedResearchProbe",
            "routeRotationOverride",
            "controlsSpearAnimation",
            "ownsKillAuraSpearUse",
            "shouldAnimateRaisedSpear",
            "raisedSpearHand",
            "getSpearAnimationTicks",
            "getSpearAnimationTicks",
            "registerHandlers",
            "disable",
        )
        private val BRIDGE_METHOD_PATTERN = Regex("""\bfun\s+([A-Za-z0-9_]+)\s*\(""")
        private val BRIDGE_STATE_PORT_PATTERN = Regex(
            """\bfun\s+[A-Za-z0-9_]+\s*\(\s*module:\s*SpearKillModuleState\b""",
        )
    }
}
