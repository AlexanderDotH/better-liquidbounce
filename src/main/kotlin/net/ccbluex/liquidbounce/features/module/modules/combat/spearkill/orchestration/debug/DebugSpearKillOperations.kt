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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.target.isSafeSpearKillCombatTarget

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillMovementOwnership
import net.ccbluex.liquidbounce.utils.entity.lastPos
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Locale

internal fun SpearKillModuleState.debugSpearKill(
    event: String,
    fields: () -> List<Pair<String, Any?>>,
) {
    debugConsole.value.log(event, fields)
}

internal fun SpearKillModuleState.debugSpearKillChanged(
    channel: String,
    event: String,
    fingerprint: () -> Any?,
    fields: () -> List<Pair<String, Any?>>,
) {
    debugConsole.value.logChanged(channel, event, fingerprint, fields)
}

internal fun SpearKillModuleState.spearKillDebugTargetFields(
    target: LivingEntity?,
    knownDistance: Double? = null,
    prefix: String = "target",
): List<Pair<String, Any?>> {
    if (target == null) return listOf("${prefix}_id" to null)

    val position = target.position()
    return listOf(
        "${prefix}_id" to target.id,
        "${prefix}_uuid" to target.uuid,
        "${prefix}_name" to target.scoreboardName.ifBlank { "entity-${target.id}" },
        "${prefix}_type" to target.type,
        "${prefix}_alive" to target.isAlive,
        "${prefix}_removed" to target.isRemoved,
        "${prefix}_same_world" to (target.level() === world),
        "${prefix}_attackable" to target.isSafeSpearKillCombatTarget(),
        "${prefix}_rejected" to isSpearKillTargetRejected(target),
        "${prefix}_health" to target.health,
        "${prefix}_max_health" to target.maxHealth,
        "${prefix}_distance" to (knownDistance ?: player.position().distanceTo(position)),
        "${prefix}_position" to spearKillDebugVector(position),
        "${prefix}_velocity" to spearKillDebugVector(target.deltaMovement),
        "${prefix}_tick_delta" to spearKillDebugVector(position.subtract(target.lastPos)),
        "${prefix}_line_of_sight" to hasLineOfSight(player.eyePosition, target.eyePosition, player),
        "${prefix}_box" to spearKillDebugBox(target.boundingBox),
    )
}

internal fun SpearKillModuleState.spearKillDebugSessionFields(): List<Pair<String, Any?>> = listOf(
    "local_position" to spearKillDebugVector(player.position()),
    "local_velocity" to spearKillDebugVector(player.deltaMovement),
    "local_on_ground" to player.onGround(),
    "local_fall_distance" to player.fallDistance,
    "remote_movement_owner" to RemoteKillMovementOwnership.currentOwner,
    "spear_engine_owns_movement" to remoteKillRouteEngine.ownsMovement,
    "packet_session_active" to packetBootSession.active,
    "packet_recovering" to packetBootSession.recovering,
    "packet_holding_pre_strike" to packetBootSession.state.holdingPreStrike,
    "packet_holding_strike" to packetBootSession.state.holdingStrike,
    "packet_requires_delivery" to packetBootSession.requiresDelivery,
    "packet_pending_outbound" to packetBootSession.pendingOutboundStep,
    "packet_committed_offset" to spearKillDebugVector(packetBootSession.committedOffset),
    "packet_virtual_offset" to spearKillDebugVector(packetBootSession.virtualOffset),
    "planned_packet" to (plannedPacket != null),
    "awaiting_vanilla_packet" to awaitingVanillaMovementPacket,
    "owned_packets_tick" to ownedMovementPacketsThisTick,
    "primed_packets_session" to primedSessionPacketsDelivered,
    "routing_mode" to packetSessionSettings?.routingMode,
    "primed" to packetSessionSettings?.primedInstant,
)

internal fun SpearKillModuleState.spearKillDebugVector(vector: Vec3): String = String.format(
    Locale.ROOT,
    "(%.3f,%.3f,%.3f)",
    vector.x,
    vector.y,
    vector.z,
)

internal fun SpearKillModuleState.spearKillDebugBox(box: AABB): String = String.format(
    Locale.ROOT,
    "[(%.3f,%.3f,%.3f)->(%.3f,%.3f,%.3f)]",
    box.minX,
    box.minY,
    box.minZ,
    box.maxX,
    box.maxY,
    box.maxZ,
)
