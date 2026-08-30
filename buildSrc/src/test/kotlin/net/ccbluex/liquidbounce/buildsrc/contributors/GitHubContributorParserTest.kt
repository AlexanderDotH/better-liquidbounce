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

package net.ccbluex.liquidbounce.buildsrc.contributors

import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubContributorParserTest {
    @Test
    fun `last page comes from GitHub link header and defaults to one`() {
        val link = "<https://api.github.com/repositories/1/contributors?per_page=100&page=2>; rel=\"next\", " +
            "<https://api.github.com/repositories/1/contributors?per_page=100&page=7>; rel=\"last\""

        assertEquals(7, GitHubContributorParser.lastPage(link))
        assertEquals(1, GitHubContributorParser.lastPage(""))
    }

    @Test
    fun `only GitHub user logins become contributors`() {
        val json = """
            [
              {"login": "alice", "type": "User"},
              {"login": "dependabot[bot]", "type": "Bot"},
              {"login": 42, "type": "User"},
              {"type": "User"}
            ]
        """.trimIndent()

        assertEquals(listOf("alice"), GitHubContributorParser.userLogins(json.byteInputStream()))
    }
}
