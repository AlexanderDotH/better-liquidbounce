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
package net.ccbluex.liquidbounce.features.account.contract

import net.raphimc.minecraftauth.msa.service.impl.ExternalBrowserMsaAuthService

interface AccountRuntimeProvider {
    fun invalidateRealmsSessionCaches()
    fun isMicrosoftWebViewAvailable(): Boolean
    fun openMicrosoftWebView(service: ExternalBrowserMsaAuthService)
    fun closeMicrosoftWebView()
}

object AccountRuntimeBridge {

    @Volatile
    private var provider: AccountRuntimeProvider? = null

    @Synchronized
    fun install(provider: AccountRuntimeProvider) {
        check(this.provider == null) { "Account runtime provider is already installed" }
        this.provider = provider
    }

    fun invalidateRealmsSessionCaches() = requireProvider().invalidateRealmsSessionCaches()

    fun isMicrosoftWebViewAvailable() = requireProvider().isMicrosoftWebViewAvailable()

    fun openMicrosoftWebView(service: ExternalBrowserMsaAuthService) =
        requireProvider().openMicrosoftWebView(service)

    fun closeMicrosoftWebView() = requireProvider().closeMicrosoftWebView()

    private fun requireProvider(): AccountRuntimeProvider =
        checkNotNull(provider) { "Account runtime provider is not installed" }

    @Synchronized
    internal fun <T> withProviderForTest(candidate: AccountRuntimeProvider?, block: () -> T): T {
        val previous = provider
        provider = candidate
        return try {
            block()
        } finally {
            provider = previous
        }
    }
}
