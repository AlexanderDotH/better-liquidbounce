/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.runtime

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractablePacketInstruction
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.interactableSweepWaypoints
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableCorrectionDecision
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableMovement
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

internal fun MinecraftReachInteractableRuntime.prepareCorrection(
    packet: ClientboundPlayerPositionPacket,
    player: Player,
) {
    if (!session.movementLeaseRequired) return
    val serverAnchor = session.serverAnchorPosition ?: player.position()
    val local = PositionMoveRotation(serverAnchor, player.deltaMovement, player.yRot, player.xRot)
    val authoritative = PositionMoveRotation.calculateAbsolute(local, packet.change, packet.relatives).position
    state.correctionContext = CorrectionContext(packet, authoritative, session.origin, player.deltaMovement)
    player.setPos(serverAnchor)
}

internal fun MinecraftReachInteractableRuntime.finishCorrection(
    packet: ClientboundPlayerPositionPacket,
    player: Player,
) {
    val context = state.correctionContext?.takeIf { it.packet === packet } ?: return
    state.correctionContext = null
    val recovery = boundedCorrectionRecovery(context.authoritative)
    val decision = session.corrected(context.authoritative, recovery, player.tickCount)
    executeEffects(decision.effects())
    if (decision is InteractableCorrectionDecision.Recovering) {
        context.visualOrigin?.let(player::setPos)
        player.deltaMovement = context.visualVelocity
    }
    controller.reconcileOwnership()
}

private fun MinecraftReachInteractableRuntime.boundedCorrectionRecovery(
    authoritative: Vec3,
): List<InteractableMovement<InteractablePacketInstruction>>? {
    val settings = state.activeSettings ?: return null
    val origin = session.origin ?: return null
    if (authoritative.distanceTo(origin) > settings.routing.maxCost) return null
    val level = net.ccbluex.liquidbounce.utils.client.mc.level ?: return null
    val player = net.ccbluex.liquidbounce.utils.client.mc.player ?: return null
    val world = CorrectionRouteWorld(level, player)
    if (!world.isClear(authoritative, origin)) return null
    return interpolatePositions(authoritative, origin, settings.routing.stepDistance).map { position ->
        InteractableMovement(
            InteractablePacketInstruction.Position(position, fullPacket = false, onGround = true),
            position,
        )
    }
}

internal fun InteractablePacketInstruction.isSafeToSend(
    runtime: MinecraftReachInteractableRuntime,
    from: Vec3,
    position: Vec3,
): Boolean {
    if (this is InteractablePacketInstruction.Status) return true
    val level = net.ccbluex.liquidbounce.utils.client.mc.level ?: return false
    val player = net.ccbluex.liquidbounce.utils.client.mc.player ?: return false
    if (this is InteractablePacketInstruction.Position && !collisionChecked) {
        val routeWorld = CorrectionRouteWorld(level, player)
        return runtime.isSafeVClip(level, player, from, position) &&
            (!requiresStandableEndpoint || routeWorld.isStandable(position))
    }
    return CorrectionRouteWorld(level, player).isClear(from, position)
}

private fun MinecraftReachInteractableRuntime.isSafeVClip(
    level: ClientLevel,
    player: Entity,
    from: Vec3,
    to: Vec3,
): Boolean {
    if (kotlin.math.abs(from.x - to.x) > RUNTIME_POSITION_EPSILON ||
        kotlin.math.abs(from.z - to.z) > RUNTIME_POSITION_EPSILON
    ) {
        return false
    }
    val minimumY = floor(minOf(from.y, to.y)).toInt()
    val maximumY = ceil(maxOf(from.y, to.y)).toInt()
    val clearanceHeight = ceil(player.getDimensions(Pose.STANDING).height).toInt()
    val protectBedrock = state.activeSettings?.surfaceFallback?.doNotClipAroundBedrock == true
    for (y in minimumY..maximumY) {
        val position = BlockPos.containing(from.x, y.toDouble(), from.z)
        val top = position.above(clearanceHeight - 1)
        if (level.isOutsideBuildHeight(y) || level.isOutsideBuildHeight(top.y) ||
            !level.isLoaded(position) || !level.isLoaded(top)
        ) {
            return false
        }
        if (protectBedrock && level.getBlockState(position).block === Blocks.BEDROCK) return false
    }
    return level.worldBorder.isWithinBounds(BlockPos.containing(from)) &&
        level.worldBorder.isWithinBounds(BlockPos.containing(to))
}

private fun interpolatePositions(from: Vec3, to: Vec3, stepDistance: Double): List<Vec3> {
    val distance = from.distanceTo(to)
    if (distance <= 1.0E-9) return emptyList()
    val count = ceil(distance / stepDistance).toInt().coerceAtLeast(1)
    return (1..count).map { index -> if (index == count) to else from.lerp(to, index.toDouble() / count) }
}

private class CorrectionRouteWorld(
    private val level: ClientLevel,
    private val player: Entity,
) {
    private val dimensions = player.getDimensions(Pose.STANDING)

    fun isClear(from: Vec3, to: Vec3): Boolean {
        val sweep = listOf(from) + interactableSweepWaypoints(from, to)
        if (!sweep.zipWithNext().all { (start, end) ->
                interpolatePositions(start, end, SWEEP_STEP).all(::isPassable)
            }
        ) {
            return false
        }
        if (from.y != to.y) return isStandable(from) && isStandable(to)
        return interpolatePositions(from, to, SWEEP_STEP).all(::isStandable)
    }

    fun isStandable(position: Vec3): Boolean {
        if (!isPassable(position)) return false
        val box = dimensions.makeBoundingBox(position).deflate(1.0E-7)
        return level.getBlockCollisions(player, box.move(0.0, -SUPPORT_DEPTH, 0.0)).any { !it.isEmpty }
    }

    private fun isPassable(position: Vec3): Boolean {
        val node = BlockPos.containing(position)
        if (!level.isLoaded(node) || level.isOutsideBuildHeight(node.y)) return false
        val box = dimensions.makeBoundingBox(position).deflate(1.0E-7)
        return level.worldBorder.isWithinBounds(box) && level.noCollision(player, box)
    }

    private companion object {
        const val SUPPORT_DEPTH = 0.05
        const val SWEEP_STEP = 0.25
    }
}

private const val RUNTIME_POSITION_EPSILON = 1.0E-6
