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

package net.ccbluex.liquidbounce.features.module.modules.render.betterinventory

import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f

/** Keeps a container preview visible without covering the hovered item's tooltip. */
internal object ContainerItemViewLayout {

    private const val GAP = 4F
    private const val VIEWPORT_MARGIN = 4F

    fun avoidTooltip(
        preview: BoundingBox2f,
        tooltip: BoundingBox2f,
        viewportWidth: Float,
        viewportHeight: Float,
    ): BoundingBox2f {
        if (!(preview intersects tooltip)) return preview

        val horizontalCandidates = horizontalCandidates(preview, tooltip)
        horizontalCandidates.firstOrNull { it.fitsHorizontally(viewportWidth) }?.let { candidate ->
            return candidate.clampVertically(viewportHeight)
        }

        val verticalCandidates = verticalCandidates(preview, tooltip)
        verticalCandidates.firstOrNull { it.fitsVertically(viewportHeight) }?.let { candidate ->
            return candidate.clampHorizontally(viewportWidth)
        }

        // Tiny viewports cannot contain both rectangles. Keeping them separate is still preferable to overlap.
        return horizontalCandidates.first()
    }

    private fun horizontalCandidates(
        preview: BoundingBox2f,
        tooltip: BoundingBox2f,
    ): List<BoundingBox2f> {
        val right = preview.offset(tooltip.xMax + GAP - preview.xMin, 0F)
        val left = preview.offset(tooltip.xMin - GAP - preview.xMax, 0F)
        return if (preview.xCenter >= tooltip.xCenter) listOf(right, left) else listOf(left, right)
    }

    private fun verticalCandidates(
        preview: BoundingBox2f,
        tooltip: BoundingBox2f,
    ): List<BoundingBox2f> {
        val below = preview.offset(0F, tooltip.yMax + GAP - preview.yMin)
        val above = preview.offset(0F, tooltip.yMin - GAP - preview.yMax)
        return if (preview.yCenter >= tooltip.yCenter) listOf(below, above) else listOf(above, below)
    }

    private fun BoundingBox2f.fitsHorizontally(viewportWidth: Float): Boolean =
        xMin >= VIEWPORT_MARGIN && xMax <= viewportWidth - VIEWPORT_MARGIN

    private fun BoundingBox2f.fitsVertically(viewportHeight: Float): Boolean =
        yMin >= VIEWPORT_MARGIN && yMax <= viewportHeight - VIEWPORT_MARGIN

    private fun BoundingBox2f.clampHorizontally(viewportWidth: Float): BoundingBox2f {
        if (width > viewportWidth - VIEWPORT_MARGIN * 2F) return this
        if (xMin < VIEWPORT_MARGIN) return offset(VIEWPORT_MARGIN - xMin, 0F)
        if (xMax > viewportWidth - VIEWPORT_MARGIN) {
            return offset(viewportWidth - VIEWPORT_MARGIN - xMax, 0F)
        }
        return this
    }

    private fun BoundingBox2f.clampVertically(viewportHeight: Float): BoundingBox2f {
        if (height > viewportHeight - VIEWPORT_MARGIN * 2F) return this
        if (yMin < VIEWPORT_MARGIN) return offset(0F, VIEWPORT_MARGIN - yMin)
        if (yMax > viewportHeight - VIEWPORT_MARGIN) {
            return offset(0F, viewportHeight - VIEWPORT_MARGIN - yMax)
        }
        return this
    }
}
