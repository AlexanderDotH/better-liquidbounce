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

package net.ccbluex.liquidbounce.features.module.modules.render.bedplates

import net.ccbluex.liquidbounce.utils.block.bed.BedState
import net.ccbluex.liquidbounce.utils.block.bed.SurroundingBlock
import net.minecraft.world.item.ItemStack

internal data class BedPlateRenderState(
    @JvmField val bedState: BedState,
    @JvmField var distance: Double,
    @JvmField var surrounding: List<SurroundingBlock>,
    @JvmField var itemStacksForRender: List<ItemStack>,
) : Comparable<BedPlateRenderState> {
    constructor(bedState: BedState) : this(bedState, 0.0, emptyList(), emptyList())

    override fun compareTo(other: BedPlateRenderState): Int = distance.compareTo(other.distance)
}

private val ROMAN_NUMERALS = arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII")

internal fun bedLayerLabel(layer: Int): String = ROMAN_NUMERALS[layer]
