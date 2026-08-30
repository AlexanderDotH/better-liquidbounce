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

import com.google.gson.JsonObject
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
import net.ccbluex.liquidbounce.features.account.AccountManager
import net.ccbluex.liquidbounce.utils.client.browseUrl
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.randomUsername

// DELETE /api/v1/client/account
internal fun Route.deleteAccount() = delete {
    data class AccountForm(val id: Int)

    val accountForm = call.receive<AccountForm>()
    val account = AccountManager.removeAccount(accountForm.id)

    call.respond(JsonObject().apply {
        addProperty("id", accountForm.id)

        val profile = account.profile
        addProperty("username", account.username)
        addProperty("uuid", profile?.id?.toString().orEmpty())
        addProperty("avatar", formatAvatarUrl(profile?.id, account.username))

        addProperty("type", account.service.tag)
    })
}

// POST /api/v1/client/account/random-name
internal fun Route.generateName() = post("/random-name") {
    call.respond(JsonObject().apply {
        addProperty("name", randomUsername())
    })
}

internal fun Route.accountRoutes() {
    route("/accounts") {
        getAccounts()
        route("/new") {
            route("/microsoft") {
                postLegacyNewMicrosoftAccount()
                postLegacyClipboardMicrosoftAccount()
                postNewMicrosoftAccount()
                postClipboardMicrosoftAccount()
                postWebViewMicrosoftAccount()
                postCredentialsMicrosoftAccount()
            }
            postNewCrackedAccount()
            postNewSessionAccount()
            route("/altening") {
                postNewAlteningAccount()
                postGenerateAlteningAccount()
            }
        }
        postSwapAccounts()
        postOrderAccounts()
    }
    route("/account") {
        deleteAccount()
        route("/login") {
            postLoginAccount()
            postLoginCrackedAccount()
            postLoginSessionAccount()
        }
        postRestoreInitial()
        route("/favorite") {
            putFavoriteAccount()
            deleteFavoriteAccount()
        }
        generateName()
    }
}
