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

package net.ccbluex.liquidbounce.features.module.modules.render.storageesp

import kotlinx.atomicfu.atomic
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.CachedMeshStorage
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.addShapeFaces
import net.ccbluex.liquidbounce.render.addShapeOutlines
import net.ccbluex.liquidbounce.render.buildMesh
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.drawGenericBlockESP
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.getDynamicTransformsUniform
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.translate
import net.ccbluex.liquidbounce.render.utils.DistanceFadeUniformValueGroup
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.math.PositionedVoxelShape
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f

internal open class StorageBoxMode(
    moduleName: String,
    private val parentProvider: () -> ModeValueGroup<Mode>,
    private val distanceFadeProvider: () -> DistanceFadeUniformValueGroup,
    private val hasTrackedBlocks: () -> Boolean,
    private val collectTrackedShapes: () -> List<PositionedVoxelShape<StorageEspCategory>>,
    private val categorizeEntity: (Entity) -> StorageEspCategory?,
) : Mode("Box") {

    override val parent: ModeValueGroup<Mode>
        get() = parentProvider()

    private val dirtyFlag = atomic(true)
    private val blockFaces = CachedMeshStorage("$moduleName $name BlockFaces")
    private val blockOutlines = CachedMeshStorage("$moduleName $name BlockOutlines")
    private val outline by boolean("Outline", true)
    private val entityBoxes = mutableListOf<EntityBox>()

    fun markDirty() {
        if (running) dirtyFlag.value = true
    }

    override fun enable() {
        dirtyFlag.value = true
        super.enable()
    }

    override fun disable() {
        blockFaces.clearStates()
        blockFaces.clearBuffers()
        blockOutlines.clearStates()
        blockOutlines.clearBuffers()
        entityBoxes.clear()
        super.disable()
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        drawBlockMeshes(event)
        if (entityBoxes.isEmpty()) return@handler

        event.renderEnvironment {
            entityBoxes.forEach { (entity, box, color) ->
                val outlineColor = color.with(a = 100).takeIf { outline }
                withPositionRelativeToCamera(entity.interpolateCurrentPosition(event.partialTicks)) {
                    drawBox(box, color.with(a = 50), outlineColor)
                }
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (!collectEntityBoxes()) return@handler
        if (!hasTrackedBlocks()) {
            blockFaces.clearStates()
            blockOutlines.clearStates()
            return@handler
        }
        if (!dirtyFlag.compareAndSet(expect = true, update = false)) return@handler

        val shapes = collectTrackedShapes()
        buildFaces(shapes)
        if (outline) buildOutlines(shapes)
    }

    private fun drawBlockMeshes(event: WorldRenderEvent) {
        val distanceFade = distanceFadeProvider()
        val target = mc.gameRenderer.mainRenderTarget()
        if (outline) {
            target.drawGenericBlockESP(
                blockOutlines,
                ClientRenderPipelines.relativeLines(useColor = true),
                distanceFade,
            ) { getDynamicTransformsUniform(modelView = Matrix4f(event.modelViewMatrix)) }
        }
        target.drawGenericBlockESP(
            blockFaces,
            ClientRenderPipelines.relativeQuads(useColor = true),
            distanceFade,
        ) { getDynamicTransformsUniform(modelView = Matrix4f(event.modelViewMatrix)) }
    }

    private fun collectEntityBoxes(): Boolean {
        val level = mc.level ?: return false
        entityBoxes.clear()
        level.entitiesForRendering().forEach { entity ->
            val type = categorizeEntity(entity)?.takeIf {
                !it.color.isTransparent && it.shouldRender(entity, ignoreDistance = false)
            } ?: return@forEach
            val dimensions = entity.getDimensions(entity.pose)
            val halfWidth = dimensions.width.toDouble() / 2.0
            val box = AABB(
                -halfWidth, 0.0, -halfWidth,
                halfWidth, dimensions.height.toDouble(), halfWidth,
            ).inflate(0.05)
            entityBoxes.add(EntityBox(entity, box, type.color))
        }
        return true
    }

    private fun buildFaces(shapes: List<PositionedVoxelShape<StorageEspCategory>>) {
        blockFaces.buildMesh(
            pipeline = ClientRenderPipelines.relativeQuads(useColor = true),
            origin = player.blockPosition(),
        ) { pose, origin ->
            shapes.forEach { shape ->
                pose.withPush {
                    translate(shape.blockPos, origin)
                    addShapeFaces(last().pose(), shape.shape, shape.key.color.alpha(50))
                }
            }
        }
    }

    private fun buildOutlines(shapes: List<PositionedVoxelShape<StorageEspCategory>>) {
        blockOutlines.buildMesh(
            pipeline = ClientRenderPipelines.relativeLines(useColor = true),
            origin = player.blockPosition(),
        ) { pose, origin ->
            shapes.forEach { shape ->
                pose.withPush {
                    translate(shape.blockPos, origin)
                    addShapeOutlines(last().pose(), shape.shape, shape.key.color.alpha(100))
                }
            }
        }
    }

    @JvmRecord
    private data class EntityBox(val entity: Entity, val box: AABB, val color: Color4b)
}
