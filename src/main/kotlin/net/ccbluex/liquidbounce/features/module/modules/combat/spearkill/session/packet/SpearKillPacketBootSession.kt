/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.buildSpearKillFixedStepMovements as buildSpearKillAStarFixedStepMovements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.spearKillPacketTravelTicks as spearKillAStarPacketTravelTicks

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillRouteSession

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.IdentityHashMap

internal const val SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS = 2
internal const val SPEAR_KILL_PACKET_MAX_PRE_STRIKE_HOLD_TICKS = 1

internal fun buildSpearKillAttackMovements(
    direction: Vec3,
    distance: Double,
    maxSpeed: Double,
): List<Vec3> {
    val outbound = buildSpearKillFixedStepMovements(direction, distance, maxSpeed)
    val inbound = outbound.asReversed().map { it.scale(-1.0) }

    return buildList(outbound.size + inbound.size + 1) {
        addAll(outbound)
        addAll(inbound)
        add(Vec3.ZERO)
    }
}

/** Splits one SpearKill movement into maximum-sized Packet steps followed by an exact remainder. */
internal fun buildSpearKillFixedStepMovements(
    direction: Vec3,
    distance: Double,
    maxSpeed: Double,
): List<Vec3> = buildSpearKillAStarFixedStepMovements(direction, distance, maxSpeed)

/** Includes the configured idle ticks between Packet steps when predicting a moving target. */
internal fun spearKillPacketTravelTicks(stepCount: Int, stepWaitTicks: Int): Int =
    spearKillAStarPacketTravelTicks(stepCount, stepWaitTicks)

internal fun applySpearKillVirtualPosition(
    packet: ServerboundMovePlayerPacket,
    playerPosition: Vec3,
    virtualOffset: Vec3,
    grounded: Boolean = false,
    heading: Rotation? = null,
) {
    val virtualPosition = playerPosition.add(virtualOffset)
    packet.x = virtualPosition.x
    packet.y = virtualPosition.y
    packet.z = virtualPosition.z
    packet.hasPos = true
    packet.onGround = grounded
    applySpearKillPathHeading(packet, heading)
}

/** Returns the silent packet heading needed for a kinetic spear to read [movement] as forward speed. */
internal fun spearKillKineticHeading(movement: Vec3): Rotation? = movement
    .takeIf {
        it.x.isFinite() && it.y.isFinite() && it.z.isFinite() &&
            it.lengthSqr() >= SPEAR_KILL_PACKET_EPSILON
    }
    ?.let(Rotation::fromRotationVec)

/** Overrides ambient camera rotation with the direction of SpearKill's active path segment. */
internal fun applySpearKillPathHeading(packet: ServerboundMovePlayerPacket, heading: Rotation?) {
    heading ?: return
    packet.yRot = heading.yaw
    packet.xRot = heading.pitch
    packet.hasRot = true
}

/** Keeps the final kinetic lunge intact instead of letting a camera packet reset its server-side speed. */
internal fun shouldSuppressSpearKillStrikeHoldPacket(holdingStrike: Boolean): Boolean = holdingStrike

/** Prevents the server from replacing the terminal lunge velocity with zero before damage is sampled. */
internal fun shouldSuppressSpearKillKineticResetPacket(
    holdingStrike: Boolean,
    clientTickEndPacket: Boolean,
): Boolean = holdingStrike && clientTickEndPacket

/** Only the selected movement packet may carry a pending virtual step. */
internal fun spearKillPacketVirtualOffset(
    carriesPendingStep: Boolean,
    committedOffset: Vec3,
    pendingOffset: Vec3,
): Vec3 = if (carriesPendingStep) pendingOffset else committedOffset

internal fun shouldProtectSpearKillFallDamage(
    fallDistance: Double,
    verticalVelocity: Double,
    safeFallDistance: Double,
    tickCount: Int,
): Boolean = tickCount > 20 && fallDistance - verticalVelocity > safeFallDistance

/** Keeps a fall-damage spoof bound to the exact SpearKill movement packet that carries it. */
internal class SpearKillFallDamagePacketTracker {

    private val protectedPackets = Collections.synchronizedMap(
        IdentityHashMap<ServerboundMovePlayerPacket, Unit>(),
    )

    fun protect(packet: ServerboundMovePlayerPacket) {
        protectedPackets[packet] = Unit
        packet.onGround = true
    }

    /** Restores the owned ground bit after lower-priority packet objections changed it. */
    fun reassertGround(packet: ServerboundMovePlayerPacket): Boolean {
        if (!protectedPackets.containsKey(packet)) return false

        packet.onGround = true
        return true
    }

    fun confirmFinalState(packet: ServerboundMovePlayerPacket, cancelled: Boolean): Boolean {
        if (protectedPackets.remove(packet) == null) return false

        return !cancelled && packet.onGround
    }

    fun clear() {
        protectedPackets.clear()
    }
}

/**
 * Tracks SpearKill's packet displacement and confirmed physical return positions.
 * A movement is removed only after the corresponding packet passed the packet pipeline.
 */
internal class SpearKillPacketBootSession(
    internal val state: SpearKillPacketSessionPort,
) : RemoteKillRouteSession by state

private const val SPEAR_KILL_PACKET_EPSILON = 1.0E-12
