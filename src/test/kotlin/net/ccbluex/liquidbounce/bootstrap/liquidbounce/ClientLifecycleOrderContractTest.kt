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
package net.ccbluex.liquidbounce.bootstrap.liquidbounce

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class ClientLifecycleOrderContractTest {
    @Test
    fun `initialization stages preserve their historical order`() {
        val initializer = source("bootstrap/liquidbounce/ClientInitializer.kt")
        val initialization = initializer
            .substringAfter("private suspend fun initializeOnRenderThread(")
            .substringBefore("private fun installRuntimeAdapters()")

        assertOrdered(
            initialization,
            "RenderSystem.assertOnRenderThread()",
            "EventManager.registerEventClass(WorldRenderEvent::class.java)",
            "installRuntimeAdapters()",
            "LiquidBounceClientConfig",
            "ClientManagerInitializer.initialize(workerDispatcher, renderThreadDispatcher)",
            "initializeFeatures()",
            "ClientResourceInitializer.initialize(workerDispatcher)",
            "ClientGuiInitializer.initialize(renderThreadDispatcher)",
            "Runtime.getRuntime().addShutdownHook",
            "reportAmdVegaApu()",
            "backupBeforeConfigLoad()",
            "ConfigSystem.loadAll()",
            "ClientLifecycleState.isInitialized = true",
        )
    }

    @Test
    fun `runtime adapters preserve their historical installation order`() {
        val initializer = source("bootstrap/liquidbounce/ClientInitializer.kt")
        val adapters = initializer
            .substringAfter("private fun installRuntimeAdapters()")
            .substringBefore("private fun installEspMaskFeatureAdapter()")

        assertOrdered(
            adapters,
            "ScoreboardEntryOrderAdapter.install()",
            "ClickGuiRuntimeInjectionAdapter.install()",
            "HudRuntimeIntegrationAdapter.install()",
            "ChamsRenderTypeInjectionAdapter.install()",
            "ParticleColorInjectionAdapter.install()",
            "SkinSessionEndpointInjectionAdapter.install()",
            "DebugScriptInventoryAdapter.install()",
            "ScriptCommandAdapter.install()",
            "ClientCommandRuntimeAdapter.install()",
            "HudBlurEffectAdapter.install(ModuleHud) { FeatureSilentScreen.shouldHide }",
            "HudSelectionSpriteInjectionAdapter.install()",
            "RenderSetupInjectionAdapter.install()",
            "AutoTranslateDefaultLanguageAdapter.install()",
            "ModelCommandIntegrationAdapter.install()",
            "AiAngleSmoothDeepLearningAdapter.install()",
            "MarketplaceContentReloadAdapter.install()",
            "RenderGsonAdapter.install()",
            "ThemeGsonAdapter.install()",
            "MinecraftAccountGsonAdapter.install()",
            "installEspMaskFeatureAdapter()",
            "MinecraftClientFeatureAdapter.install()",
            "ClientLevelFeatureAdapter.install()",
            "TrajectoryFreezeStateBridge.install { ModuleFreeze.running }",
            "BlinkDummyState.install(ModuleBlink::isDummyPlayer)",
            "DebugGeometrySinkAdapter.install()",
            "CombatTargetSnapshotAdapter.install()",
            "CrystalAttackSinkAdapter.install()",
        )
    }

    @Test
    fun `ESP combat presentation is installed before its mask selector`() {
        val initializer = source("bootstrap/liquidbounce/ClientInitializer.kt")
            .substringAfter("private fun installEspMaskFeatureAdapter()")
            .substringBefore("private fun initializeFeatures()")

        assertOrdered(
            initializer,
            "EspMaskFeatureAdapter.installCombatPresentation()",
            "EspMaskFeatureSelectorRegistry.install(EspMaskFeatureAdapter)",
        )
    }

    @Test
    fun `shutdown stages preserve their historical order`() {
        assertOrdered(
            source("bootstrap/liquidbounce/ClientLifecycle.kt"),
            "ClientLifecycleState.isInitialized = false",
            "BaritoneIntegration.shutdown()",
            "ChunkScanner.stopThread()",
            "FontManager.closeGlyphManager()",
            "EventManager.unregisterAll()",
            "ClientInteropServer.stop()",
            "ConfigSystem.storeAll()",
            "BrowserBackendManager.stop()",
        )
    }

    @Test
    fun `task screen handler retains first priority`() {
        val facade = source("LiquidBounce.kt")

        assertTrue(facade.contains("handler<ScreenEvent>(priority = FIRST_PRIORITY)"))
    }

    @Test
    fun `manager bootstrap initializes command event wiring`() {
        val managers = source("bootstrap/liquidbounce/ClientManagerInitializer.kt")

        assertTrue(managers.contains("CommandManager.initialize()"))
        assertOrdered(
            managers.substringBefore("private fun initializeUtilityListeners()"),
            "initializeUtilityListeners()",
            "initializeFeatureManagers()",
        )
        assertOrdered(
            managers.substringAfter("private fun initializeUtilityListeners()"),
            "ConfigSystem",
            "ModuleNameCollector.installCaptureRoot(ConfigSystem.rootFolder)",
            "ClientRuntimeHooksAdapter.install()",
        )
    }

    @Test
    fun `Baritone dashboard port is installed before its event publisher`() {
        val utilityListeners = source("bootstrap/liquidbounce/ClientManagerInitializer.kt")
            .substringAfter("private fun initializeUtilityListeners()")

        assertOrdered(
            utilityListeners,
            "BaritoneIntegration.initialize()",
            "BaritoneScreenAdapter.install()",
            "BaritoneEventPublisher",
        )
    }

    @Test
    fun `rotation providers and event coordinator initialize before the manager facade`() {
        val utilityManagers = source("bootstrap/liquidbounce/ClientManagerInitializer.kt")
            .substringAfter("private fun initializeUtilityManagers()")

        assertOrdered(
            utilityManagers,
            "RotationFeatureAdapter.install()",
            "RotationEventCoordinator",
            "RotationManager",
        )
    }

    private fun source(relativePath: String): String = Path.of(
        "src/main/kotlin/net/ccbluex/liquidbounce/$relativePath"
    ).readText()

    private fun assertOrdered(source: String, vararg tokens: String) {
        val positions = tokens.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing lifecycle token: ${tokens.zip(positions)}")
        assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
    }
}
