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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.phys.Vec3

internal data class SpearKillPreparedSetback(
    val packet: ClientboundPlayerPositionPacket,
    val localState: SpearKillLocalPlayerState,
    val authoritativeOffset: Vec3,
    val physicalReturn: Boolean,
    val sessionOrigin: Vec3 = localState.movement.position,
    val exactRecoveryMovements: List<Vec3>? = null,
)

internal class SpearKillSetbackRollback {

    @Volatile
    private var markedPacket: ClientboundPlayerPositionPacket? = null
    private var preparedSetback: SpearKillPreparedSetback? = null

    val confirming: Boolean
        get() = preparedSetback != null

    fun mark(packet: ClientboundPlayerPositionPacket) {
        markedPacket = packet
    }

    fun isMarked(packet: ClientboundPlayerPositionPacket): Boolean = markedPacket === packet

    fun prepare(
        packet: ClientboundPlayerPositionPacket,
        localState: SpearKillLocalPlayerState,
        guard: SpearKillSetbackGuard,
    ): SpearKillPreparedSetback? = prepare(packet, localState, guard, physicalReturn = false)

    fun prepare(
        packet: ClientboundPlayerPositionPacket,
        localState: SpearKillLocalPlayerState,
        guard: SpearKillSetbackGuard,
        physicalReturn: Boolean,
        sessionOrigin: Vec3 = localState.movement.position,
        exactRecoveryMovementsFor: (Vec3) -> List<Vec3>? = { null },
    ): SpearKillPreparedSetback? {
        if (markedPacket !== packet) return null
        markedPacket = null
        if (guard.localRestoreFor(localState.movement, packet) == null) return null

        val correctedState = PositionMoveRotation.calculateAbsolute(
            localState.movement,
            packet.change,
            packet.relatives,
        )
        val authoritativeOffset = correctedState.position.subtract(sessionOrigin)
        return SpearKillPreparedSetback(
            packet,
            localState,
            authoritativeOffset,
            physicalReturn,
            sessionOrigin,
            exactRecoveryMovementsFor(authoritativeOffset),
        ).also { preparedSetback = it }
    }

    fun finish(packet: ClientboundPlayerPositionPacket): SpearKillPreparedSetback? {
        val setback = preparedSetback?.takeIf { it.packet === packet } ?: return null
        preparedSetback = null
        return setback
    }

    fun clear() {
        markedPacket = null
        preparedSetback = null
    }
}
