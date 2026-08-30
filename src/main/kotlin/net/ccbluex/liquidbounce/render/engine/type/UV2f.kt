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
package net.ccbluex.liquidbounce.render.engine.type

@JvmInline
value class UV2f private constructor(private val bits: Long) {
    val u: Float inline get() = component1()
    val v: Float inline get() = component2()

    constructor(u: Float, v: Float) : this(pack(u, v))

    operator fun component1(): Float = Float.fromBits((bits shr Int.SIZE_BITS).toInt())
    operator fun component2(): Float = Float.fromBits(bits.toInt())

    companion object {
        private fun pack(u: Float, v: Float): Long =
            (u.toRawBits().toLong() shl Int.SIZE_BITS) or (v.toRawBits().toLong() and UINT_MASK)

        private const val UINT_MASK = 0xFFFF_FFFFL
    }
}
