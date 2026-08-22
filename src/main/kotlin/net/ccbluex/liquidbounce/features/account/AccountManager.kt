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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import net.ccbluex.liquidbounce.api.thirdparty.TheAlteningApi
import net.ccbluex.liquidbounce.api.thirdparty.TheAlteningApiException
import net.ccbluex.liquidbounce.api.thirdparty.TheAlteningGenerationResult
import net.ccbluex.liquidbounce.api.thirdparty.toGenerationResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.AccountManagerAdditionResultEvent
import net.ccbluex.liquidbounce.event.events.AccountManagerLoginResultEvent
import net.ccbluex.liquidbounce.event.events.AccountManagerRemovalResultEvent
import net.ccbluex.liquidbounce.event.events.SessionEvent
import net.ccbluex.liquidbounce.injection.mixins.realms.MixinRealmsAvailabilityAccessor
import net.ccbluex.liquidbounce.injection.mixins.realms.MixinRealmsClientAccessor
import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager
import net.ccbluex.liquidbounce.integration.screen.impl.MicrosoftLoginScreen
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.with
import net.minecraft.client.multiplayer.ProfileKeyPairManager
import java.io.InterruptedIOException
import java.net.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@Suppress("TooManyFunctions")
object AccountManager : Config("Accounts"), EventListener {

    val accounts by list(name, mutableListOf<MinecraftAccount>(), ValueType.ACCOUNT)

    private var initialSession: SessionBundle

    private val loggingIn = AtomicBoolean(false)

    private const val THE_ALTENING_GENERATE_TIMEOUT_MS = 12_000L
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
        invalidateRealms()

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

    /**
     * Re-triggering the device code flow while one is running re-shows this URL instead of asking
     * Microsoft for a new code.
     */
    private var activeDeviceCodeUrl: String? = null

    /**
     * Guards the WebView and credentials flows only - the device code flow uses [activeDeviceCodeUrl].
     */
    private val microsoftLoginInProgress = AtomicBoolean(false)

    /**
     * Blocks only until the verification URL is known and hands it to [url]; the account itself is created
     * once the user signs in elsewhere, surfaced via [AccountManagerAdditionResultEvent].
     */
    fun newMicrosoftAccountViaDeviceCode(url: (String) -> Unit) {
        val existingUrl = activeDeviceCodeUrl
        if (existingUrl != null) {
            url(existingUrl)
            return
        }

        val urlReady = CountDownLatch(1)

        thread(name = "microsoft-account-device-code", isDaemon = true) {
            runCatching {
                MicrosoftAccount.buildFromDeviceCode(onDeviceCode = { code ->
                    activeDeviceCodeUrl = code.directVerificationUri
                    url(code.directVerificationUri)
                    urlReady.countDown()
                })
            }.onSuccess { account ->
                activeDeviceCodeUrl = null
                handleNewMicrosoftAccount(account)
            }.onFailure {
                activeDeviceCodeUrl = null
                logger.error("Failed to create new account", it)
                EventManager.callEvent(AccountManagerAdditionResultEvent(error = it.message ?: "Unknown error"))
            }

            // In case buildFromDeviceCode failed before ever reaching the onDeviceCode callback
            urlReady.countDown()
        }

        urlReady.await()
    }

    /**
     * Runs asynchronously; the result is surfaced via [AccountManagerAdditionResultEvent].
     */
    fun newMicrosoftAccountViaWebView() {
        if (!microsoftLoginInProgress.compareAndSet(false, true)) {
            EventManager.callEvent(
                AccountManagerAdditionResultEvent(error = "A Microsoft sign-in is already in progress!")
            )
            return
        }

        // The login has to run in a browser we control, both to read the redirect back and to keep the
        // Microsoft session out of the client's cookie store.
        if (BrowserBackendManager.backend?.takeIf { it.isInitialized && it.supportsIncognito } == null) {
            microsoftLoginInProgress.set(false)
            EventManager.callEvent(
                AccountManagerAdditionResultEvent(error = "The browser is not available, use another sign-in method")
            )
            return
        }

        thread(name = "microsoft-account-webview", isDaemon = true) {
            try {
                runCatching {
                    MicrosoftAccount.buildFromWebView(
                        onOpen = { service ->
                            val url = service.authenticationUrl.toString()
                            mc.execute {
                                mc.gui.setScreen(MicrosoftLoginScreen(url, service, mc.gui.screen()))
                            }
                        },
                        onClose = {
                            mc.execute { (mc.gui.screen() as? MicrosoftLoginScreen)?.onClose() }
                        },
                    )
                }.onSuccess {
                    handleNewMicrosoftAccount(it)
                }.onFailure {
                    logger.error("Failed to create new account", it)
                    EventManager.callEvent(AccountManagerAdditionResultEvent(error = it.message ?: "Unknown error"))
                }
            } finally {
                microsoftLoginInProgress.set(false)
            }
        }
    }

    /**
     * Does not support accounts with two-factor authentication enabled. Runs asynchronously; the result is
     * surfaced via [AccountManagerAdditionResultEvent].
     */
    fun newMicrosoftAccountViaCredentials(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Email and password are required!"))
            return
        }

        if (!microsoftLoginInProgress.compareAndSet(false, true)) {
            EventManager.callEvent(
                AccountManagerAdditionResultEvent(error = "A Microsoft sign-in is already in progress!")
            )
            return
        }

        thread(name = "microsoft-account-credentials", isDaemon = true) {
            try {
                runCatching {
                    MicrosoftAccount.buildFromCredentials(email, password)
                }.onSuccess {
                    handleNewMicrosoftAccount(it)
                }.onFailure {
                    logger.error("Failed to create new account", it)
                    EventManager.callEvent(AccountManagerAdditionResultEvent(error = it.message ?: "Unknown error"))
                }
            } finally {
                microsoftLoginInProgress.set(false)
            }
        }
    }

    private fun handleNewMicrosoftAccount(account: MicrosoftAccount) {
        val profile = account.profile
        if (profile == null) {
            logger.error("Failed to get profile")
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Failed to get profile"))
            return
        }

        logger.info("Logged in as new account ${account.username}")

        val existingAccount = accounts.find {
            it.service == account.service && it.username == account.username
        }

        if (existingAccount != null) {
            transferAccountMetadata(existingAccount, account)
            accounts[accounts.indexOf(existingAccount)] = account
        } else {
            accounts += account
        }

        ConfigSystem.store(this@AccountManager)

        EventManager.callEvent(AccountManagerAdditionResultEvent(username = profile.name))
    }

    fun newAlteningAccount(accountToken: String) = runCatching {
        accounts += AlteningAccount.fromToken(accountToken).apply {
            val profile = this.profile

            if (profile == null) {
                EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Failed to get profile"))
                return@runCatching
            }

            EventManager.callEvent(AccountManagerAdditionResultEvent(username = profile.name))
        }

        ConfigSystem.store(this@AccountManager)
    }.onFailure {
        logger.error("Failed to login into altening account (for add-process)", it)
        EventManager.callEvent(AccountManagerAdditionResultEvent(error = it.message ?: "Unknown error"))
    }

    suspend fun generateAlteningAccount(apiToken: String): TheAlteningGenerationResult {
        return try {
            val generatedAccount = withTimeout(THE_ALTENING_GENERATE_TIMEOUT_MS) {
                TheAlteningApi.generate(apiToken)
            }
            val account = createPendingAlteningAccount(generatedAccount)
            val username = account.username

            accounts += account
            ConfigSystem.store(this@AccountManager)
            EventManager.callEvent(AccountManagerAdditionResultEvent(username = username))
            TheAlteningGenerationResult.success(username)
        } catch (exception: TheAlteningApiException) {
            logger.error("Failed to generate altening account", exception)
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = exception.userMessage))
            exception.toGenerationResult()
        } catch (exception: TimeoutCancellationException) {
            logger.error("Failed to generate altening account", exception)
            generationError(exception.generationErrorMessage())
        } catch (exception: Exception) {
            logger.error("Failed to generate altening account", exception)
            generationError(exception.generationErrorMessage())
        }
    }

    private fun Exception.accountLoginErrorMessage(): String = when (this) {
        is InterruptedIOException -> GENERATED_ALTENING_ACCOUNT_RESOLVE_TIMEOUT_MESSAGE
        else -> message ?: "Unknown error"
    }

    private fun Exception.generationErrorMessage(): String = when (this) {
        is TimeoutCancellationException -> "Failed to contact TheAltening. Try again later."
        else -> message ?: "Unknown error"
    }

    private fun generationError(message: String): TheAlteningGenerationResult {
        EventManager.callEvent(AccountManagerAdditionResultEvent(error = message))
        return TheAlteningGenerationResult.error(message)
    }

    fun restoreInitial() {
        val initialSession = initialSession
        mc.user = initialSession.session
        mc.services = mc.services.with(
            initialSession.sessionService ?: mc.services.sessionService
        )
        mc.profileKeyPairManager = initialSession.profileKeys
        invalidateRealms()

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

    /**
     * Realms builds its client from the session that was active at the time, keeps it in a static field
     * and never compares it against the current one, and it never retries an availability check that
     * failed with an authentication error. Both caches have to be dropped by hand.
     */
    private fun invalidateRealms() {
        MixinRealmsClientAccessor.setRealmsClientInstance(null)
        MixinRealmsAvailabilityAccessor.setFuture(null)
    }
    fun favoriteAccount(id: Int) {
        val account = accounts.getOrNull(id) ?: error("Account not found!")
        account.favorite = true
        ConfigSystem.store(this@AccountManager)
    }

    fun unfavoriteAccount(id: Int) {
        val account = accounts.getOrNull(id) ?: error("Account not found!")
        account.favorite = false
        ConfigSystem.store(this@AccountManager)
    }

    fun swapAccounts(index1: Int, index2: Int) {
        val account1 = accounts.getOrNull(index1) ?: error("Account not found!")
        val account2 = accounts.getOrNull(index2) ?: error("Account not found!")
        accounts[index1] = account2
        accounts[index2] = account1
        ConfigSystem.store(this@AccountManager)
    }

    fun orderAccounts(order: List<Int>) {
        order.map { index -> accounts[index] }
            .forEachIndexed { index, serverInfo ->
                accounts[index] = serverInfo
            }

        ConfigSystem.store(this@AccountManager)
    }

    fun removeAccount(id: Int): MinecraftAccount {
        val account = accounts.removeAt(id).apply { ConfigSystem.store(this@AccountManager) }
        EventManager.callEvent(AccountManagerRemovalResultEvent(account.username))
        return account
    }

    fun newSessionAccount(token: String) {
        if (token.isEmpty()) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Token is empty!"))
            return
        }

        val account: MinecraftAccount = try {
            if (token.startsWith("M.")) {
                MicrosoftAccount.buildFromRefreshToken(token)
            } else {
                SessionAccount(token).apply {
                    refresh()
                }
            }
        } catch (exception: Exception) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = exception.message ?: "Unknown error"))
            return
        }

        val profile = account.profile

        if (profile == null) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Failed to get profile"))
            return
        }

        if (accounts.any { it.username.equals(account.username, true) }) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Account already exists!"))
            return
        }

        accounts += account
        ConfigSystem.store(this@AccountManager)
        EventManager.callEvent(AccountManagerAdditionResultEvent(username = profile.name))
    }

}

internal fun transferAccountMetadata(from: MinecraftAccount, to: MinecraftAccount) {
    to.favorite = from.favorite
    to.bans.putAll(from.bans)
    AccountServerAccessRegistry.restore(to, AccountServerAccessRegistry.list(from))
}
