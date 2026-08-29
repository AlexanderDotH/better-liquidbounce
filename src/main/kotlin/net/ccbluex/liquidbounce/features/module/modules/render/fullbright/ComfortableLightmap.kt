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
package net.ccbluex.liquidbounce.features.module.modules.render.fullbright

import org.joml.Vector3f
import org.joml.Vector3fc

object ComfortableLightmap {

    @JvmStatic
    fun liftAmbient(ambient: Vector3fc, minimumBrightness: Float): Vector3fc {
        require(minimumBrightness in 0f..1f) { "Minimum brightness must be between zero and one" }

        val lift = minimumBrightness - brightestChannel(ambient)
        if (lift <= 0f) {
            return ambient
        }

        return Vector3f(ambient).add(lift, lift, lift)
    }

    /** Keeps the same white point after [liftAmbient] consumes part of the lightmap's remaining headroom. */
    @JvmStatic
    fun lightContributionMultiplier(ambient: Vector3fc, minimumBrightness: Float): Float {
        require(minimumBrightness in 0f..1f) { "Minimum brightness must be between zero and one" }

        val currentBrightness = brightestChannel(ambient)
        if (currentBrightness >= minimumBrightness) {
            return 1f
        }

        return (1f - minimumBrightness) / (1f - currentBrightness)
    }

    private fun brightestChannel(ambient: Vector3fc) = maxOf(ambient.x(), ambient.y(), ambient.z())

}
