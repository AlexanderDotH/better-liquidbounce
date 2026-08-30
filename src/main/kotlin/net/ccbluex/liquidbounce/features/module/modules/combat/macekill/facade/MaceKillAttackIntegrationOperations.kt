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
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.*
import net.ccbluex.liquidbounce.utils.entity.*
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.*
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.*
import net.minecraft.world.entity.player.*
import net.minecraft.world.item.*
import net.minecraft.world.phys.*

internal fun MaceKillModuleState.handleAcceptedAttack(attackPlayer: Player, target: Entity): MaceKillAttackResult {
    val localPlayer = attackPlayer as? LocalPlayer ?: return MaceKillAttackResult.NOT_APPLIED
    if (!canHandleAcceptedMaceAttack(localPlayer)) {
        return MaceKillAttackResult.NOT_APPLIED
    }
    val remoteIntent = remoteStrikeTarget === target
    val endpoint = remoteStrikeEndpoint.takeIf { remoteIntent } ?: localPlayer.position()
    if (remoteIntent && !isRemoteEndpointReady(localPlayer, target, endpoint)) {
        return MaceKillAttackResult.REJECTED
    }
    if (remoteIntent && remoteStrikeFallResetPlan == null) return MaceKillAttackResult.REJECTED
    val plan = planAcceptedMaceStrike(localPlayer, endpoint, remoteIntent)
        ?: return if (remoteIntent) MaceKillAttackResult.REJECTED else MaceKillAttackResult.NOT_APPLIED
    applyMaceStrikePackets(localPlayer, plan.packets)
    return MaceKillAttackResult.APPLIED
}

private fun MaceKillModuleState.canHandleAcceptedMaceAttack(localPlayer: LocalPlayer): Boolean =
    enabled && running && localPlayer === mc.player && hasServerHeldMace()

private fun MaceKillModuleState.planAcceptedMaceStrike(
    localPlayer: LocalPlayer,
    endpoint: Vec3,
    remoteIntent: Boolean,
): MaceInstantStrikePlan? = (MaceInstantStrikePlanner.plan(
        MaceInstantStrikeRequest(
            physicalPosition = localPlayer.position(),
            physicalBoundingBox = localPlayer.boundingBox,
            virtualEndpoint = endpoint,
            maximumFallHeight = fallHeight,
            endpointOnGround = !remoteIntent && localPlayer.onGround(),
        ),
    ) { box -> world.getBlockCollisions(localPlayer, box).allEmpty() }
    as? MaceInstantStrikePlanResult.Ready)?.plan

internal fun MaceKillModuleState.applyMaceStrikePackets(
    localPlayer: LocalPlayer,
    packets: List<MaceInstantStrikePacket>,
) {
    applyingStrikePackets = true
    try {
        packets.forEach { packet ->
            when (packet) {
                is MaceInstantStrikePacket.StatusOnly -> localPlayer.warp(null, packet.onGround)
                is MaceInstantStrikePacket.Position -> localPlayer.warp(packet.position, packet.onGround)
            }
        }
    } finally {
        applyingStrikePackets = false
    }
}

internal fun MaceKillModuleState.routeOwnerFor(target: LivingEntity, manualLaunch: Boolean): MaceKillRouteOwner? = when {
    fightBotMaceTarget === target && fightBotMaceState == MaceKillFightBotState.Ready ->
        MaceKillRouteOwner.FIGHT_BOT
    manualLaunch && isMaceKillActivationSatisfied(
        activationMode = activationMode,
        attackHeld = mc.options.keyAttack.isPressedOnAny,
        manualAttackRequested = true,
    ) -> MaceKillRouteOwner.MANUAL
    else -> null
}

internal fun MaceKillModuleState.hasServerHeldMace(): Boolean = player.mainHandItem.item == Items.MACE ||
    (fightBotMaceSource as? FightBotMaceUseSource.Hotbar)?.let { source ->
        SilentHotbar.serversideSlot == source.slot && isMaceInHotbarSlot(source.slot)
    } == true

internal fun MaceKillModuleState.isMaceInHotbarSlot(slot: Int): Boolean = Slots.Hotbar.asSequence()
    .firstOrNull { it.hotbarIndex == slot }
    ?.itemStack
    ?.item == Items.MACE

internal fun MaceKillModuleState.isAttackCooldownReady(): Boolean =
    player.getAttackStrengthScale(0.5f) >= MACE_KILL_MIN_ATTACK_STRENGTH
