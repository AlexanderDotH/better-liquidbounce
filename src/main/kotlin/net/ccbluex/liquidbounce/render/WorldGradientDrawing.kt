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
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.utils.forEachVertex
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.render.buffer.writeStd140
import net.minecraft.world.phys.AABB

fun WorldRenderEnvironment.drawGradientSides(
    height: Double,
    baseColor: Color4b,
    topColor: Color4b,
    box: AABB
) {
    if (height == 0.0) {
        return
    }

    drawCustomMesh(ClientRenderPipelines.quads(noDepthTest = true)) { pose ->
        addVertex(pose, box.minX, 0.0, box.minZ).setColor(baseColor)
        addVertex(pose, box.minX, height, box.minZ).setColor(topColor)
        addVertex(pose, box.maxX, height, box.minZ).setColor(topColor)
        addVertex(pose, box.maxX, 0.0, box.minZ).setColor(baseColor)

        addVertex(pose, box.maxX, 0.0, box.minZ).setColor(baseColor)
        addVertex(pose, box.maxX, height, box.minZ).setColor(topColor)
        addVertex(pose, box.maxX, height, box.maxZ).setColor(topColor)
        addVertex(pose, box.maxX, 0.0, box.maxZ).setColor(baseColor)

        addVertex(pose, box.maxX, 0.0, box.maxZ).setColor(baseColor)
        addVertex(pose, box.maxX, height, box.maxZ).setColor(topColor)
        addVertex(pose, box.minX, height, box.maxZ).setColor(topColor)
        addVertex(pose, box.minX, 0.0, box.maxZ).setColor(baseColor)

        addVertex(pose, box.minX, 0.0, box.maxZ).setColor(baseColor)
        addVertex(pose, box.minX, height, box.maxZ).setColor(topColor)
        addVertex(pose, box.minX, height, box.minZ).setColor(topColor)
        addVertex(pose, box.minX, 0.0, box.minZ).setColor(baseColor)
    }
}
