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
@file:JvmName("MinecraftInteractableGoalAdapterKt")

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteStance
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableResolvedTarget
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetLock
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.toBlockPos
import net.ccbluex.liquidbounce.utils.raytracing.clip
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

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
    return candidateNodes(targetNode, radius)
        .map { node -> InteractableRouteStance(node, node.stancePosition()) }
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

internal class MinecraftInteractableGoalWorld(
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
        return resolveInteractableBlockHit(level, player, position, initial, eyes, interactionRange) != null
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

internal fun resolveInteractableBlockHit(
    level: net.minecraft.client.multiplayer.ClientLevel,
    player: Entity,
    position: BlockPos,
    initialHit: Vec3,
    eyes: Vec3,
    interactionRange: Double,
): net.minecraft.world.phys.BlockHitResult? {
    val state = level.getBlockState(position)
    val outline = interactionOutlinePoints(position, state.getShape(level, position).toAabbs())
    return sequenceOf(initialHit, Vec3.atCenterOf(position)).plus(outline)
        .distinct()
        .filter { eyes.distanceTo(it) <= interactionRange }
        .map { point -> level.clip(eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player) }
        .firstOrNull { it.type == HitResult.Type.BLOCK && it.blockPos == position }
}

internal fun BlockPos.stancePosition() = Vec3(x + 0.5, y.toDouble(), z + 0.5)
