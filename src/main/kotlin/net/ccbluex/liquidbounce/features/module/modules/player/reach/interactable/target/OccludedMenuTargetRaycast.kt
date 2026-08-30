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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.EnderChestBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

internal fun occludedMenuBlockHit(
    level: ClientLevel,
    player: Entity,
    start: Vec3,
    end: Vec3,
): BlockHitResult? {
    val context = ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
    val hit = BlockGetter.traverseBlocks(start, end, context, { rayContext, position ->
        if (!level.isLoaded(position)) return@traverseBlocks null
        val state = level.getBlockState(position)
        if (!isPotentialOccludedMenuTarget(
                hasMenuProvider = state.getMenuProvider(level, position) != null,
                opensMenuWithoutProvider = state.block is EnderChestBlock,
                isChest = state.block is ChestBlock,
            )
        ) {
            return@traverseBlocks null
        }
        level.clipWithInteractionOverride(
            rayContext.from,
            rayContext.to,
            position,
            rayContext.getBlockShape(state, level, position),
            state,
        )
    }, { rayContext ->
        BlockHitResult.miss(
            rayContext.to,
            Direction.getApproximateNearest(rayContext.from.subtract(rayContext.to)),
            BlockPos.containing(rayContext.to),
        )
    })
    return hit.takeIf { it.type == HitResult.Type.BLOCK }
}

internal fun isPotentialOccludedMenuTarget(
    hasMenuProvider: Boolean,
    opensMenuWithoutProvider: Boolean,
    isChest: Boolean,
): Boolean = hasMenuProvider || opensMenuWithoutProvider || isChest

internal fun InteractableBlockPosition.toBlockPos() = BlockPos(x, y, z)

internal fun BlockPos.toTargetPosition() = InteractableBlockPosition(x, y, z)

internal fun Vec3.toTargetPoint() = InteractableTargetPoint(x, y, z)

internal fun Block.toTargetBlockKey() = InteractableBlockKey(BuiltInRegistries.BLOCK.getKey(this).toString())
