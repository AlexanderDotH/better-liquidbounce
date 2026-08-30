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



@file:JvmName("AccountFunctionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import net.ccbluex.liquidbounce.api.core.formatAvatarUrl
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.AccountManagerMessageEvent
import net.ccbluex.liquidbounce.features.account.AccountManager
import net.ccbluex.liquidbounce.features.account.AccountServerAccessRegistry
import net.ccbluex.liquidbounce.utils.client.browseUrl
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.randomUsername

// GET /api/v1/client/accounts
internal fun Route.getAccounts() = get {
    val accounts = JsonArray()
    for ((i, account) in AccountManager.accounts.withIndex()) {
        val profile = account.profile
        val username = account.username

        accounts.add(JsonObject().apply {
            addProperty("id", i)
            addProperty("username", username)
            addProperty("uuid", profile?.id?.toString().orEmpty())
            addProperty("avatar", formatAvatarUrl(profile?.id, username))
            add("bans", interopGson.toJsonTree(account.bans))
            add("workingServers", interopGson.toJsonTree(AccountServerAccessRegistry.list(account)))
            addProperty("type", account.service.tag)
            addProperty("favorite", account.favorite)
        })
    }
    call.respond(accounts)
}

// POST /api/v1/client/accounts/new/microsoft/device-code
internal fun Route.postNewMicrosoftAccount() = post("/device-code") {
    AccountManager.newMicrosoftAccountViaDeviceCode {
        browseUrl(it)
        EventManager.callEvent(AccountManagerMessageEvent("Opened login url in browser"))
    }
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/accounts/new/microsoft/device-code/clipboard
internal fun Route.postClipboardMicrosoftAccount() = post("/device-code/clipboard") {
    AccountManager.newMicrosoftAccountViaDeviceCode {
        mc.execute {
            mc.keyboardHandler.clipboard = it
            EventManager.callEvent(AccountManagerMessageEvent("Copied login url to clipboard"))
        }
    }
    call.respond(HttpStatusCode.NoContent)
}

// Legacy aliases retained for existing themes and external integrations.
internal fun Route.postLegacyNewMicrosoftAccount() = post {
    AccountManager.newMicrosoftAccountViaDeviceCode {
        browseUrl(it)
        EventManager.callEvent(AccountManagerMessageEvent("Opened login url in browser"))
    }
    call.respond(HttpStatusCode.NoContent)
}

internal fun Route.postLegacyClipboardMicrosoftAccount() = post("/clipboard") {
    AccountManager.newMicrosoftAccountViaDeviceCode {
        mc.execute {
            mc.keyboardHandler.clipboard = it
            EventManager.callEvent(AccountManagerMessageEvent("Copied login url to clipboard"))
        }
    }
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/accounts/new/microsoft/webview
internal fun Route.postWebViewMicrosoftAccount() = post("/webview") {
    AccountManager.newMicrosoftAccountViaWebView()
    EventManager.callEvent(AccountManagerMessageEvent("Opened Microsoft sign-in window"))
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/accounts/new/microsoft/credentials
internal fun Route.postCredentialsMicrosoftAccount() = post("/credentials") {
    data class AccountForm(val email: String, val password: String)

    val accountForm = call.receive<AccountForm>()

    AccountManager.newMicrosoftAccountViaCredentials(accountForm.email, accountForm.password)
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/accounts/new/cracked
internal fun Route.postNewCrackedAccount() = post("/cracked") {
    data class AccountForm(val username: String, val online: Boolean?)

    val accountForm = call.receive<AccountForm>()

    AccountManager.newCrackedAccount(accountForm.username, accountForm.online ?: false)
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/accounts/new/session
internal fun Route.postNewSessionAccount() = post("/session") {
    data class AccountForm(val token: String)

    val accountForm = call.receive<AccountForm>()

    AccountManager.newSessionAccount(accountForm.token)
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/accounts/new/altening
internal fun Route.postNewAlteningAccount() = post {
    data class AlteningForm(val token: String)

    val accountForm = call.receive<AlteningForm>()
    AccountManager.newAlteningAccount(accountForm.token)
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/accounts/new/altening/generate
internal fun Route.postGenerateAlteningAccount() = post("/generate") {
    data class AlteningGenForm(val apiToken: String)

    val accountForm = call.receive<AlteningGenForm>()

    val result = AccountManager.generateAlteningAccount(accountForm.apiToken)
    call.respond(interopGson.toJsonTree(result))
}

// POST /api/v1/client/accounts/swap
internal fun Route.postSwapAccounts() = post("/swap") {
    data class AccountForm(val from: Int, val to: Int)

    val accountForm = call.receive<AccountForm>()

    AccountManager.swapAccounts(accountForm.from, accountForm.to)
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/accounts/order
internal fun Route.postOrderAccounts() = post("/order") {
    data class AccountOrderRequest(val order: List<Int>)

    val accountOrderRequest = call.receive<AccountOrderRequest>()

    AccountManager.orderAccounts(accountOrderRequest.order)
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/account/login
internal fun Route.postLoginAccount() = post {
    data class AccountForm(val id: Int)

    val accountForm = call.receive<AccountForm>()

    AccountManager.loginAccount(accountForm.id)
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/account/login/cracked
internal fun Route.postLoginCrackedAccount() = post("/cracked") {
    data class AccountForm(val username: String, val online: Boolean?)

    val accountForm = call.receive<AccountForm>()

    AccountManager.loginCrackedAccount(accountForm.username, accountForm.online ?: false)
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/account/login/session
internal fun Route.postLoginSessionAccount() = post("/session") {
    data class AccountForm(val token: String)

    val accountForm = call.receive<AccountForm>()

    AccountManager.loginSessionAccount(accountForm.token)
    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/account/restore
internal fun Route.postRestoreInitial() = post("/restore") {
    AccountManager.restoreInitial()
    call.respond(mc.user)
}

// PUT /api/v1/client/account/favorite
internal fun Route.putFavoriteAccount() = put {
    data class AccountForm(val id: Int)

    val accountForm = call.receive<AccountForm>()

    AccountManager.favoriteAccount(accountForm.id)
    call.respond(HttpStatusCode.NoContent)
}

// DELETE /api/v1/client/account/favorite
internal fun Route.deleteFavoriteAccount() = delete {
    data class AccountForm(val id: Int)

    val accountForm = call.receive<AccountForm>()

    AccountManager.unfavoriteAccount(accountForm.id)
    call.respond(HttpStatusCode.NoContent)
}
