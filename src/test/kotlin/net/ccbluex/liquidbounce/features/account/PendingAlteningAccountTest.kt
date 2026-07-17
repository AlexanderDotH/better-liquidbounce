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

import com.google.gson.GsonBuilder
import net.ccbluex.liquidbounce.api.thirdparty.TheAlteningGeneratedAccount
import net.ccbluex.liquidbounce.authlib.account.AlteningAccount
import net.ccbluex.liquidbounce.authlib.account.MinecraftAccount
import net.ccbluex.liquidbounce.config.gson.adapter.MinecraftAccountAdapter
import net.ccbluex.liquidbounce.test.assertIs
import net.ccbluex.liquidbounce.test.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PendingAlteningAccountTest {

    private val gson = GsonBuilder()
        .registerTypeHierarchyAdapter(MinecraftAccount::class.java, MinecraftAccountAdapter)
        .create()

    @Test
    fun `generated token creates unresolved account without authentication`() {
        val account = createPendingAlteningAccount(generatedAccount())
        val profile = assertNotNull(account.profile)

        assertEquals("generated-token", account.accountToken)
        assertEquals("Example**", profile.username)
        assertNull(profile.uuid)
    }

    @Test
    fun `pending account survives persistence round trip`() {
        val account = createPendingAlteningAccount(generatedAccount()).apply {
            favorite()
        }

        val json = gson.toJson(account, MinecraftAccount::class.java)
        val restored = assertIs<AlteningAccount>(gson.fromJson(json, MinecraftAccount::class.java))
        val profile = assertNotNull(restored.profile)

        assertTrue(restored.favorite)
        assertEquals("generated-token", restored.accountToken)
        assertEquals("Example**", profile.username)
        assertNull(profile.uuid)
    }

    @Test
    fun `resolved account remains compatible with persistence format`() {
        val existingJson = """
            {
              "type": "AlteningAccount",
              "name": "ExistingAlt",
              "uuid": "00000000-0000-0000-0000-000000000001",
              "token": "access-token",
              "accountToken": "generator-token",
              "hypixelLevel": 0,
              "hypixelRank": "",
              "favorite": true,
              "bans": {}
            }
        """.trimIndent()
        val account = assertIs<AlteningAccount>(gson.fromJson(existingJson, MinecraftAccount::class.java))

        val persistedJson = gson.toJson(account, MinecraftAccount::class.java)
        val restored = assertIs<AlteningAccount>(gson.fromJson(persistedJson, MinecraftAccount::class.java))
        val profile = assertNotNull(restored.profile)

        assertTrue(restored.favorite)
        assertEquals("generator-token", restored.accountToken)
        assertEquals("access-token", restored.accessToken)
        assertEquals("ExistingAlt", profile.username)
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), profile.uuid)
    }

    private fun generatedAccount() = TheAlteningGeneratedAccount(
        token = "generated-token",
        username = "Example**"
    )

}
