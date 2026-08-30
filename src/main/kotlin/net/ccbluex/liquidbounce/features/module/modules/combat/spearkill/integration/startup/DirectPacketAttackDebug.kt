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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_DEBUG_ROUTE_PREVIEW_STEPS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugVector
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.reportDirectPacketAttackRejection(
    request: SpearKillDirectPacketAttackRequest,
    rejection: SpearKillDirectAttackPreparation.Rejected,
) {
    debugSpearKill("DIRECT_ROUTE_REJECTED") {
        listOf(
            "tick" to player.tickCount,
            "stage" to rejection.stage,
            "result" to rejection.result,
            "route" to request.routeMode,
            "routing_mode" to request.settings.routingMode,
            "primed" to request.settings.primedInstant,
            "priming" to request.settings.priming,
            "priming_packet_type" to request.settings.primingPacketType,
            "max_packets" to request.settings.instantMaxPackets,
            "requested_distance" to request.distance,
            "origin" to spearKillDebugVector(request.origin),
        ) + spearKillDebugTargetFields(request.target, request.distance) + spearKillDebugSessionFields()
    }
}

internal fun SpearKillModuleState.reportDirectPacketAttackReady(
    request: SpearKillDirectPacketAttackRequest,
    preparation: SpearKillDirectAttackPreparation.Ready,
) {
    val route = preparation.plan.route
    debugSpearKill("DIRECT_ROUTE_READY") {
        listOf(
            "tick" to player.tickCount,
            "route" to request.routeMode,
            "routing_mode" to request.settings.routingMode,
            "primed" to request.settings.primedInstant,
            "outbound_steps" to route.outboundMovements.size,
            "outbound_distance" to route.outboundMovements.sumOf(Vec3::length),
            "outbound_tick_count" to preparation.outboundTickCount,
            "hit_ticks" to preparation.hitTicks,
            "terminal_burst_steps" to route.terminalBurstSteps,
            "strike_hold_ticks" to request.settings.strikeHoldTicks,
            "target_snapshot_position" to spearKillDebugVector(preparation.plan.targetSnapshot.observedPosition),
            "target_snapshot_velocity" to spearKillDebugVector(preparation.plan.targetSnapshot.velocity),
            "movement_preview" to route.outboundMovements.take(SPEAR_KILL_DEBUG_ROUTE_PREVIEW_STEPS)
                .joinToString(prefix = "[", postfix = "]", transform = ::spearKillDebugVector),
        ) + spearKillDebugTargetFields(request.target, request.distance)
    }
}
