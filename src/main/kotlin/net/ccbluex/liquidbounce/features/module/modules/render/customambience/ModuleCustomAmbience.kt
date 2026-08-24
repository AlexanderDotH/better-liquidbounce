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

import com.google.gson.JsonObject
import com.mojang.blaze3d.textures.GpuTexture
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.customambience.worldeffects.WorldParticles
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.render.clearColor
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.client.renderer.state.LightmapRenderState
import org.joml.Vector4fc

/**
 * CustomAmbience module
 *
 * Override the ambience of the game
 */
object ModuleCustomAmbience : ClientModule("CustomAmbience", ModuleCategories.RENDER, aliases = listOf("FogChanger")) {

    val weather = enumChoice("Weather", WeatherType.SNOWY)
    private val time = enumChoice("Time", TimeType.NIGHT)

    object Precipitation : ToggleableValueGroup(this, "ModifyPrecipitation", true) {
        val gradient by float("Gradient", 0.7f, 0.1f..1f)
//        val layers by int("Layers", 3, 1..14)
    }

    /**
     * @see FogData
     */
    object FogValueGroup : ToggleableValueGroup(this, "Fog", true) {

        val engine = enumChoice("Engine", FogEngine.LEGACY)
        val horizon by floatRange("Horizon", 70f..100f, 0f..100f, "%")
        val silhouetteFeather by float("SilhouetteFeather", 12f, 0f..32f, "px")

        val fogDensity by int("FogDensity", 0, 0..100, "%")

        object FogColorOverride : ToggleableValueGroup(this, "FogColorOverride", false) {
            val color by color("Color", Color4b(47, 128, 255, 201))
        }

        init {
            tree(FogColorOverride)
        }

        object BlurFog : ToggleableValueGroup(this, "BlurFog", false) {
            val strength by float("Strength", 14f, 4f..24f)
        }

        init {
            tree(BlurFog)
        }

        object VolumetricFog : ToggleableValueGroup(this, "VolumetricFog", false) {
            val strength by float("Strength", 14f, 4f..24f)
            val cameraClearRadius by float("CameraClearRadius", 12f, 0f..32f)

            object MultiLayerFog : ToggleableValueGroup(this, "MultiLayerFog", true) {
                val layerSpacing by float("LayerSpacing", 48f, 16f..128f)
                val groundDensity by int("GroundDensity", 70, 0..100, "%")
                val middleDensity by int("MiddleDensity", 45, 0..100, "%")
                val upperDensity by int("UpperDensity", 25, 0..100, "%")
            }

            init {
                tree(MultiLayerFog)
            }
        }

        init {
            tree(VolumetricFog)
        }

        private val backgroundColor by color("BackgroundColor", Color4b(47, 128, 255, 201))

        private val environmental by floatRange("Environmental", 0f..1024f, -16f..2048f)
        private val renderDistance by floatRange("RenderDistance", 230f..256f, 0f..1024f)
        private val skyEnd by float("SkyEnd", 256f, 0f..1024f)
        private val cloudEnd by float("CloudEnd", 20480f, 0f..4096f)

        val shouldRenderBlur: Boolean
            get() = shouldApplyFogBlur(running, BlurFog.running)

        val shouldRenderVolume: Boolean
            get() = shouldApplyFogVolume(running, VolumetricFog.running)

        val shouldRenderUnified: Boolean
            get() = isUnified()

        /** Java-friendly activation seam for render mixins and optional compatibility hooks. */
        fun isUnified(): Boolean = shouldApplyUnifiedFog(running, engine.get())

        override fun prepareDeserialize(jsonObject: JsonObject) {
            val serializedValues = jsonObject.getAsJsonArray("value") ?: return
            val hasSerializedEngine = serializedValues.any { value ->
                value.asJsonObject["name"]?.asString == engine.name
            }
            if (!hasSerializedEngine) {
                engine.set(FogEngine.LEGACY)
            }
        }

        internal fun currentUnifiedHorizon(
            distantHorizonsFarClipBlocks: Float?,
            vanillaRenderDistanceChunks: Int,
        ): UnifiedFogHorizon = resolveUnifiedFogHorizon(
            horizonPercent = horizon,
            distantHorizonsFarClipBlocks = distantHorizonsFarClipBlocks,
            vanillaRenderDistanceChunks = vanillaRenderDistanceChunks,
        )

        internal fun currentFogBounds(): CustomFogBounds = CustomFogBounds(
            environmentalStart = environmental.start,
            environmentalEnd = environmental.endInclusive,
            renderDistanceStart = renderDistance.start,
            renderDistanceEnd = renderDistance.endInclusive,
            skyEnd = skyEnd,
            cloudEnd = cloudEnd,
        ).withDensity(fogDensity)

        /**
         * @see net.ccbluex.liquidbounce.injection.mixins.minecraft.render.fog.MixinFogRenderer
         */
        fun modifyFogData(fogData: FogData) {
            if (!this.running) {
                return
            }

            val bounds = currentFogBounds()
            fogData.environmentalStart = bounds.environmentalStart
            fogData.environmentalEnd = bounds.environmentalEnd
            fogData.renderDistanceStart = bounds.renderDistanceStart
            fogData.renderDistanceEnd = bounds.renderDistanceEnd
            fogData.skyEnd = bounds.skyEnd
            fogData.cloudEnd = bounds.cloudEnd
        }

        fun modifyClearColor(original: Vector4fc): Vector4fc {
            if (!this.running || backgroundColor.a == 0) {
                return original
            }

            return backgroundColor.toVector4f()
        }
    }

    /**
     * @see net.ccbluex.liquidbounce.injection.mixins.minecraft.render.MixinLightmap
     */
    object CustomLightmap : ToggleableValueGroup(this, "CustomLightmap", false) {
        val mode = choices("Mode", 0) {
            arrayOf(EditorMode.SingleColor, EditorMode.Custom)
        }

        sealed class EditorMode(name: String) : Mode(name) {
            final override val parent: ModeValueGroup<*>
                get() = mode

            abstract fun edit(texture: GpuTexture, lightmapRenderState: LightmapRenderState): Boolean

            object SingleColor : EditorMode("SingleColor") {
                private val color by color("Color", Color4b.BLUE)

                override fun edit(texture: GpuTexture, lightmapRenderState: LightmapRenderState): Boolean {
                    texture.clearColor(color)
                    return true
                }
            }

            object Custom : EditorMode("Custom") {
                private val blockLightTint by color("BlockLightTint", Color4b.WHITE.alpha(0))
                private val skyLightColor by color("SkyLightColor", Color4b.BLUE.alpha(0))
                private val ambientColor by color("AmbientColor", Color4b.BLUE.alpha(0))
                private val nightVisionColor by color("NightVisionColor", Color4b.WHITE.alpha(0))

                override fun edit(texture: GpuTexture, lightmapRenderState: LightmapRenderState): Boolean {
                    if (!blockLightTint.isTransparent) {
                        lightmapRenderState.blockLightTint = blockLightTint.toRgbVector3f()
                    }
                    if (!skyLightColor.isTransparent) {
                        lightmapRenderState.skyLightColor = skyLightColor.toRgbVector3f()
                    }
                    if (!ambientColor.isTransparent) {
                        lightmapRenderState.ambientColor = ambientColor.toRgbVector3f()
                    }
                    if (!nightVisionColor.isTransparent) {
                        lightmapRenderState.nightVisionColor = nightVisionColor.toRgbVector3f()
                    }

                    return false
                }
            }

        }
    }

    object SkyColor : ToggleableValueGroup(this, "SkyColor", false) {
        val color by color("Color", Color4b.BLUE)
    }

    init {
        tree(Precipitation)
        tree(FogValueGroup)
        tree(CustomLightmap)
        tree(SkyColor)
        tree(WorldParticles)
    }

    @JvmStatic
    fun getWorldClockTime(original: Long): Long {
        return if (running) {
            when (time.get()) {
                TimeType.NO_CHANGE -> original
                TimeType.DAWN -> 23041L
                TimeType.DAY -> 1000L
                TimeType.NOON -> 6000L
                TimeType.DUSK -> 12610L
                TimeType.NIGHT -> 13000L
                TimeType.MID_NIGHT -> 18000L
            }
        } else {
            original
        }
    }

    @Suppress("unused")
    enum class WeatherType(override val tag: String) : Tagged {
        NO_CHANGE("NoChange"),
        SUNNY("Sunny"),
        RAINY("Rainy"),
        SNOWY("Snowy"),
        THUNDER("Thunder")
    }

    enum class TimeType(override val tag: String) : Tagged {
        NO_CHANGE("NoChange"),
        DAWN("Dawn"),
        DAY("Day"),
        NOON("Noon"),
        DUSK("Dusk"),
        NIGHT("Night"),
        MID_NIGHT("MidNight")
    }

}
