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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.PlayerSimulationEventBridge
import net.ccbluex.liquidbounce.event.rotation.RotationEventCoordinator
import net.ccbluex.liquidbounce.features.account.AccountBanTracker
import net.ccbluex.liquidbounce.features.account.AccountManager
import net.ccbluex.liquidbounce.features.account.AccountServerAccessTracker
import net.ccbluex.liquidbounce.features.baritone.BaritoneIntegration
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.command.CommandManager
import net.ccbluex.liquidbounce.features.combat.runtime.CombatActivityAdapter
import net.ccbluex.liquidbounce.features.combat.runtime.shouldBeShown
import net.ccbluex.liquidbounce.features.cosmetic.ClientAccountManager
import net.ccbluex.liquidbounce.features.global.GlobalManager
import net.ccbluex.liquidbounce.features.global.GlobalSettingsTarget
import net.ccbluex.liquidbounce.features.marketplace.MarketplaceManager
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.misc.proxy.ProxyManager
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.rotation.BlockPlacementRotationAdapter
import net.ccbluex.liquidbounce.features.rotation.RotationFeatureAdapter
import net.ccbluex.liquidbounce.features.spoofer.SpooferManager
import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberRuntime
import net.ccbluex.liquidbounce.integration.backend.browser.GlobalBrowserSettingsAdapter
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneEventPublisher
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.ActiveServerList
import net.ccbluex.liquidbounce.integration.screen.BaritoneScreenAdapter
import net.ccbluex.liquidbounce.render.atlas.ItemImageAtlas
import net.ccbluex.liquidbounce.script.ScriptManager
import net.ccbluex.liquidbounce.features.rotation.PostRotationExecutor
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.features.interaction.InteractionTracker
import net.ccbluex.liquidbounce.features.server.ServerObserver
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.features.combat.runtime.CombatManager
import net.ccbluex.liquidbounce.features.module.modules.exploit.ModuleNameCollector
import net.ccbluex.liquidbounce.features.render.RenderedEntities
import net.ccbluex.liquidbounce.features.input.InputTracker
import net.ccbluex.liquidbounce.integration.inventory.EnderChestInventoryTracker
import net.ccbluex.liquidbounce.features.inventory.InventoryManager

internal object ClientManagerInitializer {
    suspend fun initialize(
        workerDispatcher: CoroutineDispatcher,
        renderThreadDispatcher: CoroutineDispatcher,
    ) = withContext(renderThreadDispatcher) {
        val scriptEngineJob = launch(workerDispatcher) {
            runCatching(ScriptManager::initializeEngine).onFailure { error ->
                logger.error("[ScriptAPI] Failed to initialize script engine.", error)
            }
        }
        initializeUtilityListeners()
        initializeFeatureManagers()
        initializeUtilityManagers()
        scriptEngineJob.join()
    }

    private fun initializeUtilityListeners() {
        BlockPlacementRotationAdapter.install()
        ConfigSystem
        ModuleNameCollector.installCaptureRoot(ConfigSystem.rootFolder)
        ClientRuntimeHooksAdapter.install()
        PlayerSimulationEventBridge.install()
        RenderedEntities.installVisibilityPolicy(
            shouldRenderEntity = { entity -> entity.shouldBeShown() },
            shouldRefreshOnPerspective = { GlobalSettingsTarget.rendersSelf },
        )
        ChunkScanner
        TrialChamberRuntime.initialize()
        InputTracker
        BaritoneIntegration.initialize()
        BaritoneScreenAdapter.install()
        BaritoneEventPublisher
    }

    private fun initializeFeatureManagers() {
        ModuleManager
        CommandManager.initialize()
        ProxyManager
        AccountManager
        AccountBanTracker
        AccountServerAccessTracker
    }

    private fun initializeUtilityManagers() {
        RotationFeatureAdapter.install()
        RotationEventCoordinator
        RotationManager
        BlinkManager
        InteractionTracker
        CombatManager
        CombatActivityAdapter.install()
        FriendManager
        InventoryManager
        EnderChestInventoryTracker
        ActiveServerList
        ConfigSystem.root(ClientAccountManager)
        ConfigSystem.root(SpooferManager)
        GlobalBrowserSettingsAdapter.install()
        ConfigSystem.root(GlobalManager)
        ConfigSystem.root(MarketplaceManager)
        PostRotationExecutor
        ServerObserver
        ItemImageAtlas
    }
}
