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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.DirectPacketRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAttackStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillInstantChargeAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillInstantPacketBurst
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.buildSpearKillInstantPacketBurst
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.planning.calculateSpearKillPrimedInstantSessionBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.hasSpearKillRefreshableTerminalDamageWindow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.resolveSpearKillInstantChargeAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.spearKillDirectRouteHitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy.conservativePrimedBudgetMovementProfile
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.hasSpearKillKineticDamageRequirements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.spearKillKineticDamageRequirements
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.KineticWeapon

internal sealed interface SpearKillDirectAttackPreparation {
    data class Ready(
        val plan: DirectPacketRoutePlan,
        val instantBurst: SpearKillInstantPacketBurst?,
        val hitTicks: Int,
        val outboundTickCount: Int,
    ) : SpearKillDirectAttackPreparation

    data class Rejected(
        val stage: String,
        val result: SpearKillAttackStartResult,
    ) : SpearKillDirectAttackPreparation
}

private data class SpearKillDirectChargeRequest(
    val instantBurst: Boolean,
    val ticksUsingItem: Int,
    val delayTicks: Int,
    val damageUseDuration: Int,
    val terminalStepCount: Int,
    val stepWaitTicks: Int,
    val strikeHoldTicks: Int,
    val hitTicks: Int,
)

@Suppress("ReturnCount")
internal fun SpearKillModuleState.prepareDirectPacketAttack(
    request: SpearKillDirectPacketAttackRequest,
): SpearKillDirectAttackPreparation {
    val weapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
        ?: return rejectedDirectAttack("missing-kinetic-component", SpearKillAttackStartResult.RETRY_LATER)
    val requirements = spearKillKineticDamageRequirements(weapon)
        ?: return rejectedDirectAttack("missing-damage-conditions", SpearKillAttackStartResult.REJECTED)
    val plan = calculateDirectPacketRoute(
        request.target,
        request.origin,
        request.distance,
        request.settings,
        request.origin,
    ) ?: return rejectedDirectAttack("route-plan", SpearKillAttackStartResult.BLOCKED)
    if (!hasSpearKillKineticDamageRequirements(plan, requirements)) {
        return rejectedDirectAttack("kinetic-requirements", SpearKillAttackStartResult.REJECTED)
    }
    val burst = createDirectInstantBurst(plan, request)
        ?: if (request.settings.routingMode == SpearKillRoutingMode.INSTANT) {
            return rejectedDirectAttack("instant-packet-budget", SpearKillAttackStartResult.BLOCKED)
        } else {
            null
        }
    if (!hasPrimedDirectSessionBudget(plan, request)) {
        return rejectedDirectAttack("primed-session-budget", SpearKillAttackStartResult.BLOCKED)
    }
    if (!isServerAcceptedSpearKillDirectRoute(request.origin, request.origin, plan.route, request.settings)) {
        return rejectedDirectAttack("server-collision-preflight", SpearKillAttackStartResult.BLOCKED)
    }
    val hitTicks = directPacketAttackHitTicks(plan, request)
    return completeDirectPacketPreparation(plan, request, burst, hitTicks, weapon)
}

private fun SpearKillModuleState.completeDirectPacketPreparation(
    plan: DirectPacketRoutePlan,
    request: SpearKillDirectPacketAttackRequest,
    burst: SpearKillInstantPacketBurst?,
    hitTicks: Int,
    weapon: KineticWeapon,
): SpearKillDirectAttackPreparation {
    val chargeRejection = directPacketChargeRejection(
        player.ticksUsingItem,
        weapon.delayTicks,
        weapon.computeDamageUseDuration(),
        hitTicks,
        plan,
        request,
        burst,
    )
    if (chargeRejection != null) return chargeRejection
    val fallSafetyStarted = beginVirtualFallSafety(plan.route.outboundMovements, request.origin)
    return resolveSpearKillDirectAttackReadiness(plan, burst, hitTicks, fallSafetyStarted)
}

private fun createDirectInstantBurst(
    plan: DirectPacketRoutePlan,
    request: SpearKillDirectPacketAttackRequest,
): SpearKillInstantPacketBurst? = if (request.settings.routingMode == SpearKillRoutingMode.INSTANT) {
    buildSpearKillInstantPacketBurst(plan.route, request.settings.instantMaxPackets)
} else {
    null
}

private fun SpearKillModuleState.hasPrimedDirectSessionBudget(
    plan: DirectPacketRoutePlan,
    request: SpearKillDirectPacketAttackRequest,
): Boolean = !request.settings.primedInstant || calculateSpearKillPrimedInstantSessionBudget(
    plan.route,
    request.settings.priming,
    conservativePrimedBudgetMovementProfile(),
    request.settings.instantMaxPackets,
) != null

private fun directPacketAttackHitTicks(
    plan: DirectPacketRoutePlan,
    request: SpearKillDirectPacketAttackRequest,
): Int = spearKillDirectRouteHitTicks(
    request.settings.routingMode,
    plan.route.outboundTickCount,
    request.settings.stepWaitTicks,
    request.settings.strikeHoldTicks,
)

@Suppress("LongParameterList")
private fun directPacketChargeRejection(
    ticksUsingItem: Int,
    delayTicks: Int,
    damageUseDuration: Int,
    hitTicks: Int,
    plan: DirectPacketRoutePlan,
    request: SpearKillDirectPacketAttackRequest,
    burst: SpearKillInstantPacketBurst?,
): SpearKillDirectAttackPreparation.Rejected? {
    val charge = SpearKillDirectChargeRequest(
        burst != null,
        ticksUsingItem,
        delayTicks,
        damageUseDuration,
        plan.route.terminalBurstSteps.coerceAtLeast(1),
        request.settings.stepWaitTicks,
        request.settings.strikeHoldTicks,
        hitTicks,
    )
    return resolveDirectPacketChargeRejection(charge)
}

private fun resolveDirectPacketChargeRejection(
    charge: SpearKillDirectChargeRequest,
): SpearKillDirectAttackPreparation.Rejected? {
    if (!charge.instantBurst) {
        val valid = hasSpearKillRefreshableTerminalDamageWindow(
            charge.delayTicks,
            charge.damageUseDuration,
            charge.terminalStepCount,
            charge.stepWaitTicks,
            charge.strikeHoldTicks,
        )
        return rejectedDirectAttack("terminal-damage-window", SpearKillAttackStartResult.REJECTED).takeUnless { valid }
    }
    return when (resolveSpearKillInstantChargeAction(
        charge.ticksUsingItem,
        charge.delayTicks,
        charge.damageUseDuration,
        charge.hitTicks,
    )) {
        SpearKillInstantChargeAction.READY -> null
        SpearKillInstantChargeAction.REFRESH ->
            rejectedDirectAttack("instant-charge-refresh", SpearKillAttackStartResult.RETRY_LATER)
        SpearKillInstantChargeAction.INVALID ->
            rejectedDirectAttack("instant-charge-window", SpearKillAttackStartResult.REJECTED)
    }
}

private fun rejectedDirectAttack(stage: String, result: SpearKillAttackStartResult) =
    SpearKillDirectAttackPreparation.Rejected(stage, result)
