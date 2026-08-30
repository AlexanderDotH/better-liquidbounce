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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement



import net.minecraft.world.phys.Vec3

internal const val SPEAR_KILL_MAX_PACKET_RETURN_ATTEMPTS = 3

internal sealed interface SpearKillReturnRecoveryAction {

    data class PacketAttempt(
        val number: Int,
        val authoritativePosition: Vec3,
        val destination: Vec3,
        val checkpoints: List<Vec3>,
    ) : SpearKillReturnRecoveryAction {
        val authoritativeOffset: Vec3
            get() = authoritativePosition.subtract(destination)
    }

    data class PhysicalReset(val position: Vec3) : SpearKillReturnRecoveryAction
}

/**
 * Retains both positions that matter when a virtual SpearKill return is rejected:
 * the original Packet origin and the last physical position reached while fighting.
 */
internal class SpearKillReturnRecoveryTracker(
    private val maxPacketAttempts: Int = SPEAR_KILL_MAX_PACKET_RETURN_ATTEMPTS,
    private val positionEpsilonSquared: Double = SPEAR_KILL_RETURN_POSITION_EPSILON_SQUARED,
) {

    private var originalOrigin: Vec3? = null
    private var combatOrigin: Vec3? = null
    private val pendingConfirmations = ArrayDeque<Vec3>()

    var packetAttempts: Int = 0
        private set

    val recoveryOrigin: Vec3?
        get() = originalOrigin

    init {
        require(maxPacketAttempts > 0) { "Packet recovery attempt count must be positive" }
        require(positionEpsilonSquared.isFinite() && positionEpsilonSquared >= 0.0) {
            "Recovery position epsilon must be finite and non-negative"
        }
    }

    fun begin(origin: Vec3) {
        require(origin.hasFiniteCoordinates()) { "Recovery origin must be finite" }
        originalOrigin = origin
        combatOrigin = origin
        packetAttempts = 0
        pendingConfirmations.clear()
    }

    /** The destination is frozen as soon as the first packet-first recovery begins. */
    fun observeCombatPosition(position: Vec3) {
        if (packetAttempts > 0 || !position.hasFiniteCoordinates()) return
        if (originalOrigin == null) return
        combatOrigin = position
    }

    fun nextAction(authoritativePosition: Vec3): SpearKillReturnRecoveryAction {
        require(authoritativePosition.hasFiniteCoordinates()) { "Authoritative position must be finite" }
        val origin = requireNotNull(originalOrigin) { "Packet recovery has not been initialized" }
        val observedCombatOrigin = combatOrigin ?: origin
        val destination = origin.takeIf {
            it.distanceToSqr(observedCombatOrigin) <= positionEpsilonSquared
        } ?: observedCombatOrigin
        if (packetAttempts >= maxPacketAttempts) {
            pendingConfirmations.clear()
            return SpearKillReturnRecoveryAction.PhysicalReset(destination)
        }

        packetAttempts++
        val checkpoints = distinctRecoveryCheckpoints(origin, destination)
        pendingConfirmations.clear()
        pendingConfirmations.addAll(checkpoints)
        return SpearKillReturnRecoveryAction.PacketAttempt(
            number = packetAttempts,
            authoritativePosition = authoritativePosition,
            destination = destination,
            checkpoints = checkpoints,
        )
    }

    fun consumeArrivalConfirmation(position: Vec3): Vec3? {
        val checkpoint = pendingConfirmations.firstOrNull() ?: return null
        if (checkpoint.distanceToSqr(position) > positionEpsilonSquared) return null
        pendingConfirmations.removeFirst()
        return checkpoint
    }

    fun clear() {
        originalOrigin = null
        combatOrigin = null
        packetAttempts = 0
        pendingConfirmations.clear()
    }

    private fun distinctRecoveryCheckpoints(origin: Vec3, destination: Vec3): List<Vec3> =
        if (origin.distanceToSqr(destination) <= positionEpsilonSquared) {
            listOf(origin)
        } else {
            listOf(origin, destination)
        }
}

/** Composes independently planned legs and rejects any leg that does not end at its checkpoint. */
internal fun buildSpearKillReturnRecoveryMovements(
    authoritativePosition: Vec3,
    checkpoints: List<Vec3>,
    planLeg: (Vec3, Vec3) -> List<Vec3>?,
): List<Vec3>? {
    if (!authoritativePosition.hasFiniteCoordinates() || checkpoints.isEmpty()) return null
    val movements = ArrayList<Vec3>()
    var current = authoritativePosition
    for (checkpoint in checkpoints) {
        if (!checkpoint.hasFiniteCoordinates()) return null
        if (current.distanceToSqr(checkpoint) <= SPEAR_KILL_RETURN_POSITION_EPSILON_SQUARED) {
            current = checkpoint
            continue
        }

        val leg = validatedRecoveryLeg(current, checkpoint, planLeg) ?: return null
        movements.addAll(leg)
        current = checkpoint
    }
    return movements
}

private fun validatedRecoveryLeg(
    from: Vec3,
    checkpoint: Vec3,
    planLeg: (Vec3, Vec3) -> List<Vec3>?,
): List<Vec3>? {
    val leg = planLeg(from, checkpoint) ?: return null
    return leg.takeIf {
        it.isNotEmpty() &&
            it.all { movement -> movement.hasFiniteCoordinates() && movement.lengthSqr() > 0.0 } &&
            it.fold(from, Vec3::add).distanceToSqr(checkpoint) <= SPEAR_KILL_RETURN_POSITION_EPSILON_SQUARED
    }
}

internal fun nextSpearKillRecoveryStallTicks(currentTicks: Int, madeProgress: Boolean): Int {
    require(currentTicks >= 0) { "Recovery stall ticks must not be negative" }
    return if (madeProgress) 0 else currentTicks + 1
}

private const val SPEAR_KILL_RETURN_POSITION_EPSILON_SQUARED = 1.0E-6

private fun Vec3.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
