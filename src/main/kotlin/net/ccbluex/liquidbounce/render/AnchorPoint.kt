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

package net.ccbluex.liquidbounce.render

import net.ccbluex.fastutil.objectObjectMapOf
import net.ccbluex.liquidbounce.render.utils.forEachVertex
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.render.buffer.writeStd140

@Suppress("unused")
enum class AnchorPoint(val xFactor: Float, val yFactor: Float) {
    TOP_LEFT(-1.0f, 0.0f),    TOP_CENTER(-0.5f, 0.0f),    TOP_RIGHT(0.0f, 0.0f),
    CENTER_LEFT(-1.0f, -0.5f), CENTER(-0.5f, -0.5f),      CENTER_RIGHT(0.0f, -0.5f),
    BOTTOM_LEFT(-1.0f, -1.0f), BOTTOM_CENTER(-0.5f, -1.0f), BOTTOM_RIGHT(0.0f, -1.0f)
}
