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

package net.ccbluex.liquidbounce.render.gui

import kotlin.math.abs

class GuiOverlapRearranger(
    private val maxIter: Int = 16,
) {
    init {
        require(maxIter > 0) { "maxIter must be greater than zero." }
    }

    fun rearrange(elements: Collection<GuiRearrangeable>) {
        if (elements.size <= 1) return

        val sorted = sortedElements(elements)

        var iter = 0
        while (iter++ < maxIter) {
            if (!resolvePass(sorted)) break
        }
    }

    private fun sortedElements(elements: Collection<GuiRearrangeable>) = elements.toTypedArray().apply {
        sortWith { first, second ->
            val firstY = first.bounds.yCenter
            val secondY = second.bounds.yCenter
            if (firstY != secondY) {
                firstY.compareTo(secondY)
            } else {
                first.bounds.xCenter.compareTo(second.bounds.xCenter)
            }
        }
    }

    private fun resolvePass(elements: Array<GuiRearrangeable>): Boolean {
        var moved = false
        for (firstIndex in elements.indices) {
            for (secondIndex in firstIndex + 1 until elements.size) {
                if (resolveOverlap(elements[firstIndex], elements[secondIndex])) moved = true
            }
        }
        return moved
    }

    private fun resolveOverlap(first: GuiRearrangeable, second: GuiRearrangeable): Boolean {
        val firstBounds = first.bounds
        val secondBounds = second.bounds
        val horizontalOverlap = (firstBounds.width + secondBounds.width) * 0.5f -
            abs(firstBounds.xCenter - secondBounds.xCenter)
        val verticalOverlap = (firstBounds.height + secondBounds.height) * 0.5f -
            abs(firstBounds.yCenter - secondBounds.yCenter)
        if (horizontalOverlap <= 0f || verticalOverlap <= 0f) return false

        second.bounds = if (horizontalOverlap < verticalOverlap) {
            val offset = if (firstBounds.xCenter < secondBounds.xCenter) horizontalOverlap else -horizontalOverlap
            secondBounds.offset(offset, 0f)
        } else {
            val offset = if (firstBounds.yCenter < secondBounds.yCenter) verticalOverlap else -verticalOverlap
            secondBounds.offset(0f, offset)
        }
        return true
    }
}
