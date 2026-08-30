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

package net.ccbluex.liquidbounce.features.module.modules.render.potionfx.assets

import net.ccbluex.liquidbounce.config.utils.TextureMode
import net.ccbluex.liquidbounce.utils.io.resource
import net.ccbluex.liquidbounce.utils.render.asTexture
import net.ccbluex.liquidbounce.utils.render.readNativeImage

internal val potionFxGlow = resource("/resources/liquidbounce/particles/glow.png")
    .readNativeImage().asTexture { "PotionFX Image glow" }

@Suppress("unused")
internal enum class PresetTexture(override val tag: String, val path: String) : TextureMode.Builtin.Preset {
    DASHED("Dashed", "potion_fx/main/dashed.png"),
    SOLID("Solid", "potion_fx/main/solid.png"),
    RUNES("Runes", "potion_fx/main/runes.png"),
    ATLAS("Atlas", "potion_fx/main/atlas.png");

    override val texture by lazy {
        resource("/resources/liquidbounce/$path")
            .readNativeImage().asTexture { "PotionFX Image $tag" }
    }
}

@Suppress("unused")
internal enum class SecondaryPresetTexture(
    override val tag: String,
    val path: String,
) : TextureMode.Builtin.Preset {
    CRACKED("Cracked", "potion_fx/secondary/cracked.png"),
    NEURON("Neuron", "potion_fx/secondary/neuron.png"),
    HEXAGON("Hexagon", "potion_fx/secondary/hexagon.png"),
    STARDUST("Stardust", "potion_fx/secondary/stardust.png");

    override val texture by lazy {
        resource("/resources/liquidbounce/$path")
            .readNativeImage().asTexture { "PotionFX Image $tag" }
    }
}
