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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.ccbluex.liquidbounce.api.core.ApiConfig
import net.ccbluex.liquidbounce.api.models.auth.ClientAccount
import net.ccbluex.liquidbounce.api.services.client.ClientUpdate
import net.ccbluex.liquidbounce.api.thirdparty.IpInfoApi
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.autoconfig.AutoConfig
import net.ccbluex.liquidbounce.features.cosmetic.ClientAccountManager
import net.ccbluex.liquidbounce.features.cosmetic.CosmeticService
import net.ccbluex.liquidbounce.features.creativetab.tabs.HeadsCreativeModeTab
import net.ccbluex.liquidbounce.features.language.LanguageManager
import net.ccbluex.liquidbounce.utils.client.logger
import kotlin.time.Duration.Companion.seconds

internal object ClientResourceInitializer {
    suspend fun initialize(dispatcher: CoroutineDispatcher) = withContext(dispatcher) {
        logger.info("Initializing API...")
        ApiConfig.config
        supervisorScope {
            launch { LanguageManager.loadDefault() }
            launch { reportAvailableUpdate() }
            launch { refreshCosmetics() }
            launch { HeadsCreativeModeTab.heads.getFinalState() }
            launch { AutoConfig.reloadConfigs() }
            launch { IpInfoApi.original }
            launch { refreshClientAccount() }
        }
        logger.info("API initialization done.")
    }

    private suspend fun reportAvailableUpdate() {
        val update = withTimeoutOrNull(8.seconds) { ClientUpdate.update.await() } ?: return
        logger.info("[Update] Update available: ${LiquidBounceClientConfig.clientVersion} -> ${update.lbVersion}")
    }

    private suspend fun refreshCosmetics() {
        CosmeticService.refreshCarriers(force = true) {
            logger.info("Successfully loaded ${CosmeticService.carriers.size} cosmetics carriers.")
        }
    }

    private suspend fun refreshClientAccount() {
        ConfigSystem.load(ClientAccountManager)
        ClientAccount.ENV_ACCOUNT?.let { ClientAccountManager.clientAccount = it }
        if (ClientAccountManager.clientAccount == ClientAccount.EMPTY_ACCOUNT) {
            return
        }
        runCatching {
            ClientAccountManager.clientAccount.renew()
        }.onFailure {
            logger.error("Failed to renew client account token.", it)
            ClientAccountManager.clientAccount = ClientAccount.EMPTY_ACCOUNT
        }.onSuccess {
            logger.info("Successfully renewed client account token.")
        }
        ConfigSystem.store(ClientAccountManager)
    }
}
