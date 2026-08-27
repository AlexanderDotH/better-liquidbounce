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

import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFallSafetyContext
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteFailure
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRoutePlanner
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteProgress
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteRenderSnapshot
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteRequest
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteSearchSnapshot
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteSettings
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteStance
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteTargetKind
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteTask
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteWorld
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionRoute
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableResolvedTarget
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetLock
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.toBlockPos
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.ccbluex.liquidbounce.utils.raytracing.clip
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

internal sealed interface InteractableRenderSnapshot {
    data class Planning(val search: InteractableRouteSearchSnapshot) : InteractableRenderSnapshot
    data class Route(val route: InteractableRouteRenderSnapshot) : InteractableRenderSnapshot
}

internal fun resolveInteractableGoalStances(
    targetNode: BlockPos,
    origin: Vec3,
    interactionRange: Double,
    canStand: (BlockPos) -> Boolean,
    canInteract: (BlockPos) -> Boolean,
): List<InteractableRouteStance> {
    require(interactionRange.isFinite() && interactionRange > 0.0) {
        "Interaction range must be finite and positive"
    }
    val radius = ceil(interactionRange).toInt()
    val targetCenter = Vec3.atCenterOf(targetNode)
    return candidateNodes(targetNode, radius)
        .map { node -> InteractableRouteStance(node, node.stancePosition()) }
        .filter { it.position.distanceTo(targetCenter) <= interactionRange }
        .filter { canStand(it.node) && canInteract(it.node) }
        .sortedWith(compareBy<InteractableRouteStance> { it.position.distanceToSqr(origin) }
        .thenBy { it.node.asLong() })
        .toList()
}

private fun candidateNodes(center: BlockPos, radius: Int): Sequence<BlockPos> = sequence {
    for (x in center.x - radius..center.x + radius) {
        for (y in center.y - radius..center.y + radius) {
            for (z in center.z - radius..center.z + radius) yield(BlockPos(x, y, z))
        }
    }
}

internal class MinecraftInteractableRoutePort : ControllerRoutePort<
    InteractableResolvedTarget,
    InteractableSessionRoute<InteractablePacketInstruction>,
    InteractableRenderSnapshot
> {
    override fun begin(
        target: InteractableResolvedTarget,
        origin: Vec3,
        settings: InteractableSettingsSnapshot,
    ): ControllerRouteTask<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> {
        val level = requireNotNull(mc.level) { "Interactable route requires a world" }
        val player = requireNotNull(mc.player) { "Interactable route requires a player" }
        val routeWorld = MinecraftInteractableRouteWorld(level, player)
        val goalWorld = MinecraftInteractableGoalWorld(level, player, target, settings.interactionRange)
        val targetNode = goalWorld.targetNode ?: return FailedControllerRouteTask("TARGET_MISSING")
        val goals = resolveInteractableGoalStances(
            targetNode = targetNode,
            origin = origin,
            interactionRange = settings.interactionRange,
            canStand = { routeWorld.isLoaded(it) && routeWorld.isPassable(it) && routeWorld.isSupported(it) },
            canInteract = goalWorld::canInteract,
        )
        if (goals.isEmpty()) return FailedControllerRouteTask(InteractableRouteFailure.NO_VALID_GOAL.name)

        val request = InteractableRouteRequest(
            origin = origin,
            goalStances = goals,
            targetKind = when (target.lock) {
                is InteractableTargetLock.Block -> InteractableRouteTargetKind.STATIONARY_BLOCK
                is InteractableTargetLock.ContainerVehicle -> InteractableRouteTargetKind.MOVING_CONTAINER
            },
            settings = settings.toRouteSettings(),
        )
        val task = InteractableRoutePlanner(routeWorld).begin(request)
        val fallSafety = VClipFallSafetyContext(
            initialFallDistance = player.fallDistance.toDouble(),
            safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        )
        return MinecraftControllerRouteTask(task, settings, fallSafety)
    }
}

private class MinecraftControllerRouteTask(
    private val delegate: InteractableRouteTask,
    private val settings: InteractableSettingsSnapshot,
    private val fallSafety: VClipFallSafetyContext,
) : ControllerRouteTask<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> {
    override fun advance(
        nodes: Int,
    ): ControllerRouteProgress<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> =
        when (val progress = delegate.advance(nodes)) {
            is InteractableRouteProgress.Running -> ControllerRouteProgress.Running(
                InteractableRenderSnapshot.Planning(progress.snapshot),
            )
            is InteractableRouteProgress.Failed -> ControllerRouteProgress.Failed(progress.reason.name)
            is InteractableRouteProgress.Ready -> compile(progress.plan)
        }

    override fun cancel() = delegate.cancel()

    private fun compile(
        plan: InteractableRoutePlan,
    ): ControllerRouteProgress<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> =
        when (val result = InteractableRouteCompiler.compile(
            plan,
            settings.routing.stepDistance,
            settings.surfaceFallback.transport,
            fallSafety,
        )) {
            is InteractableRouteCompileResult.Ready -> ControllerRouteProgress.Ready(
                result.route,
                InteractableRenderSnapshot.Route(plan.renderSnapshot),
            )
            InteractableRouteCompileResult.VClipUnavailable -> ControllerRouteProgress.Failed("VCLIP_UNAVAILABLE")
        }
}

private class FailedControllerRouteTask(
    private val reason: String,
) : ControllerRouteTask<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> {
    override fun advance(
        nodes: Int,
    ): ControllerRouteProgress<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> =
        ControllerRouteProgress.Failed(reason)

    override fun cancel() = Unit
}

private class MinecraftInteractableRouteWorld(
    private val level: net.minecraft.client.multiplayer.ClientLevel,
    private val player: Entity,
) : InteractableRouteWorld {
    private val dimensions = player.getDimensions(Pose.STANDING)

    override fun isWithinBuildHeight(y: Int): Boolean =
        !level.isOutsideBuildHeight(y) && !level.isOutsideBuildHeight(y + ceil(dimensions.height).toInt() - 1)

    override fun isLoaded(position: BlockPos): Boolean =
        level.isLoaded(position) && level.isLoaded(position.above(ceil(dimensions.height).toInt()))

    override fun isPassable(position: BlockPos): Boolean {
        val stance = position.stancePosition()
        val box = dimensions.makeBoundingBox(stance).deflate(COLLISION_EPSILON)
        return level.worldBorder.isWithinBounds(box) && level.noCollision(player, box)
    }

    override fun isSupported(position: BlockPos): Boolean {
        val box = dimensions.makeBoundingBox(position.stancePosition())
            .move(0.0, -SUPPORT_DEPTH, 0.0)
        return level.getBlockCollisions(player, box).anyNotEmpty()
    }

    override fun isSurface(position: BlockPos): Boolean = level.canSeeSky(position.above())

    override fun isBedrock(position: BlockPos): Boolean = level.getBlockState(position).block === Blocks.BEDROCK

    override fun isSegmentClear(from: Vec3, to: Vec3): Boolean {
        val distance = from.distanceTo(to)
        val samples = ceil(distance / SWEEP_SAMPLE_DISTANCE).toInt().coerceAtLeast(1)
        val collisionFree = (0..samples).all { index ->
            val point = from.lerp(to, index.toDouble() / samples)
            val node = BlockPos.containing(point)
            isLoaded(node) && isPassableAt(point)
        }
        if (!collisionFree) return false
        if (from.y != to.y) return isSupportedAt(from) && isSupportedAt(to)
        return (0..samples).all { index ->
            isSupportedAt(from.lerp(to, index.toDouble() / samples))
        }
    }

    private fun isPassableAt(point: Vec3): Boolean {
        val box = dimensions.makeBoundingBox(point).deflate(COLLISION_EPSILON)
        return level.worldBorder.isWithinBounds(box) && level.getBlockCollisions(player, box).allEmpty()
    }

    private fun isSupportedAt(point: Vec3): Boolean = level.getBlockCollisions(
        player,
        dimensions.makeBoundingBox(point).move(0.0, -SUPPORT_DEPTH, 0.0),
    ).anyNotEmpty()
}

private class MinecraftInteractableGoalWorld(
    private val level: net.minecraft.client.multiplayer.ClientLevel,
    private val player: Entity,
    private val target: InteractableResolvedTarget,
    private val interactionRange: Double,
) {
    val targetNode: BlockPos?
        get() = when (val lock = target.lock) {
            is InteractableTargetLock.Block -> lock.position.toBlockPos()
            is InteractableTargetLock.ContainerVehicle -> level.getEntity(lock.uuid)?.blockPosition()
        }

    fun canInteract(stance: BlockPos): Boolean {
        val eyes = stance.stancePosition().add(0.0, player.getEyeHeight(Pose.STANDING).toDouble(), 0.0)
        return when (val lock = target.lock) {
            is InteractableTargetLock.Block -> canInteractBlock(eyes, lock.position.toBlockPos())
            is InteractableTargetLock.ContainerVehicle -> canInteractEntity(eyes, level.getEntity(lock.uuid))
        }
    }

    private fun canInteractBlock(eyes: Vec3, position: BlockPos): Boolean {
        val initial = target.initialHitLocation.let { Vec3(it.x, it.y, it.z) }
        return sequenceOf(initial, Vec3.atCenterOf(position)).any { point ->
            eyes.distanceTo(point) <= interactionRange &&
                level.clip(eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
                    .let { it.type == HitResult.Type.BLOCK && it.blockPos == position }
        }
    }

    private fun canInteractEntity(eyes: Vec3, entity: Entity?): Boolean {
        entity ?: return false
        val point = entity.boundingBox.clip(eyes, entity.boundingBox.center).orElse(entity.boundingBox.center)
        if (eyes.distanceTo(point) > interactionRange) return false
        val obstruction = level.clip(eyes, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
        return obstruction.type == HitResult.Type.MISS ||
            obstruction.location.distanceToSqr(eyes) + COLLISION_EPSILON >= point.distanceToSqr(eyes)
    }
}

private fun InteractableSettingsSnapshot.toRouteSettings() = InteractableRouteSettings(
    allowDiagonal = routing.diagonal,
    maxCost = routing.maxCost.toDouble(),
    maxIterations = (routing.nodesPerTick.toLong() * routeTimeoutTicks.toLong())
        .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
    lineOfSightShortcuts = routing.lineOfSightShortcuts,
    surfaceFallback = surfaceFallback.enabled,
    maxRise = surfaceFallback.maxRise,
    horizontalSearch = surfaceFallback.horizontalSearch,
    protectBedrock = surfaceFallback.doNotClipAroundBedrock,
)

private fun BlockPos.stancePosition() = Vec3(x + 0.5, y.toDouble(), z + 0.5)

private const val SUPPORT_DEPTH = 0.05
private const val COLLISION_EPSILON = 1.0E-7
private const val SWEEP_SAMPLE_DISTANCE = 0.25
