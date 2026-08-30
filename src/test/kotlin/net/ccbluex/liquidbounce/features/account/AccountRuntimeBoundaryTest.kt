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

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountRuntimeBoundaryTest {

    @Test
    fun `account manager reaches browser screens and realms mixins only through its runtime port`() {
        val manager = read(ACCOUNT_MANAGER)
        val creationOperations = read(ACCOUNT_CREATION_OPERATIONS)
        val accountImports = listOf(manager, creationOperations)
            .flatMap { source -> source.lineSequence().filter { it.startsWith("import ") }.toList() }
        val adapter = read(ACCOUNT_RUNTIME_ADAPTER)

        assertFalse(accountImports.any { it.contains(".injection.mixins.realms.") })
        assertFalse(accountImports.any { it.contains(".integration.backend.") })
        assertFalse(accountImports.any { it.contains(".integration.screen.impl.") })
        assertTrue(manager.contains("AccountRuntimeBridge.invalidateRealmsSessionCaches()"))
        assertTrue(creationOperations.contains("AccountRuntimeBridge::openMicrosoftWebView"))
        assertTrue(adapter.contains("MixinRealmsAvailabilityAccessor.setFuture(null)"))
        assertTrue(adapter.contains("MixinRealmsClientAccessor.setRealmsClientInstance(null)"))
        assertTrue(adapter.contains("MicrosoftLoginScreen(url, service, mc.gui.screen())"))
    }

    @Test
    fun `account packet trackers own their server endpoint without depending on another feature`() {
        val trackerSources = listOf(ACCOUNT_BAN_TRACKER, ACCOUNT_SERVER_ACCESS_TRACKER).map(::read)

        assertFalse(trackerSources.any { it.contains("import net.ccbluex.liquidbounce.features.server.") })
        assertTrue(read(ACCOUNT_SERVER_ENDPOINT).contains("handler<ServerConnectEvent>"))
    }

    @Test
    fun `account manager retains its public operation surface`() {
        val manager = read(ACCOUNT_MANAGER)
        val operationsSource = listOf(
            manager,
            read(ACCOUNT_CREATION_OPERATIONS),
            read(ACCOUNT_COLLECTION_OPERATIONS),
        ).joinToString("\n")
        val operations = listOf(
            "loginAccount", "loginDirectAccount", "newCrackedAccount", "loginCrackedAccount",
            "loginSessionAccount", "newMicrosoftAccountViaDeviceCode", "newMicrosoftAccountViaWebView",
            "newMicrosoftAccountViaCredentials", "newAlteningAccount", "generateAlteningAccount",
            "restoreInitial", "favoriteAccount", "unfavoriteAccount", "swapAccounts", "orderAccounts",
            "removeAccount", "newSessionAccount",
        )

        assertEquals(
            operations,
            operations.filter { operation -> "fun $operation(" in operationsSource },
        )
        assertTrue(manager.contains("AccountCreationOperations by AccountCreationDelegate"))
        assertTrue(manager.contains("AccountCollectionOperations by AccountCollectionDelegate"))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val ACCOUNT_MANAGER =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/account/AccountManager.kt"
        const val ACCOUNT_CREATION_OPERATIONS =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/account/AccountCreationOperations.kt"
        const val ACCOUNT_COLLECTION_OPERATIONS =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/account/AccountCollectionOperations.kt"
        const val ACCOUNT_BAN_TRACKER =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/account/AccountBanTracker.kt"
        const val ACCOUNT_SERVER_ACCESS_TRACKER =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/account/AccountServerAccessTracker.kt"
        const val ACCOUNT_SERVER_ENDPOINT =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/account/AccountServerEndpoint.kt"
        const val ACCOUNT_RUNTIME_ADAPTER =
            "src/main/kotlin/net/ccbluex/liquidbounce/bootstrap/liquidbounce/AccountRuntimeAdapter.kt"
    }
}
