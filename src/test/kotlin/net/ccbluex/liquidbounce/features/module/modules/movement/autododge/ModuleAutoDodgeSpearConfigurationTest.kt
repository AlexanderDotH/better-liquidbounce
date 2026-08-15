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

package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleAutoDodgeSpearConfigurationTest {

    companion object {
        private val SPEAR_VALUE_NAMES = listOf(
            "Enabled",
            "AimMargin",
            "VisibilityGrace",
            "JukeTicks",
            "ThreatMemory",
            "Teleport",
            "Shield",
        )
        private val SPEAR_SETTING_NAMES = setOf(
            "AimMargin",
            "VisibilityGrace",
            "JukeTicks",
            "ThreatMemory",
            "Teleport",
            "BehindDistance",
            "MaxDistance",
            "SearchRadius",
            "Cooldown",
            "StepDistance",
            "MaxPackets",
            "Shield",
            "ReleaseDelay",
        )

        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `Spear defense exposes stable nested defaults`() {
        val spear = ModuleAutoDodge.toggleableGroup("Spear").also(ValueGroup::restore)
        val teleport = spear.toggleableGroup("Teleport")
        val shield = spear.toggleableGroup("Shield")
        val constraints = shield.group("Constraints")

        assertFalse(spear.enabled)
        assertTrue(shield.enabled)
        assertEquals(SPEAR_VALUE_NAMES, spear.inner.map { it.name })
        assertEquals(
            listOf(
                "Enabled",
                "BehindDistance",
                "MaxDistance",
                "SearchRadius",
                "Cooldown",
                "StepDistance",
                "MaxPackets",
            ),
            teleport.inner.map { it.name },
        )
        assertEquals(listOf("Enabled", "ReleaseDelay", "Constraints"), shield.inner.map { it.name })
        assertEquals(
            listOf("StartDelay", "ClickDelay", "CloseDelay", "MissChance", "Requires"),
            constraints.inner.map { it.name },
        )

        assertEquals(0.75F, spear.setting("AimMargin").get())
        assertEquals(0.0F..3.0F, spear.rangedSetting("AimMargin").range)
        assertEquals(8, spear.setting("VisibilityGrace").get())
        assertEquals(0..40, spear.rangedSetting("VisibilityGrace").range)
        assertEquals(2..5, spear.setting("JukeTicks").get())
        assertEquals(1..10, spear.rangedSetting("JukeTicks").range)
        assertEquals(5, spear.setting("ThreatMemory").get())
        assertEquals(0..20, spear.rangedSetting("ThreatMemory").range)
        assertFalse(teleport.enabled)
        assertEquals(2.0F, teleport.setting("BehindDistance").get())
        assertEquals(0.5F..5.0F, teleport.rangedSetting("BehindDistance").range)
        assertEquals(12.0F, teleport.setting("MaxDistance").get())
        assertEquals(2.0F..32.0F, teleport.rangedSetting("MaxDistance").range)
        assertEquals(2, teleport.setting("SearchRadius").get())
        assertEquals(0..5, teleport.rangedSetting("SearchRadius").range)
        assertEquals(6, teleport.setting("Cooldown").get())
        assertEquals(0..40, teleport.rangedSetting("Cooldown").range)
        assertEquals(4.0F, teleport.setting("StepDistance").get())
        assertEquals(0.25F..10.0F, teleport.rangedSetting("StepDistance").range)
        assertEquals(8, teleport.setting("MaxPackets").get())
        assertEquals(1..32, teleport.rangedSetting("MaxPackets").range)
        assertEquals(3, shield.setting("ReleaseDelay").get())
        assertEquals(0..20, shield.rangedSetting("ReleaseDelay").range)
        assertEquals(0..0, constraints.setting("StartDelay").get())
        assertEquals(0..0, constraints.setting("ClickDelay").get())
        assertEquals(0..0, constraints.setting("CloseDelay").get())
        assertEquals(0..0, constraints.setting("MissChance").get())
        assertTrue((constraints.setting("Requires").get() as Set<*>).isEmpty())
    }

    @Test
    fun `Spear defense serializes only below its new group`() {
        val serializedModule = fileGson
            .toJsonTree(ModuleAutoDodge, ValueGroup::class.java)
            .asJsonObject
        val rootValues = serializedModule.values()
        val spear = rootValues.single { it["name"].asString == "Spear" }
        val teleport = spear.values().single { it["name"].asString == "Teleport" }
        val shield = spear.values().single { it["name"].asString == "Shield" }

        assertEquals(SPEAR_VALUE_NAMES, spear.valueNames())
        assertEquals(
            listOf(
                "Enabled",
                "BehindDistance",
                "MaxDistance",
                "SearchRadius",
                "Cooldown",
                "StepDistance",
                "MaxPackets",
            ),
            teleport.valueNames(),
        )
        assertEquals(listOf("Enabled", "ReleaseDelay", "Constraints"), shield.valueNames())
        assertTrue(rootValues.none { it["name"].asString in SPEAR_SETTING_NAMES })
    }

    @Test
    fun `configs created before Spear defense require no migration`() {
        val legacy = JsonObject().apply {
            addProperty("name", "AutoDodge")
            add("value", JsonArray().apply {
                add(storedValue("Ignore", JsonArray()))
            })
        }
        val original = legacy.deepCopy()

        ModuleAutoDodge.prepareDeserialize(legacy)

        assertEquals(original, legacy)
        assertFalse(ModuleAutoDodge.toggleableGroup("Spear").enabled)
        assertTrue(ModuleAutoDodge.toggleableGroup("Spear").aliases.isEmpty())
    }

    @Test
    fun `Spear settings expose their exact localization keys`() {
        ModuleAutoDodge.walkKeyPath()

        val spear = ModuleAutoDodge.toggleableGroup("Spear")
        val teleport = spear.toggleableGroup("Teleport")
        val shield = spear.toggleableGroup("Shield")
        val constraints = shield.group("Constraints")

        assertEquals("liquidbounce.module.autoDodge.spear.description", spear.descriptionKey)
        assertEquals(
            "liquidbounce.module.autoDodge.spear.aimMargin.description",
            spear.setting("AimMargin").descriptionKey,
        )
        assertEquals(
            "liquidbounce.module.autoDodge.spear.visibilityGrace.description",
            spear.setting("VisibilityGrace").descriptionKey,
        )
        assertEquals(
            "liquidbounce.module.autoDodge.spear.jukeTicks.description",
            spear.setting("JukeTicks").descriptionKey,
        )
        assertEquals(
            "liquidbounce.module.autoDodge.spear.threatMemory.description",
            spear.setting("ThreatMemory").descriptionKey,
        )
        assertEquals("liquidbounce.module.autoDodge.spear.teleport.description", teleport.descriptionKey)
        assertEquals(
            listOf(
                "liquidbounce.module.autoDodge.spear.teleport.enabled.description",
                "liquidbounce.module.autoDodge.spear.teleport.behindDistance.description",
                "liquidbounce.module.autoDodge.spear.teleport.maxDistance.description",
                "liquidbounce.module.autoDodge.spear.teleport.searchRadius.description",
                "liquidbounce.module.autoDodge.spear.teleport.cooldown.description",
                "liquidbounce.module.autoDodge.spear.teleport.stepDistance.description",
                "liquidbounce.module.autoDodge.spear.teleport.maxPackets.description",
            ),
            teleport.inner.map { it.descriptionKey },
        )
        assertEquals("liquidbounce.module.autoDodge.spear.shield.description", shield.descriptionKey)
        assertEquals(
            "liquidbounce.module.autoDodge.spear.shield.releaseDelay.description",
            shield.setting("ReleaseDelay").descriptionKey,
        )
        assertEquals(
            "liquidbounce.module.autoDodge.spear.shield.constraints.description",
            constraints.descriptionKey,
        )
        assertEquals(
            listOf(
                "liquidbounce.module.autoDodge.spear.shield.constraints.startDelay.description",
                "liquidbounce.module.autoDodge.spear.shield.constraints.clickDelay.description",
                "liquidbounce.module.autoDodge.spear.shield.constraints.closeDelay.description",
                "liquidbounce.module.autoDodge.spear.shield.constraints.missChance.description",
                "liquidbounce.module.autoDodge.spear.shield.constraints.requires.description",
            ),
            constraints.inner.map { it.descriptionKey },
        )
    }

}

private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name }

private fun ValueGroup.rangedSetting(name: String): RangedValue<*> = setting(name) as RangedValue<*>

private fun ValueGroup.group(name: String): ValueGroup = inner.single { it.name == name } as ValueGroup

private fun ValueGroup.toggleableGroup(name: String): ToggleableValueGroup =
    inner.single { it.name == name } as ToggleableValueGroup

private fun JsonObject.values(): List<JsonObject> = getAsJsonArray("value").map { it.asJsonObject }

private fun JsonObject.valueNames(): List<String> = values().map { it["name"].asString }

private fun storedValue(name: String, value: JsonArray) = JsonObject().apply {
    addProperty("name", name)
    add("value", value)
}
