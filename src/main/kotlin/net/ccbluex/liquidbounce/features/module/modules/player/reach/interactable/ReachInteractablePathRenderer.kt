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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableRenderSnapshot
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.drawLineStrip
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.utils.MutableVertexList

internal object ReachInteractablePathRenderer {
    fun render(event: WorldRenderEvent, snapshot: InteractableRenderSnapshot.Route) {
        event.renderEnvironment {
            snapshot.route.paths.forEach { path ->
                drawLineStrip(
                    Color4b.WHITE.argb,
                    MutableVertexList(path.points.size).addAllRelativeToCamera(path.points, camera) { it },
                )
            }
            snapshot.route.verticalClips.forEach { clip ->
                drawLineStrip(
                    Color4b.BLUE.argb,
                    MutableVertexList(2).addAllRelativeToCamera(listOf(clip.from, clip.to), camera) { it },
                )
            }
        }
    }
}
