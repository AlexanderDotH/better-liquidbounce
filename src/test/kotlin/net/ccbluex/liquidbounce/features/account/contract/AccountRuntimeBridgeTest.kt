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
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccountRuntimeBridgeTest {

    @Test
    fun `installed provider preserves browser readiness close and realms invalidation`() {
        val provider = RecordingAccountRuntimeProvider()

        AccountRuntimeBridge.withProviderForTest(null) {
            AccountRuntimeBridge.install(provider)

            assertTrue(AccountRuntimeBridge.isMicrosoftWebViewAvailable())
            AccountRuntimeBridge.closeMicrosoftWebView()
            AccountRuntimeBridge.invalidateRealmsSessionCaches()
        }

        assertEquals(1, provider.closeCount)
        assertEquals(1, provider.invalidationCount)
    }

    @Test
    fun `missing provider fails before an account login can silently skip runtime work`() {
        AccountRuntimeBridge.withProviderForTest(null) {
            assertFailsWith<IllegalStateException> {
                AccountRuntimeBridge.invalidateRealmsSessionCaches()
            }
        }
    }

    private class RecordingAccountRuntimeProvider : AccountRuntimeProvider {
        var closeCount = 0
        var invalidationCount = 0

        override fun invalidateRealmsSessionCaches() {
            invalidationCount++
        }

        override fun isMicrosoftWebViewAvailable() = true

        override fun openMicrosoftWebView(service: ExternalBrowserMsaAuthService) = Unit

        override fun closeMicrosoftWebView() {
            closeCount++
        }
    }
}
