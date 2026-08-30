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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop
import net.ccbluex.liquidbounce.features.rotation.PostRotationExecutor
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.interactEntity
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.npc.villager.AbstractVillager
import net.minecraft.world.item.Items
import net.minecraft.world.item.SpawnEggItem
import kotlin.math.sqrt

internal fun AutoShopVanillaMode.acquireTarget(tick: Int): AbstractVillager? {
    val localPlayer = mc.player ?: return null
    val canAcquire = MerchantAcquisitionPolicy.canAcquire(
        tick = tick,
        suppressedUntilTick = suppressAcquisitionUntilTick,
        guiOpen = mc.gui.screen() != null,
        inventoryMenuActive = localPlayer.containerMenu === localPlayer.inventoryMenu,
        safeHandAvailable = safeInteractionHand() != null,
        hasActiveRule = tradeFilters.get().any { it.isActive },
        interactionInputActive = interactionInputActive(),
    )
    if (!canAcquire) return null

    val level = mc.level ?: return null
    val reachSetting = reach.get()
    val candidates = level.getEntitiesOfClass(
        AbstractVillager::class.java,
        localPlayer.boundingBox.inflate(reachSetting.range.toDouble()),
    ).map { merchant -> merchant.targetCandidate(localPlayer) }
    val selected = MerchantTargetSelector.selectReachable(
        candidates,
        reachSetting.range,
        canRetry = { session.canRetry(it, tick) },
    ) { merchant ->
        raytraceBox(
            eyes = localPlayer.eyePosition,
            box = merchant.boundingBox,
            range = reachSetting.range.toDouble(),
            wallsRange = reachSetting.wallRange.toDouble(),
        ) != null
    } ?: return null

    return selected.entity.takeIf { session.tryLock(selected.entityId, tick) }
}

private fun AbstractVillager.targetCandidate(localPlayer: LocalPlayer) = MerchantTargetCandidate(
    entity = this,
    entityId = id,
    boxedDistance = sqrt(boundingBox.distanceToSqr(localPlayer.eyePosition)),
    visible = false,
    alive = isAlive && !isRemoved,
    adult = !isBaby,
    sleeping = isSleeping,
)

internal fun AutoShopVanillaMode.requestInteractionRotation(
    target: AbstractVillager,
    spot: RotationWithVector,
) {
    val targetId = target.id
    val whenReached = RestrictedSingleUseAction(
        { canOpenAfterRotation(targetId) },
        { queueInteraction(targetId) },
    )
    RotationManager.setRotationTarget(
        rotations.toRotationTarget(
            spot.rotation,
            entity = target,
            considerInventory = false,
            whenReached = whenReached,
        ),
        Priority.IMPORTANT_FOR_USAGE_1,
        ModuleAutoShop,
    )
}

private fun AutoShopVanillaMode.canOpenAfterRotation(targetId: Int): Boolean {
    if (interactionInputActive()) return false
    val target = merchant(targetId) ?: return false
    val spot = rotationSpot(target) ?: return false
    val angleDifference = RotationManager.serverRotation.directionAngleTo(spot.rotation)
    return MerchantRotationGate.canInteract(session.state, targetId, angleDifference, AIM_THRESHOLD)
}

private fun AutoShopVanillaMode.queueInteraction(targetId: Int) {
    PostRotationExecutor.addTask(ModuleAutoShop, postMove = true, priority = true) {
        openMerchant(targetId)
    }
}

private fun AutoShopVanillaMode.openMerchant(targetId: Int) {
    val localPlayer = mc.player ?: return
    if (yieldToUserInteraction(localPlayer.tickCount)) return

    val target = merchant(targetId)
        ?: return finishSession(MerchantSessionEndCause.TARGET_LOST, localPlayer.tickCount)
    val spot = eligibleRotationSpot(target)
    if (mc.gui.screen() != null || spot == null) {
        finishSession(MerchantSessionEndCause.TARGET_LOST, localPlayer.tickCount)
        return
    }
    if (RotationManager.serverRotation.directionAngleTo(spot.rotation) > AIM_THRESHOLD) return

    val hand = safeInteractionHand()
        ?: return finishSession(MerchantSessionEndCause.TRADE_BLOCKED, localPlayer.tickCount)
    if (!session.markInteractionSent(targetId, localPlayer.tickCount)) return

    sendingOwnedInteraction = true
    val result = try {
        interactEntity(
            target,
            net.minecraft.world.phys.EntityHitResult(target, spot.vec),
            hand,
            SwingMode.DO_NOT_HIDE,
        )
    } finally {
        sendingOwnedInteraction = false
    }
    if (result?.consumesAction() != true) {
        finishSession(MerchantSessionEndCause.TRADE_BLOCKED, localPlayer.tickCount)
    }
}

internal fun AutoShopVanillaMode.lockedTargetIsValid(): Boolean {
    val target = session.targetId?.let(::merchant) ?: return false
    return eligibleRotationSpot(target) != null
}

internal fun AutoShopVanillaMode.eligibleRotationSpot(target: AbstractVillager): RotationWithVector? {
    if (!target.isAlive || target.isRemoved || target.isBaby || target.isSleeping) return null
    return rotationSpot(target)
}

private fun AutoShopVanillaMode.rotationSpot(target: AbstractVillager): RotationWithVector? {
    val localPlayer = mc.player ?: return null
    val reachSetting = reach.get()
    return raytraceBox(
        eyes = localPlayer.eyePosition,
        box = target.boundingBox,
        range = reachSetting.range.toDouble(),
        wallsRange = reachSetting.wallRange.toDouble(),
    )
}

private fun AutoShopVanillaMode.safeInteractionHand(): InteractionHand? {
    val localPlayer = mc.player ?: return null
    return InteractionHand.entries.firstOrNull { localPlayer.getItemInHand(it).isEmpty }
        ?: InteractionHand.entries.firstOrNull { hand ->
            val item = localPlayer.getItemInHand(hand).item
            item !== Items.NAME_TAG && item !is SpawnEggItem
        }
}

internal fun AutoShopVanillaMode.merchant(entityId: Int): AbstractVillager? =
    mc.level?.getEntity(entityId) as? AbstractVillager

private const val AIM_THRESHOLD = 2f
