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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar


import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.drawLines
import net.ccbluex.liquidbounce.render.drawLinesWithWidth
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowSource
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderRenderer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.utils.MutableVertexList
import net.ccbluex.liquidbounce.render.utils.lineStripAsLines
import net.minecraft.world.phys.Vec3

/** Builds the immutable outbound-only route snapshot used by the world renderer. */
internal fun buildSpearKillAStarRenderPath(origin: Vec3, outboundWaypoints: List<Vec3>): List<Vec3> {
    if (!origin.isFiniteSpearKillPathPoint()) return emptyList()

    return buildList {
        add(origin)
        for (waypoint in outboundWaypoints) {
            if (waypoint.isFiniteSpearKillPathPoint() && waypoint.distanceToSqr(last()) > 0.0) {
                add(waypoint)
            }
        }
    }
}

/** Keeps the visual opt-in independent from Packet transport and path planning. */
internal fun shouldRenderSpearKillAStarPath(
    previewEnabled: Boolean = true,
    packetAStarEnabled: Boolean,
    renderPathEnabled: Boolean,
    renderPath: List<Vec3>,
): Boolean = previewEnabled && packetAStarEnabled && renderPathEnabled && renderPath.size >= 2

/** Visuals shared by the A* route and SpearKill's target Glow preview. */
internal data class SpearKillAStarPathAppearance(
    val color: Color4b,
    val style: EspGlowStyle,
) {
    val glowMaskColor: Color4b
        get() = color
}

/** Renders a tracer-style core and halo for SpearKill's already planned outbound A* route. */
internal fun renderSpearKillAStarPath(
    event: WorldRenderEvent,
    renderPath: List<Vec3>,
    appearance: SpearKillAStarPathAppearance,
) {
    val vertices = MutableVertexList(renderPath.size)
        .addAllRelativeToCamera(renderPath, event.camera) { point ->
            point.add(0.0, SPEAR_KILL_A_STAR_PATH_RENDER_Y_OFFSET, 0.0)
        }
        .lineStripAsLines()
    if (vertices.size == 0) return

    event.renderEnvironment {
        drawLines(appearance.color.argb, vertices)
    }
    EspShaderRenderer.contributeGlow(event, EspGlowSource.SPEAR_KILL_PATH, appearance.style) {
        drawLinesWithWidth(
            appearance.glowMaskColor.argb,
            SPEAR_KILL_A_STAR_PATH_GLOW_MASK_LINE_WIDTH,
            vertices,
        )
    }
}

private fun Vec3.isFiniteSpearKillPathPoint(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private const val SPEAR_KILL_A_STAR_PATH_GLOW_MASK_LINE_WIDTH = 2f
private const val SPEAR_KILL_A_STAR_PATH_RENDER_Y_OFFSET = 0.05
