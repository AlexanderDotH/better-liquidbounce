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
package net.ccbluex.liquidbounce.features.module.modules.render

import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.DebuggedOwner
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.debug.DebugGraphGroup
import net.ccbluex.liquidbounce.features.module.modules.render.debug.DebugParameterCapture
import net.ccbluex.liquidbounce.features.module.modules.render.debug.DebugParameterKey
import net.ccbluex.liquidbounce.features.module.modules.render.debug.DebugSimulatedPlayerGroup
import net.ccbluex.liquidbounce.features.module.modules.render.debug.buildDebugParameterLines
import net.ccbluex.liquidbounce.features.module.modules.render.debug.renderDebugParameterOverlay
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.drawTriangle
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.math.geometry.Line
import net.ccbluex.liquidbounce.render.engine.type.toVec3f
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Debug module
 *
 * Only of interest to developers.
 */

object ModuleDebug : ClientModule("Debug", ModuleCategories.RENDER) {

    private val parameters by boolean("Parameters", true).onChanged { _ ->
        debugParameters.clear()
    }
    private val geometry by boolean("Geometry", true).onChanged { _ ->
        debuggedGeometry.clear()
    }

    private val expireTime by int("Expires", 5, 1..30, "secs")

    init {
        tree(DebugSimulatedPlayerGroup)
        tree(DebugGraphGroup)
    }

    private val keyComparator = compareBy<DebugParameterKey> { it.owner.debugOwnerId }
        .thenComparing(DebugParameterKey::name)

    private val debugParameters = Object2ObjectRBTreeMap<DebugParameterKey, DebugParameterCapture>(keyComparator)

    private val debuggedGeometry = Object2ObjectRBTreeMap<DebugParameterKey, DebuggedGeometry>(keyComparator)

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (!geometry) {
            return@handler
        }

        event.renderEnvironment {
            debuggedGeometry.values.forEach { geometry ->
                geometry.render()
            }
        }
    }

    @Suppress("unused")
    private val expireHandler = handler<GameTickEvent>(priority = FIRST_PRIORITY) {
        val earliest = System.currentTimeMillis() - expireTime * 1000

        debugParameters.entries.removeIf { (_, capture) ->
            capture.time <= earliest
        }
    }

    @Suppress("unused")
    private val screenRenderHandler = handler<OverlayRenderEvent> { event ->
        if (mc.options.keyPlayerList.isDown || !parameters) {
            return@handler
        }
        renderDebugParameterOverlay(event, buildDebugParameterLines(debugParameters))
    }

    fun debugGeometry(owner: DebuggedOwner, name: String, geometry: DebuggedGeometry?) {
        // Do not take any new debugging while the module is off
        if (!running) {
            return
        }

        if (geometry != null) {
            debuggedGeometry[DebugParameterKey(owner, name)] = geometry
        } else {
            debuggedGeometry.remove(DebugParameterKey(owner, name))
        }
    }

    inline fun DebuggedOwner.debugGeometry(name: String, lazyGeometry: () -> DebuggedGeometry?) {
        if (!running) {
            return
        }

        debugGeometry(owner = this, name, lazyGeometry())
    }

    fun debugParameter(owner: DebuggedOwner, name: String, value: Any?) {
        if (!running) {
            return
        }

        debugParameters[DebugParameterKey(owner, name)] = DebugParameterCapture(value = value)
    }

    inline fun DebuggedOwner.debugParameter(name: String, lazyValue: () -> Any?) {
        if (!running) {
            return
        }

        debugParameter(owner = this, name, lazyValue())
    }

    fun getArrayEntryColor(idx: Int, length: Int): Color4b {
        val hue = idx.toFloat() / length.toFloat()
        return Color4b.ofHSB(hue, 1f, 1f, alpha = 32f / 255f)
    }

    fun interface DebuggedGeometry {
        context(env: WorldRenderEnvironment)
        fun render()
    }

    class DebuggedLine(line: Line, val color: Color4b) : DebuggedGeometry {
        val from: Vec3
        val to: Vec3

        init {
            val normalizedDirection = line.direction.normalize()

            this.from = line.position.subtract(normalizedDirection.scale(100.0))
            this.to = line.position.add(normalizedDirection.scale(100.0))
        }

        context(env: WorldRenderEnvironment)
        override fun render() {
            env.withPositionRelativeToCamera {
                env.drawLine(
                    from,
                    to,
                    color.argb,
                )
            }
        }
    }

    class DebuggedTriangle(
        val p1: Vec3,
        val p2: Vec3,
        val p3: Vec3,
        val color: Color4b,
    ) : DebuggedGeometry {
        context(env: WorldRenderEnvironment)
        override fun render() {
            env.withPositionRelativeToCamera {
                env.drawTriangle(
                    p1 = p1.toVec3f(),
                    p2 = p2.toVec3f(),
                    p3 = p3.toVec3f(),
                    argb = color.argb,
                )
            }
        }
    }

    class DebuggedLineSegment(val from: Vec3, val to: Vec3, val color: Color4b) : DebuggedGeometry {
        context(env: WorldRenderEnvironment)
        override fun render() {
            env.withPositionRelativeToCamera {
                env.drawLine(
                    from,
                    to,
                    color.argb,
                )
            }
        }
    }

    open class DebuggedBox(val box: AABB, val color: Color4b) : DebuggedGeometry {
        context(env: WorldRenderEnvironment)
        override fun render() {
            env.drawBox(box.move(env.camera.position().reverse()), color)
        }
    }

    class DebuggedPoint(point: Vec3, color: Color4b, size: Double = 0.2) : DebuggedBox(
        AABB.ofSize(point, size, size, size),
        color
    )

    class DebugCollection(val geometry: Collection<DebuggedGeometry>) : DebuggedGeometry {
        context(env: WorldRenderEnvironment)
        override fun render() {
            this.geometry.forEach { it.render() }
        }
    }

    override fun onDisabled() {
        // Might clean up some memory if we disable the module
        debuggedGeometry.clear()
        debugParameters.clear()
        super.onDisabled()
    }

}
