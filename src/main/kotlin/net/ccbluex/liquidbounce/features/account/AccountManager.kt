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

package net.ccbluex.liquidbounce.features.account

import com.mojang.authlib.yggdrasil.YggdrasilEnvironment
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.AccountManagerAdditionResultEvent
import net.ccbluex.liquidbounce.event.events.AccountManagerLoginResultEvent
import net.ccbluex.liquidbounce.event.events.SessionEvent
import net.ccbluex.liquidbounce.features.account.contract.AccountRuntimeBridge
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.with
import net.minecraft.client.multiplayer.ProfileKeyPairManager
import java.io.InterruptedIOException
import java.net.Proxy
import java.util.concurrent.atomic.AtomicBoolean

object AccountManager : Config("Accounts"), EventListener,
    AccountCreationOperations by AccountCreationDelegate,
    AccountCollectionOperations by AccountCollectionDelegate {

    val accounts by list(name, mutableListOf<MinecraftAccount>(), ValueType.ACCOUNT)

    private var initialSession: SessionBundle

    private val loggingIn = AtomicBoolean(false)

    private const val GENERATED_ALTENING_ACCOUNT_RESOLVE_TIMEOUT_MESSAGE =
        "TheAltening authentication server is not responding. Try again later."

    init {
        ConfigSystem.root(this)

        try {
            initialSession = SessionBundle(mc.user, mc.services.sessionService, mc.profileKeyPairManager)
            logger.info("Initial session saved: ${mc.user.name} (${mc.user.profileId})")
        } catch (e: Exception) {
            logger.error("Failed to save initial session", e)
            initialSession = SessionBundle(mc.user, null, ProfileKeyPairManager.EMPTY_KEY_MANAGER)
        }
    }

    fun loginAccount(id: Int) {
        if (!loggingIn.compareAndSet(false, true)) {
            EventManager.callEvent(AccountManagerLoginResultEvent(error = "Logging in already started!"))
            return
        }

        val account = accounts.getOrNull(id) ?: run {
            EventManager.callEvent(AccountManagerLoginResultEvent(error = "Account not found!"))
            loggingIn.set(false)
            return
        }
        try {
            if (loginDirectAccount(account)) {
                ConfigSystem.store(this@AccountManager)
            }
        } finally {
            loggingIn.set(false)
        }
    }

    fun loginDirectAccount(account: MinecraftAccount) = try {
        logger.info("Start logging in with username '${account.username}'")
        val (session, service) = account.login()

        val profileKeys = runCatching {
            // In this case the environment doesn't matter, as it is only used for the profile key
            val environment = YggdrasilEnvironment.PROD.environment
            val userAuthenticationService = YggdrasilUserApiService(session.accessToken, Proxy.NO_PROXY, environment)
            ProfileKeyPairManager.create(userAuthenticationService, session, mc.gameDirectory.toPath())
        }.onFailure {
            logger.error("Failed to create profile keys for ${session.name} due to ${it.message}")
        }.getOrDefault(ProfileKeyPairManager.EMPTY_KEY_MANAGER)

        mc.user = session
        mc.services = mc.services.with(
            service.createMinecraftSessionService(),
            service.servicesKeySet,
            service.createProfileRepository(),
        )
        mc.profileKeyPairManager = profileKeys
        AccountRuntimeBridge.invalidateRealmsSessionCaches()

        EventManager.callEvent(SessionEvent(session))
        EventManager.callEvent(AccountManagerLoginResultEvent(username = account.username))
        true
    } catch (e: Exception) {
        logger.error("Failed to login into account", e)
        EventManager.callEvent(AccountManagerLoginResultEvent(error = e.accountLoginErrorMessage()))
        false
    }

    /**
     * Cannot join premium servers.
     */
    fun newCrackedAccount(username: String, online: Boolean = false) {
        if (username.isEmpty()) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Username is empty!"))
            return
        }

        if (username.length > 16) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Username is too long!"))
            return
        }

        if (accounts.any { it.username.equals(username, true) }) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Account already exists!"))
            return
        }

        accounts += CrackedAccount(username, online).also { it.refresh() }

        ConfigSystem.store(this@AccountManager)

        EventManager.callEvent(AccountManagerAdditionResultEvent(username = username))
    }

    fun loginCrackedAccount(username: String, online: Boolean = false) {
        if (username.isEmpty()) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Username is empty!"))
            return
        }

        if (username.length > 16) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Username is too long!"))
            return
        }

        val account = CrackedAccount(username, online).also { it.refresh() }
        loginDirectAccount(account)
    }

    fun loginSessionAccount(token: String) {
        val account = if (token.startsWith("M.")) {
            MicrosoftAccount.buildFromRefreshToken(token)
        } else {
            SessionAccount(token).apply {
                refresh()
            }
        }

        loginDirectAccount(account)
    }

    private fun Exception.accountLoginErrorMessage(): String = when (this) {
        is InterruptedIOException -> GENERATED_ALTENING_ACCOUNT_RESOLVE_TIMEOUT_MESSAGE
        else -> message ?: "Unknown error"
    }

    fun restoreInitial() {
        val initialSession = initialSession
        mc.user = initialSession.session
        mc.services = mc.services.with(
            initialSession.sessionService ?: mc.services.sessionService
        )
        mc.profileKeyPairManager = initialSession.profileKeys
        AccountRuntimeBridge.invalidateRealmsSessionCaches()

        EventManager.callEvent(SessionEvent(mc.user))
        EventManager.callEvent(AccountManagerLoginResultEvent(username = mc.user.name))
    }

    internal fun trackCurrentAccountBan(serverName: String, reason: String, bannedUntil: Long): Boolean {
        val account = currentAccount() ?: return false

        account.trackBan(Ban(serverName, reason, bannedUntil))
        AccountServerAccessRegistry.markUnavailable(account, serverName)
        ConfigSystem.store(this@AccountManager)
        return true
    }

    internal fun trackCurrentAccountServerAccess(serverName: String): Boolean {
        val account = currentAccount() ?: return false
        val normalizedServerName = normalizeServerName(serverName)
        val matchingBans = account.bans.keys.filter { serverNamesMatch(it, normalizedServerName) }

        matchingBans.forEach(account::untrackBan)
        val accessChanged = AccountServerAccessRegistry.markWorking(account, normalizedServerName)
        if (matchingBans.isNotEmpty() || accessChanged) {
            ConfigSystem.store(this@AccountManager)
        }
        return true
    }

    private fun currentAccount(): MinecraftAccount? =
        accounts.firstOrNull { it.profile?.id == mc.user.profileId }
            ?: accounts.firstOrNull { it.profile?.name.equals(mc.user.name, ignoreCase = true) }

}

internal fun transferAccountMetadata(from: MinecraftAccount, to: MinecraftAccount) {
    to.favorite = from.favorite
    to.bans.putAll(from.bans)
    AccountServerAccessRegistry.restore(to, AccountServerAccessRegistry.list(from))
}
