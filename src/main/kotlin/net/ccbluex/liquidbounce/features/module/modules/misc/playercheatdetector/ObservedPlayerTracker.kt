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
package net.ccbluex.liquidbounce.features.module.modules.misc.playercheatdetector

import net.ccbluex.liquidbounce.utils.math.center
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.UUID

class ObservedPlayerTracker {

    private val framesByPlayerId = linkedMapOf<UUID, ObservedMovementFrame>()
    private val playerIdsByEntityId = hashMapOf<Int, UUID>()

    fun sample(
        players: Iterable<Player>,
        localPlayer: Player?,
        maxTrackedPlayers: Int,
        canObserve: (RemotePlayer) -> Boolean,
    ): List<ObservedMovementFrame> {
        val limit = maxTrackedPlayers.coerceAtLeast(0)
        val frames = ArrayList<ObservedMovementFrame>(limit.coerceAtMost(32))

        for (candidate in players) {
            if (frames.size >= limit) {
                break
            }

            val remotePlayer = candidate as? RemotePlayer ?: continue
            if (remotePlayer === localPlayer || !canObserve(remotePlayer)) {
                continue
            }

            val frame = sample(remotePlayer)
            frames += frame
            framesByPlayerId[remotePlayer.uuid] = frame
            playerIdsByEntityId[remotePlayer.id] = remotePlayer.uuid
        }

        trim(maxTrackedPlayers)
        return frames
    }

    fun actionFromVelocity(entityId: Int, vector: Vec3, tick: Int): ObservedActionFrame? {
        val frame = frameByEntityId(entityId) ?: return null
        return frame.toAction(ObservedActionType.VELOCITY, tick, vector = vector)
    }

    fun actionFromDamage(victimEntityId: Int, attackerEntityId: Int, tick: Int): ObservedActionFrame? {
        val attacker = frameByEntityId(attackerEntityId) ?: return null
        val target = frameByEntityId(victimEntityId) ?: return null

        return attacker.toAction(
            ObservedActionType.DAMAGE,
            tick,
            targetId = target.playerId,
            targetName = target.playerName,
            targetBoundingBox = target.boundingBox,
            targetPosition = target.position,
        )
    }

    fun actionFromBlockUpdate(blockPos: BlockPos, state: BlockState, tick: Int): ObservedActionFrame? {
        val frame = nearestFrame(blockPos.center, maxDistance = 7.0) ?: return null
        val type = if (state.isAir) ObservedActionType.BLOCK_BREAK else ObservedActionType.BLOCK_PLACE

        return frame.toAction(type, tick, blockPos = blockPos)
    }

    fun removeEntity(entityId: Int): UUID? {
        val playerId = playerIdsByEntityId.remove(entityId) ?: return null
        framesByPlayerId.remove(playerId)
        return playerId
    }

    fun reset() {
        framesByPlayerId.clear()
        playerIdsByEntityId.clear()
    }

    private fun sample(player: RemotePlayer): ObservedMovementFrame {
        val previous = framesByPlayerId[player.uuid]
        val position = player.position()
        val delta = previous?.position?.let(position::subtract) ?: Vec3.ZERO
        val nearGroundBox = player.boundingBox.inflate(-0.001, 0.0, -0.001).move(0.0, -0.08, 0.0)

        return ObservedMovementFrame(
            playerId = player.uuid,
            playerName = player.gameProfile.name,
            entityId = player.id,
            tick = player.tickCount,
            position = position,
            previousPosition = previous?.position,
            delta = delta,
            boundingBox = player.boundingBox,
            eyeY = player.eyeY,
            yaw = player.yRot,
            pitch = player.xRot,
            onGround = player.onGround(),
            nearGround = !player.level().noCollision(player, nearGroundBox),
            inFluid = player.isInWater,
            swimming = player.isSwimming,
            fallFlying = player.isFallFlying,
            passenger = player.isPassenger,
            sprinting = player.isSprinting,
            crouching = player.isCrouching,
            hurtTime = player.hurtTime,
            swingTime = player.swingTime,
            teleportLike = delta.length() > TELEPORT_DISTANCE,
        )
    }

    private fun frameByEntityId(entityId: Int): ObservedMovementFrame? {
        val playerId = playerIdsByEntityId[entityId] ?: return null
        return framesByPlayerId[playerId]
    }

    private fun nearestFrame(position: Vec3, maxDistance: Double): ObservedMovementFrame? {
        val maxDistanceSq = maxDistance * maxDistance
        var nearestFrame: ObservedMovementFrame? = null
        var nearestDistanceSq = maxDistanceSq

        for (frame in framesByPlayerId.values) {
            val distanceSq = frame.position.distanceToSqr(position)
            if (distanceSq <= nearestDistanceSq) {
                nearestDistanceSq = distanceSq
                nearestFrame = frame
            }
        }

        return nearestFrame
    }

    private fun ObservedMovementFrame.toAction(
        type: ObservedActionType,
        tick: Int,
        vector: Vec3? = null,
        targetId: UUID? = null,
        targetName: String? = null,
        targetBoundingBox: net.minecraft.world.phys.AABB? = null,
        targetPosition: Vec3? = null,
        blockPos: BlockPos? = null,
    ) = ObservedActionFrame(
        playerId = playerId,
        playerName = playerName,
        entityId = entityId,
        tick = tick,
        type = type,
        position = position,
        eyeY = eyeY,
        vector = vector,
        targetId = targetId,
        targetName = targetName,
        targetBoundingBox = targetBoundingBox,
        targetPosition = targetPosition,
        blockPos = blockPos,
    )

    private fun trim(maxTrackedPlayers: Int) {
        while (framesByPlayerId.size > maxTrackedPlayers) {
            val oldestPlayerId = framesByPlayerId.keys.first()
            val oldest = framesByPlayerId.remove(oldestPlayerId)
            if (oldest != null) {
                playerIdsByEntityId.remove(oldest.entityId)
            }
        }
    }

    private companion object {
        const val TELEPORT_DISTANCE = 8.0
    }
}
