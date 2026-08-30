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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.SpearKillChargeDecision
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillTickTargetContext
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.debugSpearKillChanged
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.isSpearUseRequested
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.isUsingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.resolveSpearKillChargeDecision
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.resetAttack
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.KineticWeapon

internal data class SpearKillTickChargeContext(
    val target: SpearKillTickTargetContext,
    val weapon: KineticWeapon,
    val duration: Int,
)

internal fun SpearKillModuleState.prepareSpearKillTickCharge(
    target: SpearKillTickTargetContext,
): SpearKillTickChargeContext? {
    val weapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: run {
        resetAttack()
        return null
    }
    val duration = weapon.computeDamageUseDuration()
    val decision = resolveSpearKillChargeDecision(
        ticksUsingItem = player.ticksUsingItem,
        delayTicks = weapon.delayTicks,
        isUsingSpear = isUsingSpear,
        useRequested = isSpearUseRequested,
    )
    debugSpearKillTickCharge(target, weapon, duration, decision)
    return when (decision) {
        SpearKillChargeDecision.WAIT_FOR_VANILLA -> null
        SpearKillChargeDecision.RESET -> null.also { resetAttack() }
        SpearKillChargeDecision.READY -> SpearKillTickChargeContext(target, weapon, duration)
    }
}

private fun SpearKillModuleState.debugSpearKillTickCharge(
    target: SpearKillTickTargetContext,
    weapon: KineticWeapon,
    duration: Int,
    decision: SpearKillChargeDecision,
) = debugSpearKillChanged(
    channel = "charge-state",
    event = "CHARGE_STATE",
    fingerprint = { listOf(target.target?.first?.id, decision, isUsingSpear, isSpearUseRequested) },
) {
    listOf(
        "tick" to player.tickCount,
        "state" to decision,
        "target_id" to target.target?.first?.id,
        "ticks_using_item" to player.ticksUsingItem,
        "delay_ticks" to weapon.delayTicks,
        "damage_use_duration" to duration,
        "using_spear" to isUsingSpear,
        "use_requested" to isSpearUseRequested,
        "attack_requested" to target.attackRequested,
    )
}
