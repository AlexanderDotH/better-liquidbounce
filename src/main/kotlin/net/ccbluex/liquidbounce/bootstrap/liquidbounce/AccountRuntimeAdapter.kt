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

import net.ccbluex.liquidbounce.features.account.contract.AccountRuntimeBridge
import net.ccbluex.liquidbounce.features.account.contract.AccountRuntimeProvider
import net.ccbluex.liquidbounce.injection.mixins.realms.MixinRealmsAvailabilityAccessor
import net.ccbluex.liquidbounce.injection.mixins.realms.MixinRealmsClientAccessor
import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager
import net.ccbluex.liquidbounce.integration.screen.impl.MicrosoftLoginScreen
import net.ccbluex.liquidbounce.utils.client.mc
import net.raphimc.minecraftauth.msa.service.impl.ExternalBrowserMsaAuthService

internal object AccountRuntimeAdapter : AccountRuntimeProvider {

    fun install() = AccountRuntimeBridge.install(this)

    override fun invalidateRealmsSessionCaches() {
        MixinRealmsClientAccessor.setRealmsClientInstance(null)
        MixinRealmsAvailabilityAccessor.setFuture(null)
    }

    override fun isMicrosoftWebViewAvailable(): Boolean =
        BrowserBackendManager.backend?.let { it.isInitialized && it.supportsIncognito } == true

    override fun openMicrosoftWebView(service: ExternalBrowserMsaAuthService) {
        val url = service.authenticationUrl.toString()
        mc.execute {
            mc.gui.setScreen(MicrosoftLoginScreen(url, service, mc.gui.screen()))
        }
    }

    override fun closeMicrosoftWebView() {
        mc.execute {
            (mc.gui.screen() as? MicrosoftLoginScreen)?.onClose()
        }
    }
}
