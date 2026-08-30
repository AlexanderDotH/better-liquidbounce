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

package net.ccbluex.liquidbounce.features.module.modules.player.fastuse

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleFastUse
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.InteractionHand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleFastUseTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `FastUse exposes independent Food Spear and Crossbow switches`() {
        val groups = ModuleFastUse.inner.filterIsInstance<ToggleableValueGroup>()
        val food = groups.single { it.name == "Food" }
        val spear = groups.single { it.name == "Spear" }
        val crossbow = groups.single { it.name == "Crossbow" }
        val foodMode = food.inner.filterIsInstance<ModeValueGroup<*>>().single { it.name == "Mode" }

        assertEquals(listOf("Food", "Spear", "Crossbow"), groups.map { it.name })
        assertTrue(food.enabled)
        assertTrue(spear.enabled)
        assertFalse(crossbow.enabled)
        assertEquals(listOf("Enabled", "Mode", "Conditions", "StopInput", "PacketType"), food.inner.map { it.name })
        assertEquals(listOf("Enabled"), spear.inner.map { it.name })
        assertEquals(listOf("Enabled", "TickCooldown"), crossbow.inner.map { it.name })
        assertEquals(
            mapOf(
                "Immediate" to listOf("Delay", "Timer", "Speed"),
                "ItemUseTime" to listOf("ConsumeTime", "Speed"),
            ),
            foodMode.modes.associate { mode -> mode.name to mode.inner.map { value -> value.name } },
        )
    }

    @Test
    fun `Food keeps consumables while FastUse spear only accepts the spear tag`() {
        // Bread and potion both reach FastUse through their CONSUMABLE component, not Food only.
        assertTrue(shouldAccelerateFastUseFood(true, false, true, isConsumable = true))
        assertFalse(shouldAccelerateFastUseFood(true, false, true, isConsumable = false))

        val spearTagValues = vanillaItemTagValues("data/minecraft/tags/item/spears.json")
        assertTrue(isFastUseSpearTag("minecraft:wooden_spear" in spearTagValues))
        assertFalse(isFastUseSpearTag("minecraft:trident" in spearTagValues))
    }

    @Test
    fun `disabled Food blocks acceleration and input suppression`() {
        assertFalse(
            shouldAccelerateFastUseFood(
                foodRunning = false,
                hasBlockingCondition = false,
                isUsingItem = true,
                isConsumable = true,
            ),
        )
        assertFalse(shouldStopFastUseFoodInput(foodRunning = false, stopInput = true))
    }

    @Test
    fun `FastUse spear visuals yield when SpearKill owns the raised pose`() {
        assertTrue(
            shouldRenderFastUseSpear(
                fastUseRunning = true,
                spearRunning = true,
                isUsingItem = true,
                isUsingSpear = true,
                usedHand = InteractionHand.MAIN_HAND,
                renderedHand = InteractionHand.MAIN_HAND,
                spearKillControlsAnimation = false,
            ),
        )
        assertFalse(
            shouldRenderFastUseSpear(
                fastUseRunning = true,
                spearRunning = true,
                isUsingItem = true,
                isUsingSpear = true,
                usedHand = InteractionHand.MAIN_HAND,
                renderedHand = InteractionHand.MAIN_HAND,
                spearKillControlsAnimation = true,
            ),
        )
        assertFalse(
            shouldRenderFastUseSpear(
                fastUseRunning = true,
                spearRunning = true,
                isUsingItem = true,
                isUsingSpear = true,
                usedHand = InteractionHand.MAIN_HAND,
                renderedHand = InteractionHand.OFF_HAND,
                spearKillControlsAnimation = false,
            ),
        )
    }

    @Test
    fun `SpearKill owns FastUse's server release`() {
        assertFalse(
            shouldReleaseFastUseSpear(
                spearKillRunning = true,
                ticksUsingItem = 4,
                delayTicks = 3,
            ),
        )
        assertFalse(
            shouldReleaseFastUseSpear(
                spearKillRunning = false,
                ticksUsingItem = 3,
                delayTicks = 3,
            ),
        )
        assertTrue(
            shouldReleaseFastUseSpear(
                spearKillRunning = false,
                ticksUsingItem = 4,
                delayTicks = 3,
            ),
        )
    }

    @Test
    fun `FastUse refreshes a held spear before its kinetic window expires`() {
        assertFalse(
            shouldRefreshFastUseSpear(
                isUseKeyDown = true,
                ticksUsingItem = 18,
                damageUseDuration = 20,
            ),
        )
        assertTrue(
            shouldRefreshFastUseSpear(
                isUseKeyDown = true,
                ticksUsingItem = 19,
                damageUseDuration = 20,
            ),
        )
    }

    @Test
    fun `FastUse never refreshes a spear reserved by SpearKill prehold`() {
        assertFalse(
            shouldRefreshFastUseSpear(
                spearKillControlsUse = true,
                isUseKeyDown = true,
                ticksUsingItem = 19,
                damageUseDuration = 20,
            ),
        )
    }

    @Test
    fun `FastUse spear refresh requires a held use key`() {
        assertFalse(
            shouldRefreshFastUseSpear(
                isUseKeyDown = false,
                ticksUsingItem = 19,
                damageUseDuration = 20,
            ),
        )
        assertFalse(
            shouldRefreshFastUseSpear(
                isUseKeyDown = true,
                ticksUsingItem = 0,
                damageUseDuration = 0,
            ),
        )
    }

    @Test
    fun `FastUse spear animation stays at its required delay`() {
        assertEquals(3f, adjustedSpearAnimationTicks(originalTicks = 0f, delayTicks = 3))
        assertEquals(3f, adjustedSpearAnimationTicks(originalTicks = 2f, delayTicks = 3))
        assertEquals(3f, adjustedSpearAnimationTicks(originalTicks = 5f, delayTicks = 3))
    }

    @Test
    fun `legacy Crossbow FastUse config migrates into all three switches exactly once`() {
        val legacy = legacyFastUseConfig(activeMode = "Crossbow")

        ModuleFastUse.prepareDeserialize(legacy)

        val values = legacy.values()
        val food = values.single { it.name() == "Food" }
        val spear = values.single { it.name() == "Spear" }
        val crossbow = values.single { it.name() == "Crossbow" }
        val foodMode = food.value("Mode")
        val foodChoices = foodMode.getAsJsonObject("choices")

        assertEquals(listOf("Food", "Spear", "Crossbow"), values.map { it.name() })
        assertTrue(food.value("Enabled").get("value").asBoolean)
        assertTrue(spear.value("Enabled").get("value").asBoolean)
        assertTrue(crossbow.value("Enabled").get("value").asBoolean)
        assertEquals("Immediate", foodMode.get("active").asString)
        assertEquals(listOf("Immediate", "ItemUseTime"), foodChoices.keySet().toList())
        assertEquals(3, foodChoices.choiceValue("Immediate", "Delay").asInt)
        assertEquals(1.5f, foodChoices.choiceValue("Immediate", "Timer").asFloat)
        assertEquals(17, foodChoices.choiceValue("Immediate", "Speed").asInt)
        assertEquals(12, foodChoices.choiceValue("ItemUseTime", "ConsumeTime").asInt)
        assertEquals(9, foodChoices.choiceValue("ItemUseTime", "Speed").asInt)
        val conditions = food.value("Conditions").getAsJsonArray("value").map { it.asString }
        assertEquals(listOf("NotInTheAir", "NotDuringMove"), conditions)
        assertTrue(food.value("StopInput").get("value").asBoolean)
        assertEquals("FULL", food.value("PacketType").get("value").asString)
        assertEquals(4, crossbow.value("TickCooldown").get("value").asInt)

        val migrated = legacy.deepCopy()
        ModuleFastUse.prepareDeserialize(legacy)

        assertEquals(migrated, legacy)
    }

    @Test
    fun `legacy Food mode remains selected without enabling Crossbow`() {
        val legacy = legacyFastUseConfig(activeMode = "ItemUseTime")

        ModuleFastUse.prepareDeserialize(legacy)

        val values = legacy.values()
        val food = values.single { it.name() == "Food" }
        val spear = values.single { it.name() == "Spear" }
        val crossbow = values.single { it.name() == "Crossbow" }

        assertEquals("ItemUseTime", food.value("Mode").get("active").asString)
        assertTrue(food.value("Enabled").get("value").asBoolean)
        assertTrue(spear.value("Enabled").get("value").asBoolean)
        assertFalse(crossbow.value("Enabled").get("value").asBoolean)
    }

    private fun legacyFastUseConfig(activeMode: String): JsonObject = JsonParser.parseString(
        """
        {
          "name": "FastUse",
          "value": [
            {
              "name": "Mode",
              "active": "$activeMode",
              "value": [],
              "choices": {
                "Immediate": {
                  "name": "Immediate",
                  "value": [
                    { "name": "Delay", "value": 3 },
                    { "name": "Timer", "value": 1.5 },
                    { "name": "Speed", "value": 17 }
                  ]
                },
                "ItemUseTime": {
                  "name": "ItemUseTime",
                  "value": [
                    { "name": "ConsumeTime", "value": 12 },
                    { "name": "Speed", "value": 9 }
                  ]
                },
                "Crossbow": {
                  "name": "Crossbow",
                  "value": [
                    { "name": "TickCooldown", "value": 4 }
                  ]
                }
              }
            },
            { "name": "Conditions", "value": ["NotInTheAir", "NotDuringMove"] },
            { "name": "StopInput", "value": true },
            { "name": "PacketType", "value": "FULL" }
          ]
        }
        """.trimIndent(),
    ).asJsonObject

    private fun JsonObject.values(): List<JsonObject> = getAsJsonArray("value").map { it.asJsonObject }

    private fun JsonObject.name(): String = get("name").asString

    private fun JsonObject.value(name: String): JsonObject = values().single { it.name() == name }

    private fun JsonObject.choiceValue(choice: String, value: String) = getAsJsonObject(choice)
        .getAsJsonArray("value")
        .map { it.asJsonObject }
        .single { it.name() == value }
        .get("value")

    private fun vanillaItemTagValues(path: String): Set<String> = checkNotNull(
        javaClass.classLoader.getResourceAsStream(path),
    ).bufferedReader().use { reader ->
        JsonParser.parseReader(reader).asJsonObject.getAsJsonArray("values")
            .map { it.asString }
            .toSet()
    }
}
