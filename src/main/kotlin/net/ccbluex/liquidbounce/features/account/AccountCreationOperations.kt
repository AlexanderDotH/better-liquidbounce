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
package net.ccbluex.liquidbounce.features.account

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import net.ccbluex.liquidbounce.api.thirdparty.TheAlteningApi
import net.ccbluex.liquidbounce.api.thirdparty.TheAlteningApiException
import net.ccbluex.liquidbounce.api.thirdparty.TheAlteningGenerationResult
import net.ccbluex.liquidbounce.api.thirdparty.toGenerationResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.AccountManagerAdditionResultEvent
import net.ccbluex.liquidbounce.features.account.contract.AccountRuntimeBridge
import net.ccbluex.liquidbounce.utils.client.logger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

interface AccountCreationOperations {
    fun newMicrosoftAccountViaDeviceCode(url: (String) -> Unit)
    fun newMicrosoftAccountViaWebView()
    fun newMicrosoftAccountViaCredentials(email: String, password: String)
    fun newAlteningAccount(accountToken: String): Result<Unit>
    suspend fun generateAlteningAccount(apiToken: String): TheAlteningGenerationResult
    fun newSessionAccount(token: String)
}

internal object AccountCreationDelegate : AccountCreationOperations {

    private const val THE_ALTENING_GENERATE_TIMEOUT_MS = 12_000L

    private var activeDeviceCodeUrl: String? = null
    private val microsoftLoginInProgress = AtomicBoolean(false)

    override fun newMicrosoftAccountViaDeviceCode(url: (String) -> Unit) {
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

            urlReady.countDown()
        }

        urlReady.await()
    }

    override fun newMicrosoftAccountViaWebView() {
        if (!microsoftLoginInProgress.compareAndSet(false, true)) {
            EventManager.callEvent(
                AccountManagerAdditionResultEvent(error = "A Microsoft sign-in is already in progress!")
            )
            return
        }

        if (!AccountRuntimeBridge.isMicrosoftWebViewAvailable()) {
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
                        onOpen = AccountRuntimeBridge::openMicrosoftWebView,
                        onClose = { AccountRuntimeBridge.closeMicrosoftWebView() },
                    )
                }.onSuccess(::handleNewMicrosoftAccount).onFailure {
                    logger.error("Failed to create new account", it)
                    EventManager.callEvent(AccountManagerAdditionResultEvent(error = it.message ?: "Unknown error"))
                }
            } finally {
                microsoftLoginInProgress.set(false)
            }
        }
    }

    override fun newMicrosoftAccountViaCredentials(email: String, password: String) {
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
                }.onSuccess(::handleNewMicrosoftAccount).onFailure {
                    logger.error("Failed to create new account", it)
                    EventManager.callEvent(AccountManagerAdditionResultEvent(error = it.message ?: "Unknown error"))
                }
            } finally {
                microsoftLoginInProgress.set(false)
            }
        }
    }

    private fun handleNewMicrosoftAccount(account: MicrosoftAccount) {
        val profile = account.profile ?: run {
            logger.error("Failed to get profile")
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Failed to get profile"))
            return
        }

        logger.info("Logged in as new account ${account.username}")

        val existingAccount = AccountManager.accounts.find {
            it.service == account.service && it.username == account.username
        }

        if (existingAccount != null) {
            transferAccountMetadata(existingAccount, account)
            AccountManager.accounts[AccountManager.accounts.indexOf(existingAccount)] = account
        } else {
            AccountManager.accounts += account
        }

        ConfigSystem.store(AccountManager)
        EventManager.callEvent(AccountManagerAdditionResultEvent(username = profile.name))
    }

    override fun newAlteningAccount(accountToken: String) = runCatching {
        AccountManager.accounts += AlteningAccount.fromToken(accountToken).apply {
            val profile = this.profile

            if (profile == null) {
                EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Failed to get profile"))
                return@runCatching
            }

            EventManager.callEvent(AccountManagerAdditionResultEvent(username = profile.name))
        }

        ConfigSystem.store(AccountManager)
    }.onFailure {
        logger.error("Failed to login into altening account (for add-process)", it)
        EventManager.callEvent(AccountManagerAdditionResultEvent(error = it.message ?: "Unknown error"))
    }

    override suspend fun generateAlteningAccount(apiToken: String): TheAlteningGenerationResult {
        return try {
            val generatedAccount = withTimeout(THE_ALTENING_GENERATE_TIMEOUT_MS) {
                TheAlteningApi.generate(apiToken)
            }
            val account = createPendingAlteningAccount(generatedAccount)
            val username = account.username

            AccountManager.accounts += account
            ConfigSystem.store(AccountManager)
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

    private fun Exception.generationErrorMessage(): String = when (this) {
        is TimeoutCancellationException -> "Failed to contact TheAltening. Try again later."
        else -> message ?: "Unknown error"
    }

    private fun generationError(message: String): TheAlteningGenerationResult {
        EventManager.callEvent(AccountManagerAdditionResultEvent(error = message))
        return TheAlteningGenerationResult.error(message)
    }

    override fun newSessionAccount(token: String) {
        if (token.isEmpty()) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Token is empty!"))
            return
        }

        val account: MinecraftAccount = try {
            if (token.startsWith("M.")) {
                MicrosoftAccount.buildFromRefreshToken(token)
            } else {
                SessionAccount(token).apply { refresh() }
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

        if (AccountManager.accounts.any { it.username.equals(account.username, true) }) {
            EventManager.callEvent(AccountManagerAdditionResultEvent(error = "Account already exists!"))
            return
        }

        AccountManager.accounts += account
        ConfigSystem.store(AccountManager)
        EventManager.callEvent(AccountManagerAdditionResultEvent(username = profile.name))
    }
}
