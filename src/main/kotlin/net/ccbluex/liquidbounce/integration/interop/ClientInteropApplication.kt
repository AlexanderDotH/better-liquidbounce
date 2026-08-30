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
package net.ccbluex.liquidbounce.integration.interop

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import net.ccbluex.liquidbounce.common.ClientBuildMetadata
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.features.marketplace.MarketplaceManager
import net.ccbluex.liquidbounce.integration.interop.middleware.AuthPlugin
import net.ccbluex.liquidbounce.integration.interop.middleware.isWebSocketAuthenticated
import net.ccbluex.liquidbounce.integration.interop.protocol.event.WebSocketSessionManager
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.registerInteropFunctions
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.respondJsonWriter
import net.ccbluex.liquidbounce.integration.theme.Theme
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.utils.client.logger
import kotlin.time.Duration.Companion.seconds

internal fun Application.configureClientInterop(authCode: String) {
    install(StatusPages) {
        exception<HttpStatusException> { call, cause -> call.respond(cause.status, cause.body) }
    }
    installGson(interopGson)
    installCors()
    install(Compression) { default() }
    install(AuthPlugin) { this.authCode = authCode }
    install(WebSockets) { pingPeriod = 15.seconds }
    routing { registerClientInteropRoutes(authCode) }
}

private fun Route.registerClientInteropRoutes(authCode: String) {
    webSocket("/") {
        val authenticated = isWebSocketAuthenticated(this, authCode) || ThemeManager.isThemeExternal
        if (!authenticated) {
            logger.warn("[Interop] Unauthenticated web socket upgrade request")
            return@webSocket
        }

        WebSocketSessionManager.add(this)
        try {
            closeReason.await()
        } finally {
            WebSocketSessionManager.remove(this)
        }
    }

    rootResponse()
    registerInteropFunctions()
    staticFiles("/local", ThemeManager.themesFolder)
    staticFiles("/marketplace", MarketplaceManager.marketplaceRoot)
    singlePageApplication {
        applicationRoute = "/${Theme.Origin.RESOURCE.tag}/${ClientBuildMetadata.NAME.lowercase()}"
        filesPath = "resources/liquidbounce/themes/${ClientBuildMetadata.NAME.lowercase()}"
        useResources = true
    }
}

private fun Route.rootResponse() = get("/") {
    call.respondJsonWriter {
        beginObject()
        name("name").value(ClientBuildMetadata.NAME)
        name("version").value(ClientBuildMetadata.version)
        name("author").value(ClientBuildMetadata.AUTHOR)
        endObject()
    }
}
