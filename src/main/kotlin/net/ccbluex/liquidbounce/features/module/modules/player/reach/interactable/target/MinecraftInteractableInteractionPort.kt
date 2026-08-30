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

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableInteractionPort
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableResolvedInteraction
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableRuntimeTarget
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.requiresSecondaryUse
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.resolveInteractableBlockHit
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.interactBlock
import net.ccbluex.liquidbounce.utils.entity.interactEntity
import net.ccbluex.liquidbounce.utils.raytracing.clip
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

internal object MinecraftInteractableInteractionPort : InteractableInteractionPort {
    override fun resolve(
        target: InteractableRuntimeTarget,
        eyePosition: Vec3,
        interactionRange: Double,
    ): InteractableResolvedInteraction? {
        val resolvedTarget = target as InteractableResolvedTarget
        return when (val lock = resolvedTarget.lock) {
            is InteractableTargetLock.Block -> resolveBlockInteraction(
                position = lock.position.toBlockPos(),
                initialHit = resolvedTarget.initialHitLocation.let { Vec3(it.x, it.y, it.z) },
                eyePosition = eyePosition,
                interactionRange = interactionRange,
            )
            is InteractableTargetLock.ContainerVehicle -> resolveEntityInteraction(
                uuid = lock.uuid,
                kind = lock.kind,
                eyePosition = eyePosition,
                interactionRange = interactionRange,
            )
        }
    }
}

private fun resolveBlockInteraction(
    position: BlockPos,
    initialHit: Vec3,
    eyePosition: Vec3,
    interactionRange: Double,
): InteractableResolvedInteraction? {
    val level = mc.level ?: return null
    val player = mc.player ?: return null
    val hit = resolveInteractableBlockHit(
        level,
        player,
        position,
        initialHit,
        eyePosition,
        interactionRange,
    ) ?: return null
    return InteractableResolvedInteraction(hit.location) { hand ->
        interactBlock(hit, hand, SwingMode.DO_NOT_HIDE)
    }
}

private fun resolveEntityInteraction(
    uuid: UUID,
    kind: InteractableEntityKind,
    eyePosition: Vec3,
    interactionRange: Double,
): InteractableResolvedInteraction? {
    val level = mc.level ?: return null
    val player = mc.player ?: return null
    val entity = level.getEntity(uuid) ?: return null
    val point = entity.boundingBox.clip(eyePosition, entity.boundingBox.center).orElse(entity.boundingBox.center)
    if (eyePosition.distanceTo(point) > interactionRange) return null
    val obstruction = level.clip(eyePosition, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
    if (obstruction.type != HitResult.Type.MISS &&
        obstruction.location.distanceToSqr(eyePosition) < point.distanceToSqr(eyePosition)
    ) {
        return null
    }
    val hit = EntityHitResult(entity, point)
    return InteractableResolvedInteraction(point) { hand ->
        withSecondaryUse(player, requiresSecondaryUse(kind)) {
            interactEntity(entity, hit, hand, SwingMode.DO_NOT_HIDE)
        }
    }
}

private inline fun <T> withSecondaryUse(player: Player, required: Boolean, action: () -> T): T {
    if (!required || player.isShiftKeyDown) return action()
    player.setShiftKeyDown(true)
    return try {
        action()
    } finally {
        player.setShiftKeyDown(false)
    }
}
