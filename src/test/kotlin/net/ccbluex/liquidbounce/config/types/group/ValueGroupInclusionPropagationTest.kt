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
package net.ccbluex.liquidbounce.config.types.group

import net.ccbluex.liquidbounce.config.OptionalInclusion
import net.ccbluex.liquidbounce.config.autoconfig.IncludeConfiguration
import net.ccbluex.liquidbounce.features.autoconfig.AutoConfig
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ValueGroupInclusionPropagationTest {

    @BeforeEach
    fun bootstrap() = MinecraftBootstrap.ensureInitialized()

    @Test
    fun `fork action and player values inherit an existing inclusion group`() {
        val group = ValueGroup("Settings").inclusionGroup(OptionalInclusion.RENDER)
        val action = group.action("Action") {}
        val player = group.player("Player", "")

        assertSame(OptionalInclusion.RENDER, action.inclusionGroup)
        assertSame(OptionalInclusion.RENDER, player.inclusionGroup)
    }

    @Test
    fun `public serialization includes fork values only when their group is selected`() {
        val group = ValueGroup("Settings").inclusionGroup(OptionalInclusion.RENDER)
        group.action("Action") {}
        group.player("Player", "Alex")

        try {
            AutoConfig.includeConfiguration = IncludeConfiguration.DEFAULT
            assertTrue(publicGson.toJsonTree(group).asJsonObject.getAsJsonArray("value").isEmpty)

            AutoConfig.includeConfiguration = IncludeConfiguration(
                optionalInclusions = setOf(OptionalInclusion.RENDER),
            )
            val names = publicGson.toJsonTree(group).asJsonObject.getAsJsonArray("value")
                .map { it.asJsonObject["name"].asString }
            assertEquals(listOf("Action", "Player"), names)
        } finally {
            AutoConfig.includeConfiguration = IncludeConfiguration.DEFAULT
        }
    }

}
