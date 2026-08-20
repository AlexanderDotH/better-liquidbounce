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

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.gson.stategies.Exclude
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType

data class MerchantReach private constructor(val range: Float, val wallRange: Float) {

    companion object {
        val RANGE_BOUNDS = 1f..6f
        val WALL_RANGE_BOUNDS = 0f..6f
        val DEFAULT = MerchantReach(range = 4.5f, wallRange = 3f)

        fun of(range: Float, wallRange: Float): MerchantReach {
            val safeRange = range.finiteOr(DEFAULT.range).coerceIn(RANGE_BOUNDS)
            val maximumWallRange = minOf(safeRange, WALL_RANGE_BOUNDS.endInclusive)
            val safeWallRange = wallRange.finiteOr(DEFAULT.wallRange).coerceIn(0f, maximumWallRange)
            return MerchantReach(safeRange, safeWallRange)
        }

        private fun Float.finiteOr(fallback: Float) = takeIf(Float::isFinite) ?: fallback
    }
}

class MerchantReachValue(
    name: String,
    defaultValue: MerchantReach = MerchantReach.DEFAULT,
) : Value<MerchantReach>(
    name = name,
    defaultValue = MerchantReach.of(defaultValue.range, defaultValue.wallRange),
    valueType = ValueType.MERCHANT_REACH,
) {

    @Exclude
    val rangeBounds = MerchantReach.RANGE_BOUNDS

    @Exclude
    val wallRangeBounds = MerchantReach.WALL_RANGE_BOUNDS

    @Exclude
    val suffix = "blocks"

    override fun deserializeFrom(gson: Gson, element: JsonElement) {
        val reach = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return
        val current = get()
        set(MerchantReach.of(reach.float("range") ?: current.range, reach.float("wallRange") ?: current.wallRange))
    }

    private fun JsonObject.float(field: String): Float? {
        val primitive = get(field)?.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive ?: return null
        if (!primitive.isNumber) {
            return null
        }

        return runCatching(primitive::getAsFloat).getOrNull()
    }
}
