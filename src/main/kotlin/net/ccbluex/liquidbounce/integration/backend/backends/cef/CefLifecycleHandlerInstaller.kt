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
package net.ccbluex.liquidbounce.integration.backend.backends.cef

import net.ccbluex.liquidbounce.integration.backend.browser.BrowserState
import net.ccbluex.liquidbounce.mcef.MCEF
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefRequest

internal object CefLifecycleHandlerInstaller {

    fun install(
        onCreated: (CefBrowser) -> Unit,
        onStateChanged: (CefBrowser, BrowserState) -> Unit,
    ) {
        MCEF.INSTANCE.client.handle.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(browser: CefBrowser) {
                onCreated(browser)
                super.onAfterCreated(browser)
            }
        })
        MCEF.INSTANCE.client.addLoadHandler(loadHandler(onStateChanged))
    }

    private fun loadHandler(onStateChanged: (CefBrowser, BrowserState) -> Unit) =
        object : CefLoadHandlerAdapter() {
            override fun onLoadStart(browser: CefBrowser, frame: CefFrame?, type: CefRequest.TransitionType?) {
                onStateChanged(browser, BrowserState.Loading)
                super.onLoadStart(browser, frame, type)
            }

            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame?, statusCode: Int) {
                onStateChanged(browser, BrowserState.Success(statusCode))
                super.onLoadEnd(browser, frame, statusCode)
            }

            override fun onLoadError(
                browser: CefBrowser,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?,
            ) {
                onStateChanged(
                    browser,
                    BrowserState.Failure(
                        errorCode?.code ?: -1,
                        errorText ?: "Unknown Error",
                        failedUrl ?: "Unknown URL",
                    ),
                )
                super.onLoadError(browser, frame, errorCode, errorText, failedUrl)
            }
        }
}
