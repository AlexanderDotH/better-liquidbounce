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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop

import com.google.gson.Gson
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.AutoShopVanillaMode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantReach
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantReachValue
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantTradeFiltersValue
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantTradeRule
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.item.Items
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AutoShopConfigRoundTripTest {

    private lateinit var originalMode: String
    private lateinit var originalRules: List<MerchantTradeRule>
    private lateinit var originalReach: MerchantReach
    private lateinit var originalCps: IntRange

    @BeforeEach
    fun captureOriginalConfig() {
        MinecraftBootstrap.ensureInitialized()
        originalMode = ModuleAutoShop.modes.activeMode.name
        originalRules = tradeFilters().get()
        originalReach = reach().get()
        originalCps = cps().get()
    }

    @AfterEach
    fun restoreOriginalConfig() {
        if (::originalMode.isInitialized) {
            tradeFilters().set(originalRules)
            reach().set(originalReach)
            cps().set(originalCps)
            ModuleAutoShop.modes.setByString(originalMode)
        }
    }

    @Test
    fun `inactive Vanilla settings survive file and REST style round trips`() {
        val expectedRules = listOf(
            MerchantTradeRule(
                inputA = linkedSetOf(Items.EMERALD),
                inputB = emptySet(),
                outputs = linkedSetOf(Items.BREAD),
            ),
        )
        val expectedReach = MerchantReach.of(range = 5.5f, wallRange = 2.25f)
        val expectedCps = 7..11

        tradeFilters().set(expectedRules)
        reach().set(expectedReach)
        cps().set(expectedCps)
        ModuleAutoShop.modes.setByString("ServerShop")

        val fileSnapshot = serializeModule(fileGson)
        val restSnapshot = serializeModule(interopGson)

        replaceWithDifferentVanillaState()
        restoreModeSnapshot(fileSnapshot)
        assertRestored(expectedRules, expectedReach, expectedCps)

        replaceWithDifferentVanillaState()
        restoreModeSnapshot(restSnapshot)
        assertRestored(expectedRules, expectedReach, expectedCps)
    }

    private fun serializeModule(gson: Gson): JsonObject =
        gson.toJsonTree(ModuleAutoShop, ValueGroup::class.javaObjectType).asJsonObject

    /**
     * Applies the same persisted Mode shape used by file loading and the REST setting endpoint
     * without initializing ConfigSystem's live Minecraft filesystem in this unit test.
     */
    private fun restoreModeSnapshot(snapshot: JsonObject) {
        val modeSnapshot = snapshot["value"].asJsonArray
            .map { it.asJsonObject }
            .single { it["name"].asString == "Mode" }
        ModuleAutoShop.modes.setByString(modeSnapshot["active"].asString)

        val vanillaSnapshot = modeSnapshot["choices"].asJsonObject["Vanilla"].asJsonObject
        val vanillaValues = vanillaSnapshot["value"].asJsonArray
            .map { it.asJsonObject }
            .associateBy { it["name"].asString }

        listOf(tradeFilters(), reach(), cps()).forEach { value ->
            value.deserializeFrom(fileGson, vanillaValues.getValue(value.name)["value"])
        }
    }

    private fun replaceWithDifferentVanillaState() {
        ModuleAutoShop.modes.setByString("Vanilla")
        tradeFilters().set(emptyList())
        reach().set(MerchantReach.DEFAULT)
        cps().set(1..1)
    }

    private fun assertRestored(
        rules: List<MerchantTradeRule>,
        merchantReach: MerchantReach,
        clicksPerSecond: IntRange,
    ) {
        assertEquals("ServerShop", ModuleAutoShop.modes.activeMode.name)
        assertEquals(rules, tradeFilters().get())
        assertEquals(merchantReach, reach().get())
        assertEquals(clicksPerSecond, cps().get())
    }

    private fun tradeFilters() = AutoShopVanillaMode.inner
        .single { it.name == "Trades" } as MerchantTradeFiltersValue

    private fun reach() = AutoShopVanillaMode.inner
        .single { it.name == "Reach" } as MerchantReachValue

    @Suppress("UNCHECKED_CAST")
    private fun cps() = AutoShopVanillaMode.inner
        .single { it.name == "CPS" } as Value<IntRange>
}
