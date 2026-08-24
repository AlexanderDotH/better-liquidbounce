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
package net.ccbluex.liquidbounce.features.module.modules.render.customambience

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ModuleCustomAmbienceFogTest {

    @BeforeEach
    fun bootstrapAndRestoreDefaults() {
        MinecraftBootstrap.ensureInitialized()
        ModuleCustomAmbience.restore()
    }

    @AfterEach
    fun restoreDefaults() {
        ModuleCustomAmbience.restore()
    }

    @Test
    fun `fog schema preserves existing order around density blur and volume additions`() {
        assertEquals(
            listOf(
                "Enabled",
                "Engine",
                "Horizon",
                "SilhouetteFeather",
                "FogDensity",
                "FogColorOverride",
                "BlurFog",
                "VolumetricFog",
                "BackgroundColor",
                "Environmental",
                "RenderDistance",
                "SkyEnd",
                "CloudEnd",
            ),
            ModuleCustomAmbience.FogValueGroup.inner.map { it.name },
        )
    }

    @Test
    fun `unified engine is opt in and its settings keep the planned defaults`() {
        val fog = ModuleCustomAmbience.FogValueGroup
        val horizon = rangedSetting<ClosedFloatingPointRange<Float>>(fog, "Horizon")
        val silhouetteFeather = rangedSetting<Float>(fog, "SilhouetteFeather")

        assertEquals(FogEngine.LEGACY, fog.engine.get())
        assertEquals(70f..100f, horizon.get())
        assertEquals(0f..100f, horizon.range)
        assertEquals("%", horizon.suffix)
        assertEquals(12f, silhouetteFeather.get())
        assertEquals(0f..32f, silhouetteFeather.range)
        assertEquals("px", silhouetteFeather.suffix)
    }

    @Test
    fun `legacy config without Engine restores Legacy and switching engines preserves raw fog values`() {
        val fog = ModuleCustomAmbience.FogValueGroup
        val legacyConfig = ConfigSystem.serializeValueGroup(ModuleCustomAmbience)
        val fogConfig = legacyConfig.getAsJsonArray("value")
            .map { it.asJsonObject }
            .single { it["name"].asString == "Fog" }
        fogConfig.getAsJsonArray("value").removeAll { value ->
            value.asJsonObject["name"].asString == "Engine"
        }

        fog.engine.setByString("Unified")
        ConfigSystem.deserializeValueGroup(ModuleCustomAmbience, legacyConfig)
        assertEquals(FogEngine.LEGACY, fog.engine.get())

        val legacyRanges = serializedLegacyRanges()
        fog.engine.setByString("Unified")
        assertEquals(legacyRanges, serializedLegacyRanges())
    }

    @Test
    fun `fog density defaults to zero percent across its full percentage range`() {
        val density = rangedSetting<Int>(ModuleCustomAmbience.FogValueGroup, "FogDensity")

        assertEquals(0, density.get())
        assertEquals(0..100, density.range)
        assertEquals("%", density.suffix)
        assertEquals(0, ModuleCustomAmbience.FogValueGroup.fogDensity)
    }

    @Test
    fun `blur fog is independently toggleable at the original strength range`() {
        val blurFog = ModuleCustomAmbience.FogValueGroup.inner
            .single { it.name == "BlurFog" } as ToggleableValueGroup
        val strength = rangedSetting<Float>(blurFog, "Strength")

        assertFalse(blurFog.enabled)
        assertEquals(emptyList(), blurFog.aliases)
        assertEquals(listOf("Enabled", "Strength"), blurFog.inner.map { it.name })
        assertEquals(14f, strength.get())
        assertEquals(4f..24f, strength.range)
        assertEquals("", strength.suffix)
        assertEquals(14f, ModuleCustomAmbience.FogValueGroup.BlurFog.strength)
    }

    @Test
    fun `volumetric fog remains independently toggleable`() {
        val volumetricFog = ModuleCustomAmbience.FogValueGroup.inner
            .single { it.name == "VolumetricFog" } as ToggleableValueGroup
        val strength = rangedSetting<Float>(volumetricFog, "Strength")

        assertFalse(volumetricFog.enabled)
        assertEquals(emptyList(), volumetricFog.aliases)
        assertEquals(
            listOf("Enabled", "Strength", "CameraClearRadius", "MultiLayerFog"),
            volumetricFog.inner.map { it.name },
        )
        assertEquals(14f, strength.get())
        assertEquals(4f..24f, strength.range)
        assertEquals("", strength.suffix)
        assertEquals(14f, ModuleCustomAmbience.FogValueGroup.VolumetricFog.strength)
        assertEquals(12f, ModuleCustomAmbience.FogValueGroup.VolumetricFog.cameraClearRadius)
    }

    @Test
    fun `multilayer volume defaults to three immersive density bands`() {
        val multiLayer = ModuleCustomAmbience.FogValueGroup.VolumetricFog.MultiLayerFog

        assertEquals(true, multiLayer.enabled)
        assertEquals(48f, multiLayer.layerSpacing)
        assertEquals(70, multiLayer.groundDensity)
        assertEquals(45, multiLayer.middleDensity)
        assertEquals(25, multiLayer.upperDensity)
        assertEquals(
            listOf("Enabled", "LayerSpacing", "GroundDensity", "MiddleDensity", "UpperDensity"),
            multiLayer.inner.map { it.name },
        )
    }

    @Test
    fun `colored fog and all existing fog defaults remain unchanged`() {
        val fog = ModuleCustomAmbience.FogValueGroup
        val colorOverride = fog.inner.single { it.name == "FogColorOverride" } as ToggleableValueGroup

        assertFalse(colorOverride.enabled)
        assertEquals(Color4b(47, 128, 255, 201), colorOverride.inner.single { it.name == "Color" }.get())
        assertEquals(Color4b(47, 128, 255, 201), fog.inner.single { it.name == "BackgroundColor" }.get())
        assertEquals(0f..1024f, fog.inner.single { it.name == "Environmental" }.get())
        assertEquals(230f..256f, fog.inner.single { it.name == "RenderDistance" }.get())
        assertEquals(256f, fog.inner.single { it.name == "SkyEnd" }.get())
        assertEquals(20480f, fog.inner.single { it.name == "CloudEnd" }.get())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> rangedSetting(group: ToggleableValueGroup, name: String): RangedValue<T> =
        group.inner.single { it.name == name } as RangedValue<T>

    private fun serializedLegacyRanges() = ConfigSystem.serializeValueGroup(ModuleCustomAmbience)
        .getAsJsonArray("value")
        .map { it.asJsonObject }
        .single { it["name"].asString == "Fog" }
        .getAsJsonArray("value")
        .map { it.asJsonObject }
        .filter { it["name"].asString in LEGACY_RANGE_NAMES }
        .associate { it["name"].asString to it["value"].deepCopy() }

    private companion object {
        val LEGACY_RANGE_NAMES = setOf("Environmental", "RenderDistance", "SkyEnd", "CloudEnd")
    }
}
