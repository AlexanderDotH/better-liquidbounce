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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.SpearKillMovementAssistMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.canStartSpearKillElytraFlight
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.holdingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.isUseInputHeld
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.isUsingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.nextSpearKillHoldUseLaunchTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.nextSpearKillManualAttackRequestLatch
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.physicalAttackRequest
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items

internal fun SpearKillModuleState.canStartSpearKillElytraFlight(): Boolean {
    val chestItem = player.getItemBySlot(EquipmentSlot.CHEST)
    return canStartSpearKillElytraFlight(
        isFallFlying = player.isFallFlying,
        hasFlyingAbility = player.abilities.flying,
        isPassenger = player.isPassenger,
        isOnClimbable = player.onClimbable(),
        isInWater = player.isInWater,
        hasLevitation = player.hasEffect(MobEffects.LEVITATION),
        isOnGround = player.onGround(),
        hasUsableElytra = chestItem.`is`(Items.ELYTRA) && !chestItem.nextDamageWillBreak(),
    )
}

internal fun SpearKillModuleState.requestSpearKillPacketFallFlight() {
    if (elytraWhileMoving != SpearKillMovementAssistMode.PACKET ||
        player.isFallFlying || !canStartSpearKillElytraFlight()
    ) {
        return
    }

    player.startFallFlying()
    network.send(
        ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING),
    )
}

internal fun SpearKillModuleState.updateManualAttackRequestLatch() {
    manualAttackRequestLatched = nextSpearKillManualAttackRequestLatch(
        activationMode = activationMode,
        holdingSpear = holdingSpear,
        isUsingSpear = isUsingSpear,
        useInputHeld = isUseInputHeld,
        wasLatched = manualAttackRequestLatched,
        attackPressed = physicalAttackRequest,
    )
}

internal fun SpearKillModuleState.updateHoldUseLaunchCycle(
    launchStarted: Boolean = false,
    launchedTarget: LivingEntity? = null,
) {
    holdUseLaunchTarget = nextSpearKillHoldUseLaunchTarget(
        activationMode = activationMode,
        holdingSpear = holdingSpear,
        useInputHeld = isUseInputHeld,
        currentTarget = holdUseLaunchTarget,
        launchedTarget = launchedTarget,
        launchStarted = launchStarted,
    )
}
