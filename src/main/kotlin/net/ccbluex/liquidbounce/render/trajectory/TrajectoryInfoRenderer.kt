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

@file:JvmName("TrajectoryInfoRendererKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.trajectory

import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.drawBoxSide
import net.ccbluex.liquidbounce.render.drawLines
import net.ccbluex.liquidbounce.render.drawLinesWithWidth
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.utils.MutableVertexList
import net.ccbluex.liquidbounce.render.utils.lineStripAsLines
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.render.trajectory.TrajectoryInfoRenderer.Companion.getHypotheticalTrajectory
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryType
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class TrajectoryInfoRenderer @Suppress("LongParameterList") constructor(
    /**
     * Entity used by the simulation as the projectile source.
     *
     * This affects spawn position, inherited momentum, clip context, collision filtering,
     * and projectile-specific hit margin handling.
     */
    val simulationOwner: Entity,
    /**
     * Entity displayed as the projectile owner in UI.
     *
     * This is separate from [simulationOwner] because some real projectiles have no traceable owner and
     * still need a non-null simulation source entity.
     */
    val displayOwner: Entity?,
    val icon: ItemStack,
    velocity: Vec3,
    pos: Vec3,
    val trajectoryInfo: TrajectoryInfo,
    val trajectoryType: TrajectoryType,
    /**
     * Only used for rendering. No effect on simulation.
     */
    val type: Type,
    /**
     * The visualization should be what-you-see-is-what-you-get, so we use the actual current position of the player
     * for simulation. Since the trajectory line should follow the player smoothly, we offset it by some amount.
     */
    private val renderOffset: Vec3
) {
    enum class Type {
        /**
         * From the entity holding items.
         *
         * @see [getHypotheticalTrajectory]
         */
        HYPOTHETICAL,

        /**
         * From a moving entity, such as [net.minecraft.world.entity.projectile.Projectile].
         */
        REAL,
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun getHypotheticalTrajectory(
            simulationOwner: Entity,
            trajectoryInfo: TrajectoryInfo,
            trajectoryType: TrajectoryType,
            rotation: Rotation,
            icon: ItemStack = ItemStack.EMPTY,
            partialTicks: Float = mc.deltaTracker.getGameTimeDeltaPartialTick(true),
        ): TrajectoryInfoRenderer = createHypotheticalTrajectory(
            simulationOwner = simulationOwner,
            trajectoryInfo = trajectoryInfo,
            trajectoryType = trajectoryType,
            rotation = rotation,
            icon = icon,
            partialTicks = partialTicks,
        )
    }

    private val simulation = TrajectorySimulation(
        simulationOwner = simulationOwner,
        trajectoryInfo = trajectoryInfo,
        trajectoryType = trajectoryType,
        velocity = velocity,
        pos = pos,
    )

    fun runSimulation(
        maxTicks: Int,
    ): SimulationResult = simulation.run(maxTicks)

    context(env: WorldRenderEnvironment)
    fun drawTrajectoryForProjectile(
        maxTicks: Int,
        partialTicks: Float,
        trajectoryColor: Color4b,
        blockHitColor: Color4b?,
        entityHitColor: Color4b?,
        lineWidth: Float = 1f,
    ): SimulationResult {
        val simulationResult = runSimulation(maxTicks)

        val (landingPosition, positions) = simulationResult

        env.drawTrajectoryForProjectile(positions, trajectoryColor.argb, lineWidth)

        when (landingPosition) {
            null -> return simulationResult
            is BlockHitResult -> if (blockHitColor != null) {
                env.renderHitBlockFace(landingPosition, blockHitColor)
            }
            is EntityHitResult -> if (entityHitColor != null) {
                val entities = listOf(landingPosition.entity)

                env.drawHitEntities(entityHitColor, entities, partialTicks)
            }
            else -> error("Unexpected HitResult type: ${landingPosition::class.java.name}")
        }

        if (trajectoryInfo == TrajectoryInfo.POTION && entityHitColor != null) {
            env.drawSplashPotionTargets(landingPosition.location, trajectoryInfo, partialTicks, entityHitColor)
        }

        return simulationResult
    }

    private fun WorldRenderEnvironment.drawTrajectoryForProjectile(
        positions: List<Vec3>,
        argb: Int,
        lineWidth: Float,
    ) {
        val origin = positions.firstOrNull() ?: return
        val lineVertices = MutableVertexList(positions.size).addAllRelative(positions, origin)
            .lineStripAsLines()

        // Don't use LineStrip because in batch mode
        poseStack.pushPose()
        poseStack.translate(origin.add(renderOffset).subtract(camera.position()))
        if (lineWidth == 1f) {
            drawLines(argb, lineVertices)
        } else {
            drawLinesWithWidth(argb, lineWidth, lineVertices)
        }
        poseStack.popPose()
    }

    @JvmRecord
    data class SimulationResult(
        val hitResult: HitResult?,
        val positions: List<Vec3>,
    )
}
