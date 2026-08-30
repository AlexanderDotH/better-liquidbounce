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
package net.ccbluex.liquidbounce.features.module.modules.render.debug

import net.ccbluex.fastutil.forEachFloat
import net.ccbluex.fastutil.step
import net.ccbluex.liquidbounce.config.types.CurveValue.Axis.Companion.axis
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.drawLineStrip
import net.ccbluex.liquidbounce.render.drawQuad
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.utils.MutableVertexList
import net.ccbluex.liquidbounce.features.simulation.PlayerSimulationCache
import net.ccbluex.liquidbounce.utils.math.vector2f
import net.ccbluex.liquidbounce.utils.text.asPlainText

internal object DebugSimulatedPlayerGroup : ToggleableValueGroup(null, "SimulatedPlayer", false) {
    private val ticksToPredict by int("TicksToPredict", 20, 5..100)

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> {
        PlayerSimulationCache.getSimulationForLocalPlayer().simulateUntil(ticksToPredict)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val snapshots = PlayerSimulationCache.getSimulationForLocalPlayer()
            .getSnapshotsBetween(0 until ticksToPredict)
        event.renderEnvironment {
            drawLineStrip(
                Color4b.BLUE.argb,
                MutableVertexList(snapshots.size).addAllRelativeToCamera(snapshots, camera) { it.pos },
            )
        }
    }
}

internal object DebugGraphGroup : ToggleableValueGroup(null, "Graph", false) {
    private val curve = curve(
        "Curve",
        mutableListOf(0f vector2f 120f, 50f vector2f 60f, 140f vector2f 120f, 180f vector2f 90f),
        xAxis = "X Axis" axis 0f..180f,
        yAxis = "Y Axis" axis 40f..120f,
    )

    @Suppress("unused")
    private val screenRenderHandler = handler<OverlayRenderEvent> { event ->
        val fontRenderer = FontManager.FONT_RENDERER
        with(event.context) {
            fontRenderer.draw("Graph".asPlainText()) {
                x = 300f
                y = 500f
                shadow = true
                scale = 0.3f
            }
            curve.xAxis.range.step(0.1f).forEachFloat { x ->
                val y = curve.transform(x)
                drawQuad(300 + x, 500 - y, 301 + x, 501 - y, Color4b.GREEN)
            }
            curve.get().forEach { point ->
                val x = point[0]
                val y = point[1]
                drawQuad(298 + x, 498 - y, 302 + x, 502 - y, Color4b.WHITE)
            }
        }
    }
}
