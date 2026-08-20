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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class MerchantValuesTest {

    @Test
    fun `trade filters round-trip as item identifiers without amount fields`() {
        val original = MerchantTradeFiltersValue(
            "Trades",
            listOf(MerchantTradeRule(setOf(Items.EMERALD), setOf(Items.BOOK), setOf(Items.ENCHANTED_BOOK))),
        )
        val serialized = fileGson.toJsonTree(original).asJsonObject["value"]
        val restored = MerchantTradeFiltersValue("Trades")

        restored.deserializeFrom(fileGson, serialized)

        assertEquals(original.get(), restored.get())
        assertEquals("minecraft:emerald", serialized.asJsonArray[0].asJsonObject["inputA"].asJsonArray[0].asString)
        assertFalse(serialized.toString().contains("count", ignoreCase = true))
        assertFalse(serialized.toString().contains("component", ignoreCase = true))
    }

    @Test
    fun `unknown malformed and duplicate item entries are safely sanitized`() {
        val value = MerchantTradeFiltersValue("Trades")
        val json = JsonParser.parseString(
            """
            [
              {
                "inputA": ["minecraft:emerald", "minecraft:emerald", "missing:not_an_item", 7],
                "inputB": null,
                "outputs": ["minecraft:bread", {}, "bad id"]
              },
              "not-a-rule",
              {"inputA": ["missing:nope"], "inputB": [], "outputs": []}
            ]
            """.trimIndent(),
        )

        value.deserializeFrom(fileGson, json)

        assertEquals(2, value.get().size)
        assertEquals(setOf(Items.EMERALD), value.get()[0].inputA)
        assertEquals(emptySet<net.minecraft.world.item.Item>(), value.get()[0].inputB)
        assertEquals(setOf(Items.BREAD), value.get()[0].outputs)
        assertFalse(value.get()[1].isActive)
    }

    @Test
    fun `non-array trade filter payload fails closed`() {
        val value = MerchantTradeFiltersValue(
            "Trades",
            listOf(MerchantTradeRule(setOf(Items.EMERALD), emptySet(), setOf(Items.BREAD))),
        )

        value.deserializeFrom(fileGson, JsonParser.parseString("{\"inputA\":[]}"))

        assertTrue(value.get().isEmpty())
    }

    @Test
    fun `trade filter slots retain only their first selected item`() {
        val value = MerchantTradeFiltersValue("Trades")

        value.set(
            listOf(
                MerchantTradeRule(
                    inputA = linkedSetOf(Items.EMERALD, Items.DIAMOND),
                    inputB = linkedSetOf(Items.BOOK, Items.PAPER),
                    outputs = linkedSetOf(Items.BREAD, Items.ENCHANTED_BOOK),
                ),
            ),
        )

        assertEquals(setOf(Items.EMERALD), value.get().single().inputA)
        assertEquals(setOf(Items.BOOK), value.get().single().inputB)
        assertEquals(setOf(Items.BREAD), value.get().single().outputs)
    }

    @Test
    fun `trade filter interop advertises the item registry and custom value type`() {
        val group = ValueGroup("Vanilla").apply { value(MerchantTradeFiltersValue("Trades")) }
        val serialized = interopGson.toJsonTree(group).asJsonObject["value"].asJsonArray[0].asJsonObject

        assertEquals("MERCHANT_TRADE_FILTERS", serialized["valueType"].asString)
        assertEquals("item", serialized["registry"].asString)
    }

    @Test
    fun `trade filter interop value can be applied through the config deserializer`() {
        val rule = MerchantTradeRule(setOf(Items.EMERALD), emptySet(), setOf(Items.BREAD))
        val group = ValueGroup("Vanilla").apply { value(MerchantTradeFiltersValue("Trades", listOf(rule))) }
        val serialized = interopGson.toJsonTree(group).asJsonObject["value"].asJsonArray[0].asJsonObject
        val restored = MerchantTradeFiltersValue("Trades")

        restored.deserializeFrom(fileGson, serialized["value"])

        assertEquals(listOf(rule), restored.get())
    }

    @Test
    fun `reach clamps visible and wall distances to safe limits`() {
        assertEquals(MerchantReach.of(6f, 0f), MerchantReach.of(100f, -5f))
        assertEquals(MerchantReach.of(2f, 2f), MerchantReach.of(2f, 5f))
        assertEquals(MerchantReach.DEFAULT, MerchantReach.of(Float.NaN, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `reach value sanitizes deserialization and direct updates`() {
        val value = MerchantReachValue("Reach")

        value.deserializeFrom(fileGson, JsonParser.parseString("{\"range\":2.5,\"wallRange\":5}"))
        value.set(MerchantReach.of(1.5f, 6f))

        assertEquals(MerchantReach.of(1.5f, 1.5f), value.get())
    }

    @Test
    fun `reach round-trips through file Gson`() {
        val original = MerchantReachValue("Reach", MerchantReach.of(5.5f, 2.5f))
        val serialized = fileGson.toJsonTree(original).asJsonObject["value"]
        val restored = MerchantReachValue("Reach")

        restored.deserializeFrom(fileGson, serialized)

        assertEquals(original.get(), restored.get())
    }

    @Test
    fun `reach interop exposes independent slider bounds`() {
        val group = ValueGroup("Vanilla").apply { value(MerchantReachValue("Reach")) }
        val serialized = interopGson.toJsonTree(group).asJsonObject["value"].asJsonArray[0].asJsonObject

        assertEquals("MERCHANT_REACH", serialized["valueType"].asString)
        assertEquals(1f, serialized["rangeBounds"].asJsonObject["from"].asFloat)
        assertEquals(6f, serialized["rangeBounds"].asJsonObject["to"].asFloat)
        assertEquals(0f, serialized["wallRangeBounds"].asJsonObject["from"].asFloat)
        assertEquals(6f, serialized["wallRangeBounds"].asJsonObject["to"].asFloat)
        assertEquals("blocks", serialized["suffix"].asString)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() = MinecraftBootstrap.ensureInitialized()
    }
}
