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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSourceRegistry
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items

internal fun MaceKillModuleState.registerMaceKillPreviewGlow() {
    TargetGlowSourceRegistry.register(::currentPreviewGlow)
}

internal fun MaceKillModuleState.installMaceKillControlRegistries() {
    RemoteKillSetbackRegistry.register(setbackListener)
    MaceClipResearchControlRegistry.install(researchControl)
}

internal val MaceKillModuleState.facadeMaximumTargetRange: Float
    get() = maceKillMaximumTargetRange(
        configuredTargetRange = maxTargetDistance.toDouble(),
        instantRouting = isInstantPacketRoutingConfigured(),
        instantMovementAllowance = movementConfiguration.packet.instant.clearanceHeight.toDouble(),
    ).toFloat()

internal val MaceKillModuleState.facadeOwnsKillAuraRoute: Boolean
    get() = activeRouteOwner == MaceKillRouteOwner.KILL_AURA && activeRouteTarget != null

/** True only while MaceKill still owns packets, not its post-return correction observation window. */
internal val MaceKillModuleState.facadeSuppressesNoFallPackets: Boolean
    get() = routeEngine.ownsMovement && (
        routeSession.active || routeEngine.awaitingStrike || plannedRoutePacket != null ||
            primingPackets.isNotEmpty() || groundingPacketTracker.pendingCount > 0
        )

internal val MaceKillModuleState.acceptsKillAuraDelegation: Boolean
    get() = integration.acceptsKillAuraDelegation

internal fun MaceKillModuleState.canPrepareFightBotMaceUse(target: LivingEntity): Boolean = enabled && running &&
    acceptsKillAuraDelegation && integration.fightBotMacePolicy != MaceUsePolicy.Off &&
    (activeRouteTarget == null || activeRouteTarget === target) && isMaceKillTargetEligible(target)

internal fun MaceKillModuleState.resolveFightBotMaceUseSource(): FightBotMaceUseSource? = selectMaceUseSource(
    policy = integration.fightBotMacePolicy,
    mainHandMace = player.mainHandItem.item == Items.MACE,
    selectedHotbarSlot = SilentHotbar.serversideSlot,
    hotbarMaceSlots = Slots.Hotbar.asSequence()
        .filter { it.itemStack.item == Items.MACE }
        .mapNotNull { it.hotbarIndex }
        .toList(),
)

internal val MaceKillModuleState.facadeKillAuraIntegrationAvailable: Boolean
    get() {
        val admissionFailure = evaluateMaceKillRouteAdmission(currentKillAuraAdmissionContext())
        val available = acceptsKillAuraDelegation && fightBotMaceTarget == null && admissionFailure == null
        if (available) {
            if (debugConsole.isInitialized()) debugConsole.value.clearTransition("kill-aura-admission")
        } else {
            debugMaceKillChanged(
                channel = "kill-aura-admission",
                event = "kill-aura-unavailable",
                fingerprint = {
                    listOf(
                        acceptsKillAuraDelegation,
                        fightBotMaceTarget?.id,
                        admissionFailure,
                        RemoteKillMovementOwnership.currentOwner,
                    )
                },
            ) {
                listOf(
                    "delegation" to acceptsKillAuraDelegation,
                    "fightbot-target" to fightBotMaceTarget?.id,
                    "admission" to admissionFailure,
                    "movement-owner" to RemoteKillMovementOwnership.currentOwner,
                )
            }
        }
        return available
    }

private fun MaceKillModuleState.currentKillAuraAdmissionContext() = MaceKillRouteAdmissionContext(
    enabled = enabled && running,
    routeOwned = activeRouteTarget != null || routeEngine.ownsMovement,
    conflictingMovementOwned = RemoteKillMovementOwnership.active && !routeEngine.ownsMovement,
    blinkRunning = integration.blinkRunning,
    passenger = player.isPassenger,
    gliding = player.isFallFlying,
    backoffActive = routeAdmissionBackoff.isBlocked(player.tickCount) ||
        instantRouteBackoff.isBlocked(player.tickCount) ||
        shouldBlockMaceKillRouteAfterInstantCorrection(
            instantRouting = isInstantPacketRoutingConfigured(),
            instantServerRejected = instantServerRejected,
        ),
    holdingMace = hasServerHeldMace(),
)

internal val MaceKillModuleState.facadeKillAuraIntegrationArmed: Boolean
    get() = isKillAuraIntegrationAvailable && isAttackCooldownReady()

internal val MaceKillModuleState.facadeFightBotRouteTarget: LivingEntity?
    get() = fightBotMaceTarget.takeIf {
        fightBotMaceState == MaceKillFightBotState.RouteActive && activeRouteTarget === it
    }

internal fun MaceKillModuleState.facadeFightBotStateFor(target: LivingEntity): MaceKillFightBotState =
    fightBotMaceState.takeIf { fightBotMaceTarget === target } ?: MaceKillFightBotState.Unavailable

internal fun MaceKillModuleState.facadeReservesFightBotMaceUse(target: LivingEntity?): Boolean = target != null &&
    fightBotMaceTarget === target && fightBotMaceState.reservesKillAuraSubsystems

internal fun MaceKillModuleState.facadeRequestFightBotMaceUse(target: LivingEntity): MaceKillFightBotState {
    admitFightBotMaceTarget(target)?.let { return it }
    fightBotMaceTarget = target
    val existingHotbar = fightBotMaceSource as? FightBotMaceUseSource.Hotbar
    return if (existingHotbar != null) {
        refreshFightBotMaceHotbar(target, existingHotbar)
    } else {
        acquireFightBotMaceSource(target)
    }
}

private fun MaceKillModuleState.admitFightBotMaceTarget(target: LivingEntity): MaceKillFightBotState? {
    if (!canPrepareFightBotMaceUse(target)) {
        if (fightBotMaceTarget === target) beginFightBotTerminal(MaceKillFightBotTerminal.TargetLoss)
        return MaceKillFightBotState.Unavailable
    }
    if (fightBotMaceTarget !== target) {
        if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT) {
            beginFightBotTerminal(MaceKillFightBotTerminal.TargetLoss)
            return fightBotMaceState
        }
        clearFightBotMaceUse(MaceKillFightBotTerminal.TargetLoss)
    }
    return null
}

private fun MaceKillModuleState.refreshFightBotMaceHotbar(
    target: LivingEntity,
    source: FightBotMaceUseSource.Hotbar,
): MaceKillFightBotState {
    if (!isMaceInHotbarSlot(source.slot) ||
        !SilentHotbar.selectSlotSilently(FightBotMaceUseRequester, source.slot, 2)
    ) {
        return rejectFightBotMaceUse(target)
    }
    return activateFightBotMaceState(target)
}

private fun MaceKillModuleState.acquireFightBotMaceSource(target: LivingEntity): MaceKillFightBotState {
    val source = resolveFightBotMaceUseSource() ?: return rejectFightBotMaceUse(target)
    if (source is FightBotMaceUseSource.Hotbar &&
        !SilentHotbar.selectSlotSilently(FightBotMaceUseRequester, source.slot, 2)
    ) {
        return rejectFightBotMaceUse(target)
    }
    fightBotMaceSource = source
    return activateFightBotMaceState(target)
}

private fun MaceKillModuleState.activateFightBotMaceState(target: LivingEntity): MaceKillFightBotState {
    fightBotMaceState = if (activeRouteTarget === target) {
        MaceKillFightBotState.RouteActive
    } else {
        MaceKillFightBotState.Ready
    }
    return fightBotMaceState
}

internal fun MaceKillModuleState.rejectFightBotMaceUse(target: LivingEntity): MaceKillFightBotState {
    if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT) {
        fightBotMaceState = MaceKillFightBotState.Rejected
        beginFightBotTerminal(MaceKillFightBotTerminal.Rejection)
        return fightBotMaceState
    }
    clearFightBotMaceUse(MaceKillFightBotTerminal.Rejection)
    fightBotMaceTarget = target
    fightBotMaceState = MaceKillFightBotState.Rejected
    return fightBotMaceState
}

internal fun MaceKillModuleState.facadeReleaseFightBotMaceUse(
    terminal: MaceKillFightBotTerminal = MaceKillFightBotTerminal.TargetLoss,
) {
    beginFightBotTerminal(terminal)
}

internal fun MaceKillModuleState.facadeCanAcceptKillAuraTarget(target: LivingEntity): Boolean =
    isKillAuraIntegrationAvailable && isMaceKillTargetEligible(target)

internal fun MaceKillModuleState.facadeShouldExcludeKillAuraTarget(target: LivingEntity): Boolean = shouldExcludeMaceKillWaterTarget(
    maceKillEnabled = enabled,
    mainHandMace = player.mainHandItem.item == Items.MACE,
    targetInWater = target.isInWater || target.isSwimming || target.isUnderWater,
)

/** KillAura explicitly transfers one selected target; no attack-key state participates. */
internal fun MaceKillModuleState.facadeRequestKillAuraMaceKill(target: LivingEntity): Boolean {
    if (ownsKillAuraRoute) return activeRouteTarget === target
    if (!isKillAuraIntegrationArmed || integration.killAuraTarget() !== target) return false
    return startRemoteRoute(target, MaceKillRouteOwner.KILL_AURA)
}

internal fun MaceKillModuleState.facadeOnKillAuraDisabled() {
    if (activeRouteOwner == MaceKillRouteOwner.KILL_AURA) abortRemoteRoute()
}
