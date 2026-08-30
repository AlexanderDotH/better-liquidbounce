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

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class AccountRouteCompatibilityTest {

    @Test
    fun `account routes retain legacy aliases and every upstream Microsoft login flow`() {
        val source = readSources(ACCOUNT_ROUTE_OPERATIONS, ACCOUNT_ROUTE_REGISTRATION)

        assertTrue(source.contains("postLegacyNewMicrosoftAccount()"))
        assertTrue(source.contains("postLegacyClipboardMicrosoftAccount()"))
        assertTrue(source.contains("post(\"/device-code\")"))
        assertTrue(source.contains("post(\"/device-code/clipboard\")"))
        assertTrue(source.contains("post(\"/webview\")"))
        assertTrue(source.contains("post(\"/credentials\")"))
        assertTrue(source.contains("call.respond(interopGson.toJsonTree(result))"))
    }

    @Test
    fun `Microsoft worker guards are released from finally blocks`() {
        val source = readSources(ACCOUNT_CREATION_OPERATIONS)
        val webViewWorker = source.substringAfter("thread(name = \"microsoft-account-webview\"")
            .substringBefore("fun newMicrosoftAccountViaCredentials")
        val credentialsWorker = source.substringAfter("thread(name = \"microsoft-account-credentials\"")
            .substringBefore("private fun handleNewMicrosoftAccount")

        for (worker in listOf(webViewWorker, credentialsWorker)) {
            val finallyBlock = worker.substringAfter("finally {").substringBefore('}')
            assertTrue(finallyBlock.contains("microsoftLoginInProgress.set(false)"))
        }
    }

    private fun readSources(vararg paths: String): String = paths.joinToString("\n") { path ->
        Files.readString(Path.of(path))
    }

    private companion object {
        const val ACCOUNT_ROUTE_OPERATIONS =
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/interop/protocol/rest/v1/client/GetAccounts.kt"
        const val ACCOUNT_ROUTE_REGISTRATION =
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/interop/protocol/rest/v1/client/DeleteAccount.kt"
        const val ACCOUNT_CREATION_OPERATIONS =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/account/AccountCreationOperations.kt"
    }

}
