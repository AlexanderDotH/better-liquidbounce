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

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target

import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.raytracing.clip
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySelector
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.entity.vehicle.boat.ChestBoat
import net.minecraft.world.entity.vehicle.boat.ChestRaft
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Minecraft-facing boundary for the otherwise pure target policy. */
internal object MinecraftInteractableTargetWorldAdapter : InteractableTargetWorldAdapter {

    override fun raycast(maxRange: Double): InteractableRayHit {
        val level = mc.level ?: return InteractableRayHit.WorldUnavailable
        val player = mc.player ?: return InteractableRayHit.WorldUnavailable
        val start = player.eyePosition
        val direction = (RotationManager.currentRotation ?: player.rotation).directionVector
        val blockHit = level.clip(
            from = start,
            to = start.add(direction.scale(maxRange)),
            block = ClipContext.Block.OUTLINE,
            fluid = ClipContext.Fluid.NONE,
            entity = player,
        )
        val blockDistanceSquared = blockHit.location.distanceToSqr(start)
        val entityHit = visibleEntityHit(player, start, direction, blockHit, blockDistanceSquared, maxRange)

        if (entityHit != null && entityHit.location.distanceToSqr(start) < blockDistanceSquared) {
            return entityRayHit(level, entityHit, start)
        }
        return blockRayHit(level, blockHit, start)
    }

    override fun observe(lock: InteractableTargetLock): InteractableTargetObservation {
        val level = mc.level ?: return InteractableTargetObservation.WorldUnavailable
        return when (lock) {
            is InteractableTargetLock.Block -> blockObservation(level, lock.position.toBlockPos())
            is InteractableTargetLock.ContainerVehicle -> {
                val entity = level.getEntity(lock.uuid) ?: return InteractableTargetObservation.Missing
                entityObservation(level, entity)
            }
        }
    }

    private fun visibleEntityHit(
        player: Entity,
        start: Vec3,
        direction: Vec3,
        blockHit: BlockHitResult,
        blockDistanceSquared: Double,
        maxRange: Double,
    ): EntityHitResult? {
        val cappedRange = if (blockHit.type == HitResult.Type.MISS) maxRange else kotlin.math.sqrt(blockDistanceSquared)
        val end = start.add(direction.scale(cappedRange))
        val searchBox = player.boundingBox.expandTowards(direction.scale(cappedRange)).inflate(ENTITY_SEARCH_MARGIN)
        return ProjectileUtil.getEntityHitResult(
            player,
            start,
            end,
            searchBox,
            EntitySelector.CAN_BE_PICKED,
            minOf(maxRange * maxRange, blockDistanceSquared),
        )
    }

    private fun blockRayHit(
        level: ClientLevel,
        hit: BlockHitResult,
        start: Vec3,
    ): InteractableRayHit {
        if (hit.type != HitResult.Type.BLOCK) return InteractableRayHit.Miss
        return InteractableRayHit.Block(
            observation = blockObservation(level, hit.blockPos),
            hitLocation = hit.location.toTargetPoint(),
            distanceSquared = hit.location.distanceToSqr(start),
            visible = true,
        )
    }

    private fun entityRayHit(
        level: ClientLevel,
        hit: EntityHitResult,
        start: Vec3,
    ) = InteractableRayHit.Entity(
        observation = entityObservation(level, hit.entity),
        hitLocation = hit.location.toTargetPoint(),
        distanceSquared = hit.location.distanceToSqr(start),
        visible = true,
    )

    private fun blockObservation(
        level: ClientLevel,
        position: BlockPos,
    ): InteractableTargetObservation.Block {
        val targetPosition = position.toTargetPosition()
        val insideWorldBorder = level.worldBorder.isWithinBounds(position)
        if (!level.isLoaded(position)) {
            return InteractableTargetObservation.Block(
                targetPosition, null, loaded = false, insideWorldBorder, menuProviderAvailable = false,
                blocked = false,
            )
        }

        val state = level.getBlockState(position)
        val identity = state.toTargetIdentity()
        if (!level.isCompleteChestLoaded(position, state)) {
            return InteractableTargetObservation.Block(
                targetPosition, identity, loaded = false, insideWorldBorder, menuProviderAvailable = false,
                blocked = false,
            )
        }

        val blocked = level.isBlockedChest(position, state)
        return InteractableTargetObservation.Block(
            position = targetPosition,
            identity = identity,
            loaded = true,
            insideWorldBorder = insideWorldBorder,
            menuProviderAvailable = !blocked && state.getMenuProvider(level, position) != null,
            blocked = blocked,
        )
    }

    private fun entityObservation(
        level: ClientLevel,
        entity: Entity,
    ) = InteractableTargetObservation.Entity(
        uuid = entity.uuid,
        kind = entity.interactableKind(),
        alive = entity.isAlive,
        removed = entity.isRemoved,
        loaded = level.getEntity(entity.uuid) === entity,
        insideWorldBorder = level.worldBorder.isWithinBounds(entity.blockPosition()),
    )

    private fun ClientLevel.isCompleteChestLoaded(position: BlockPos, state: BlockState): Boolean {
        if (state.block !is ChestBlock || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return true
        return isLoaded(ChestBlock.getConnectedBlockPos(position, state))
    }

    private fun ClientLevel.isBlockedChest(position: BlockPos, state: BlockState): Boolean {
        if (state.block !is ChestBlock) return false
        if (ChestBlock.isChestBlockedAt(this, position)) return true
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return false
        return ChestBlock.isChestBlockedAt(this, ChestBlock.getConnectedBlockPos(position, state))
    }

    private fun Entity.interactableKind(): InteractableEntityKind = when {
        this is AbstractMinecartContainer &&
            (type === EntityTypes.CHEST_MINECART || type === EntityTypes.HOPPER_MINECART) -> {
            InteractableEntityKind.CONTAINER_MINECART
        }
        this is ChestBoat -> InteractableEntityKind.CHEST_BOAT
        this is ChestRaft -> InteractableEntityKind.CHEST_RAFT
        else -> InteractableEntityKind.UNSUPPORTED
    }

    private fun BlockState.toTargetIdentity() = InteractableBlockIdentity(
        blockKey = block.toTargetBlockKey(),
        stateKey = InteractableBlockStateKey(Block.getId(this)),
    )

    private const val ENTITY_SEARCH_MARGIN = 1.0
}

internal fun InteractableBlockPosition.toBlockPos() = BlockPos(x, y, z)

internal fun BlockPos.toTargetPosition() = InteractableBlockPosition(x, y, z)

internal fun Vec3.toTargetPoint() = InteractableTargetPoint(x, y, z)

internal fun Block.toTargetBlockKey() = InteractableBlockKey(BuiltInRegistries.BLOCK.getKey(this).toString())
