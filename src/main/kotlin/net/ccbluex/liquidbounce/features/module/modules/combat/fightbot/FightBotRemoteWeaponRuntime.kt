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
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items

internal fun FightBotRuntime.handleTargetUpdate() {
    if (!combatOperational || player.isDeadOrDying || player.isSpectator) {
        val spearTerminal = if (player.isDeadOrDying) {
            SpearKillFightBotTerminal.Death
        } else {
            SpearKillFightBotTerminal.TargetLoss
        }
        val maceTerminal = if (player.isDeadOrDying) {
            FightBotMaceTerminal.Death
        } else {
            FightBotMaceTerminal.TargetLoss
        }
        clearTargetAndWeapons(spearTerminal, maceTerminal)
        return
    }

    updateTarget()
    val target = targetTracker.target
    currentTargetHandoff = target?.let(FightBotTargetHandoff::Locked) ?: FightBotTargetHandoff.Idle
    if (target == null || target.squaredBoxedDistanceTo(player) <= combat.extendedInteractionRange.sq()) {
        remoteWeapons.releaseSpearUse(SpearKillFightBotTerminal.TargetLoss)
        remoteWeapons.releaseMaceUse(FightBotMaceTerminal.TargetLoss)
        return
    }
    requestRemoteWeaponUse(target)
}

private fun FightBotRuntime.updateTarget() {
    val routeTarget = selectFightBotRouteTarget(
        maceRouteTarget = remoteWeapons.maceRouteTarget,
        spearRouteTarget = remoteWeapons.spearRouteTarget,
    )
    if (routeTarget != null && targetTracker.validate(routeTarget)) {
        targetTracker.target = routeTarget
        return
    }
    targetTracker.target = selectFightBotTarget(
        mode = targetTracker.mode,
        configuredName = targetTracker.configuredName,
        candidates = targetTracker.targets(),
        nameOf = { (it as? Player)?.gameProfile?.name },
        distanceOf = { player.squaredBoxedDistanceTo(it) },
        isEligible = { true },
    )
}

private fun FightBotRuntime.requestRemoteWeaponUse(target: LivingEntity) {
    val maceSource = resolveMaceUseSource()
    val spearSource = resolveSpearUseSource()
    when (selectFightBotRemoteWeapon(
        maceSource = maceSource,
        spearSource = spearSource,
        maceRouteActive = remoteWeapons.maceStateFor(target) == FightBotMaceState.RouteActive,
        spearRouteActive = remoteWeapons.spearStateFor(target) == SpearKillFightBotState.RouteActive,
    )) {
        FightBotRemoteWeapon.Mace -> requestMaceUse(target)
        FightBotRemoteWeapon.Spear -> requestSpearUse(target)
        null -> releaseRemoteWeapons()
    }
}

private fun FightBotRuntime.requestMaceUse(target: LivingEntity) {
    remoteWeapons.releaseSpearUse(SpearKillFightBotTerminal.TargetLoss)
    if (!remoteWeapons.maceStateFor(target).retainsRejectedTarget) {
        remoteWeapons.requestMaceUse(target)
    }
}

private fun FightBotRuntime.requestSpearUse(target: LivingEntity) {
    remoteWeapons.releaseMaceUse(FightBotMaceTerminal.TargetLoss)
    remoteWeapons.requestSpearUse(target)
}

private fun FightBotRuntime.releaseRemoteWeapons() {
    remoteWeapons.releaseMaceUse(FightBotMaceTerminal.TargetLoss)
    remoteWeapons.releaseSpearUse(SpearKillFightBotTerminal.TargetLoss)
}

private fun FightBotRuntime.resolveMaceUseSource(): FightBotMaceUseSource? {
    if (!remoteWeapons.maceRunning) return null
    return selectFightBotMaceUseSource(
        automation = settings.maceAutomation,
        mainHandMace = player.mainHandItem.item === Items.MACE,
        selectedHotbarSlot = SilentHotbar.serversideSlot,
        hotbarMaceSlots = Slots.Hotbar.asSequence()
            .filter { it.itemStack.item === Items.MACE }
            .mapNotNull { it.hotbarIndex }
            .toList(),
    )
}

private fun FightBotRuntime.resolveSpearUseSource(): FightBotSpearUseSource? {
    if (!remoteWeapons.spearRunning) return null
    return selectFightBotSpearUseSource(
        automation = settings.spearAutomation,
        mainHandSpear = player.mainHandItem.isSpear,
        offhandSpear = player.offhandItem.isSpear,
        selectedHotbarSlot = SilentHotbar.serversideSlot,
        hotbarSpearSlots = Slots.Hotbar.asSequence()
            .filter { it.itemStack.isSpear }
            .mapNotNull { it.hotbarIndex }
            .toList(),
    )
}

internal fun FightBotRuntime.clearTargetAndWeapons(
    spearTerminal: SpearKillFightBotTerminal,
    maceTerminal: FightBotMaceTerminal,
) {
    targetTracker.reset()
    currentTargetHandoff = FightBotTargetHandoff.Idle
    remoteWeapons.releaseSpearUse(spearTerminal)
    remoteWeapons.releaseMaceUse(maceTerminal)
}
