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
package net.ccbluex.liquidbounce.features.baritone.adapter

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BaritoneSettingMapperTest {

    @Test
    fun `maps primitive enum and list settings without losing canonical strings`() {
        val boolean = native(type = "Boolean", value = "false", default = "true").toCoreSetting()
        val decimal = native(type = "Double", value = "1.25", default = "2.5").toCoreSetting()
        val enum = native(
            type = "Rotation",
            value = "CLOCKWISE_90",
            default = "NONE",
            options = listOf("NONE", "CLOCKWISE_90"),
        ).toCoreSetting()
        val list = native(
            type = "List<Block>",
            value = "minecraft:stone,minecraft:dirt",
            default = "",
        ).toCoreSetting()

        assertEquals(BaritoneSettingValue.BooleanValue(false), boolean.value)
        assertEquals(BaritoneSettingValue.DecimalValue(1.25), decimal.value)
        assertEquals(BaritoneSettingType.ENUM, enum.type)
        assertEquals(listOf("NONE", "CLOCKWISE_90"), enum.options)
        assertEquals(
            BaritoneSettingValue.StringListValue(listOf("minecraft:stone", "minecraft:dirt")),
            list.value,
        )
        assertEquals(BaritoneSettingValue.StringListValue(emptyList()), list.defaultValue)
        assertTrue(boolean.mutable)
    }

    @Test
    fun `serializes typed facade values back to upstream syntax`() {
        assertEquals("true", BaritoneSettingValue.BooleanValue(true).toUpstreamString())
        assertEquals("42", BaritoneSettingValue.IntegerValue(42).toUpstreamString())
        assertEquals("1.5", BaritoneSettingValue.DecimalValue(1.5).toUpstreamString())
        assertEquals(
            "minecraft:stone,minecraft:dirt",
            BaritoneSettingValue.StringListValue(listOf("minecraft:stone", "minecraft:dirt")).toUpstreamString(),
        )
        assertIs<BaritoneSettingValue.TextValue>(native(type = "Map<Block,List<Block>>").toCoreSetting().value)
    }

    private fun native(
        type: String,
        value: String = "value",
        default: String = value,
        options: List<String> = emptyList(),
    ) = NativeBaritoneSetting("example", type, value, default, locked = false, options)
}
