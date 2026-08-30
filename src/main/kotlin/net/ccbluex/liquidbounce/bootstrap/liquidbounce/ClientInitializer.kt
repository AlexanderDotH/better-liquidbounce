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
package net.ccbluex.liquidbounce.bootstrap.liquidbounce

import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import net.ccbluex.liquidbounce.bootstrap.command.builtinCommands
import net.ccbluex.liquidbounce.bootstrap.command.AutoTranslateDefaultLanguageAdapter
import net.ccbluex.liquidbounce.bootstrap.command.ModelCommandIntegrationAdapter
import net.ccbluex.liquidbounce.bootstrap.module.builtinModules
import net.ccbluex.liquidbounce.common.ClientLifecycleState
import net.ccbluex.liquidbounce.common.runtime.BlinkDummyState
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.deeplearn.AiAngleSmoothDeepLearningAdapter
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.features.account.MinecraftAccountGsonAdapter
import net.ccbluex.liquidbounce.features.chat.ClientChatOutputAdapter
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.injection.ClientLevelFeatureAdapter
import net.ccbluex.liquidbounce.features.injection.MinecraftClientFeatureAdapter
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.ccbluex.liquidbounce.features.module.modules.misc.nameprotect.sanitizeForeignInput
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.player.cheststealer.features.FeatureSilentScreen
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.features.module.modules.render.esp.integration.EspMaskFeatureAdapter
import net.ccbluex.liquidbounce.features.module.modules.render.hud.HudBlurEffectAdapter
import net.ccbluex.liquidbounce.injection.ChamsRenderTypeInjectionAdapter
import net.ccbluex.liquidbounce.injection.ClickGuiRuntimeInjectionAdapter
import net.ccbluex.liquidbounce.injection.HudSelectionSpriteInjectionAdapter
import net.ccbluex.liquidbounce.injection.ParticleColorInjectionAdapter
import net.ccbluex.liquidbounce.injection.RenderSetupInjectionAdapter
import net.ccbluex.liquidbounce.injection.SkinSessionEndpointInjectionAdapter
import net.ccbluex.liquidbounce.integration.ClientCommandRuntimeAdapter
import net.ccbluex.liquidbounce.integration.MarketplaceContentReloadAdapter
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.CombatTargetSnapshotAdapter
import net.ccbluex.liquidbounce.integration.theme.HudRuntimeIntegrationAdapter
import net.ccbluex.liquidbounce.integration.theme.ThemeGsonAdapter
import net.ccbluex.liquidbounce.render.HAS_AMD_VEGA_APU
import net.ccbluex.liquidbounce.render.config.RenderGsonAdapter
import net.ccbluex.liquidbounce.render.engine.esp.EspMaskFeatureSelectorRegistry
import net.ccbluex.liquidbounce.render.engine.font.ForeignTextSanitizer
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.trajectory.TrajectoryFreezeStateBridge
import net.ccbluex.liquidbounce.script.DebugScriptInventoryAdapter
import net.ccbluex.liquidbounce.script.ScriptCommandAdapter
import net.ccbluex.liquidbounce.script.ScriptManager
import net.ccbluex.liquidbounce.utils.client.error.ErrorHandler
import net.ccbluex.liquidbounce.utils.client.logger
import java.util.concurrent.CompletableFuture

internal object ClientInitializer {
    fun initialize(
        workerDispatcher: CoroutineDispatcher,
        renderThreadDispatcher: CoroutineDispatcher,
    ): CompletableFuture<Void?> = CoroutineScope(
        renderThreadDispatcher + CoroutineName("$CLIENT_NAME Initializer")
    ).future<Void?> {
        initializeOnRenderThread(workerDispatcher, renderThreadDispatcher)
    }.exceptionally { throwable ->
        ErrorHandler.fatal(throwable, additionalMessage = "$CLIENT_NAME initializer")
    }

    private fun installRuntimeAdapters() {
        ScoreboardEntryOrderAdapter.install()
        ClickGuiRuntimeInjectionAdapter.install()
        HudRuntimeIntegrationAdapter.install()
        ChamsRenderTypeInjectionAdapter.install()
        ParticleColorInjectionAdapter.install()
        SkinSessionEndpointInjectionAdapter.install()
        DebugScriptInventoryAdapter.install()
        ScriptCommandAdapter.install()
        ClientCommandRuntimeAdapter.install()
        HudBlurEffectAdapter.install(ModuleHud) { FeatureSilentScreen.shouldHide }
        HudSelectionSpriteInjectionAdapter.install()
        RenderSetupInjectionAdapter.install()
        AutoTranslateDefaultLanguageAdapter.install()
        ModelCommandIntegrationAdapter.install()
        AiAngleSmoothDeepLearningAdapter.install()
        MarketplaceContentReloadAdapter.install()
        RenderGsonAdapter.install()
        ThemeGsonAdapter.install()
        MinecraftAccountGsonAdapter.install()
        AccountRuntimeAdapter.install()
        ClientChatOutputAdapter.install()
        ForeignTextSanitizer.install { component -> component.sanitizeForeignInput() }
        installEspMaskFeatureAdapter()
        MinecraftClientFeatureAdapter.install()
        ClientLevelFeatureAdapter.install()
        TrajectoryFreezeStateBridge.install { ModuleFreeze.running }
        BlinkDummyState.install(ModuleBlink::isDummyPlayer)
        DebugGeometrySinkAdapter.install()
        CombatTargetSnapshotAdapter.install()
        CrystalAttackSinkAdapter.install()
    }

    private fun installEspMaskFeatureAdapter() {
        EspMaskFeatureAdapter.installCombatPresentation()
        EspMaskFeatureSelectorRegistry.install(EspMaskFeatureAdapter)
    }

    private suspend fun initializeOnRenderThread(
        workerDispatcher: CoroutineDispatcher,
        renderThreadDispatcher: CoroutineDispatcher,
    ): Void? {
        if (ClientLifecycleState.isInitialized) {
            return null
        }
        RenderSystem.assertOnRenderThread()
        EventManager.registerEventClass(WorldRenderEvent::class.java)
        installRuntimeAdapters()
        LiquidBounceClientConfig
        ClientManagerInitializer.initialize(workerDispatcher, renderThreadDispatcher)
        initializeFeatures()
        ClientResourceInitializer.initialize(workerDispatcher)
        ClientGuiInitializer.initialize(renderThreadDispatcher)
        Runtime.getRuntime().addShutdownHook(Thread(ClientLifecycle::shutdown))
        reportAmdVegaApu()
        backupBeforeConfigLoad()
        ConfigSystem.loadAll()
        ClientLifecycleState.isInitialized = true
        logger.info("$CLIENT_NAME has been successfully initialized.")
        return null
    }

    private fun initializeFeatures() {
        CommandManager.registerInbuilt(builtinCommands.asIterable())
        ModuleManager.registerInbuilt(builtinModules.asIterable())
        runCatching(ScriptManager::loadAll).onFailure { error ->
            logger.error("ScriptManager was unable to load scripts.", error)
        }
    }

    private fun reportAmdVegaApu() {
        if (!HAS_AMD_VEGA_APU) {
            return
        }
        logger.info(
            "AMD Vega iGPU detected, enabling different line smooth handling. " +
                "If you believe this is a mistake, please create an issue at " +
                "https://github.com/CCBlueX/LiquidBounce/issues."
        )
    }

    private fun backupBeforeConfigLoad() {
        if (ConfigSystem.isFirstLaunch || LiquidBounceClientConfig.jsonFile.exists()) {
            return
        }
        runCatching {
            ConfigSystem.backup("automatic_${LiquidBounceClientConfig.version.inner}")
        }.onFailure {
            logger.error("Unable to create backup", it)
        }
    }
}
