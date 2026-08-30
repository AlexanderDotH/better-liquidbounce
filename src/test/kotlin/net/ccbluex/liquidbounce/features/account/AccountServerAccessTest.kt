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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccountServerAccessTest {

    private val gson = GsonBuilder()
        .registerTypeHierarchyAdapter(MinecraftAccount::class.java, MinecraftAccountGsonAdapter)
        .create()

    @Test
    fun `working server survives account persistence round trip`() {
        val account = CrackedAccount("WorkingAccount").apply { refresh() }
        AccountServerAccessRegistry.markWorking(account, "play.example.net")

        val json = gson.toJson(account, MinecraftAccount::class.java)
        val restored = gson.fromJson(json, MinecraftAccount::class.java)

        assertEquals(listOf("play.example.net"), AccountServerAccessRegistry.list(restored))
    }

    @Test
    fun `unavailable server removes a matching working state`() {
        val account = CrackedAccount("BannedAccount")
        AccountServerAccessRegistry.markWorking(account, "PLAY.EXAMPLE.NET.")

        assertTrue(AccountServerAccessRegistry.markUnavailable(account, "example.net"))
        assertFalse(AccountServerAccessRegistry.markUnavailable(account, "example.net"))
        assertTrue(AccountServerAccessRegistry.list(account).isEmpty())
    }

}
