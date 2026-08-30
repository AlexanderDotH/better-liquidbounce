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



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/** Recognizes short-lived server corrections to positions that Packet SpearKill actually delivered. */
internal class SpearKillSetbackGuard(
    private val guardTicks: Int = DEFAULT_GUARD_TICKS,
) {

    private val recentVirtualPositions = ArrayDeque<Vec3>()
    private var remainingTicks = 0

    val armed: Boolean
        get() = remainingTicks > 0 && recentVirtualPositions.isNotEmpty()

    init {
        require(guardTicks > 0) { "Setback guard duration must be positive" }
    }

    fun record(serverPosition: Vec3, localPosition: Vec3) {
        if (serverPosition.distanceToSqr(localPosition) <= POSITION_EPSILON_SQUARED) return

        recordRecognizedPosition(localPosition)
        recordRecognizedPosition(serverPosition)
        remainingTicks = guardTicks
    }

    private fun recordRecognizedPosition(position: Vec3) {
        if (recentVirtualPositions.any { it.distanceToSqr(position) <= POSITION_EPSILON_SQUARED }) return
        if (recentVirtualPositions.size == MAX_RECENT_POSITIONS) recentVirtualPositions.removeFirst()
        recentVirtualPositions += position
    }

    fun tick(pathActive: Boolean) {
        if (!armed) return
        if (pathActive) {
            remainingTicks = guardTicks
            return
        }

        remainingTicks--
        if (remainingTicks == 0) clear()
    }

    fun localRestoreFor(
        localState: PositionMoveRotation,
        correction: ClientboundPlayerPositionPacket,
    ): PositionMoveRotation? {
        if (!armed) return null

        val correctedState = PositionMoveRotation.calculateAbsolute(
            localState,
            correction.change,
            correction.relatives,
        )
        val matchesVirtualPosition = recentVirtualPositions.any {
            it.distanceToSqr(correctedState.position) <= POSITION_EPSILON_SQUARED
        }
        return localState.takeIf { matchesVirtualPosition }
    }

    fun clear() {
        recentVirtualPositions.clear()
        remainingTicks = 0
    }

    private companion object {
        const val DEFAULT_GUARD_TICKS = 40
        const val MAX_RECENT_POSITIONS = 512
        const val POSITION_EPSILON_SQUARED = 1.0E-6
    }
}

internal data class SpearKillLocalPlayerState(
    val movement: PositionMoveRotation,
    val oldPosition: Vec3,
    val oldYRot: Float,
    val oldXRot: Float,
) {

    fun restore(player: Player) {
        player.setPos(movement.position)
        player.deltaMovement = movement.deltaMovement
        player.yRot = movement.yRot
        player.xRot = movement.xRot
        player.setOldPosAndRot(oldPosition, oldYRot, oldXRot)
    }

    companion object {
        fun capture(player: Player) = SpearKillLocalPlayerState(
            movement = PositionMoveRotation.of(player),
            oldPosition = player.oldPosition(),
            oldYRot = player.yRotO,
            oldXRot = player.xRotO,
        )
    }
}
