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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.IdentityHashMap

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
): List<Vec3> {
    require(distance.isFinite() && distance >= 0.0) { "Distance must be finite and non-negative" }
    require(maxSpeed.isFinite() && maxSpeed > 0.0) { "Maximum speed must be finite and positive" }

    val normalizedDirection = direction.normalize()
    var remaining = normalizedDirection.scale(distance)

    return buildList {
        do {
            val remainingLength = remaining.length()
            if (remainingLength <= maxSpeed) {
                add(remaining)
                return@buildList
            }

            var step = remaining.scale(maxSpeed / remainingLength)
            val stepLength = step.length()
            if (stepLength > maxSpeed) {
                // Floating point rounding can make a geometrically exact step one ULP too large.
                step = step.scale(Math.nextDown(maxSpeed) / stepLength)
            }
            add(step)
            remaining = remaining.subtract(step)
        } while (true)
    }
}

/** Includes the configured idle ticks between Packet steps when predicting a moving target. */
internal fun spearKillPacketTravelTicks(stepCount: Int, stepWaitTicks: Int): Int {
    require(stepCount > 0) { "Packet travel must contain at least one step" }
    require(stepWaitTicks in 0..SPEAR_KILL_MAX_WAIT_TICKS) { "Packet wait must be in the configured range" }
    return stepCount + (stepCount - 1) * stepWaitTicks
}

internal fun applySpearKillVirtualPosition(
    packet: ServerboundMovePlayerPacket,
    playerPosition: Vec3,
    virtualOffset: Vec3,
    heading: Rotation? = null,
) {
    val virtualPosition = playerPosition.add(virtualOffset)
    packet.x = virtualPosition.x
    packet.y = virtualPosition.y
    packet.z = virtualPosition.z
    packet.hasPos = true
    packet.onGround = isSpearKillGrounded(packet.isOnGround, virtualOffset)
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

internal fun isSpearKillGrounded(wasOnGround: Boolean, virtualOffset: Vec3): Boolean =
    wasOnGround && virtualOffset.y == 0.0

/** Keeps the final kinetic lunge intact instead of letting a camera packet reset its server-side speed. */
internal fun shouldSuppressSpearKillAStarStrikeHoldPacket(
    packetAStarAttackActive: Boolean,
    holdingStrike: Boolean,
): Boolean = packetAStarAttackActive && holdingStrike

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
@Suppress("TooManyFunctions")
internal class SpearKillPacketBootSession {

    private val movements = ArrayDeque<Vec3>()
    private val committedMovements = ArrayDeque<Vec3>()
    private var pendingOffset: Vec3? = null
    private var pendingStepIsOutbound = false
    private var pendingStepIsPhysicalReturn = false
    private var pendingPhysicalPositionOffset: Vec3? = null
    private var remainingOutboundSteps = 0
    private var remainingStrikeHoldTicks = 0
    private var remainingPreStrikeHoldTicks = 0
    private var remainingStepWaitTicks = 0
    private var holdingStrikeThisTick = false
    private var holdingPreStrikeThisTick = false
    private var preStrikeHoldPending = false
    private var configuredStrikeHoldTicks = 0
    private var configuredPreStrikeHoldTicks = 0
    private var configuredStepWaitTicks = 0
    private var physicalReturnEnabled = false
    private var physicalReturnStarted = false
    private var lastDeliveredMovement: Vec3? = null

    var committedOffset: Vec3 = Vec3.ZERO
        private set

    var recovering: Boolean = false
        private set

    val active: Boolean
        get() = movements.isNotEmpty() || pendingOffset != null || remainingStrikeHoldTicks > 0 ||
            remainingPreStrikeHoldTicks > 0 || remainingStepWaitTicks > 0 ||
            pendingPhysicalPositionOffset != null

    val virtualOffset: Vec3
        get() = pendingOffset ?: committedOffset

    val requiresDelivery: Boolean
        get() = pendingOffset != null

    val pendingOutboundStep: Boolean
        get() = pendingOffset != null && pendingStepIsOutbound

    val pendingMovement: Vec3?
        get() = pendingOffset?.subtract(committedOffset)

    /** Pending movement wins; between steps the last delivered direction remains authoritative. */
    val pathHeading: Rotation?
        get() = (pendingMovement ?: lastDeliveredMovement)?.let(::spearKillKineticHeading)

    /** True from the final outbound confirmation until both strike-hold ticks have been consumed. */
    val holdingStrike: Boolean
        get() = remainingStrikeHoldTicks > 0 || holdingStrikeThisTick

    /** True while ambient movement packets must not collapse into the terminal kinetic lunge. */
    val holdingKineticBarrier: Boolean
        get() = holdingStrike || remainingPreStrikeHoldTicks > 0 || holdingPreStrikeThisTick

    val canReplaceRemainingOutbound: Boolean
        get() = !recovering && remainingOutboundSteps > 0 && pendingOffset == null

    val physicalReturnConfigured: Boolean
        get() = physicalReturnEnabled

    fun start(
        path: List<Vec3>,
        outboundSteps: Int = 0,
        strikeHoldTicks: Int = 0,
        stepWaitTicks: Int = 0,
        preStrikeHoldTicks: Int = 0,
    ) = startInternal(path, outboundSteps, strikeHoldTicks, stepWaitTicks, preStrikeHoldTicks, physicalReturn = false)

    fun startPhysicalReturn(
        path: List<Vec3>,
        outboundSteps: Int,
        strikeHoldTicks: Int = 0,
        stepWaitTicks: Int = 0,
        preStrikeHoldTicks: Int = 0,
    ) = startInternal(path, outboundSteps, strikeHoldTicks, stepWaitTicks, preStrikeHoldTicks, physicalReturn = true)

    /**
     * Atomically replaces only movement that has not entered the packet pipeline yet.
     * Confirmed outbound deltas are retained and appended to the exact inverse return.
     */
    fun replaceRemainingOutbound(
        outboundMovements: List<Vec3>,
        strikeHoldTicks: Int,
    ): Boolean {
        if (!canReplaceRemainingOutbound || strikeHoldTicks < 0 ||
            outboundMovements.isEmpty() || outboundMovements.any { !it.isFinite() || it.lengthSqr() < EPSILON }
        ) {
            return false
        }

        movements.clear()
        movements.addAll(outboundMovements)
        outboundMovements.asReversed().forEach { movements += it.scale(-1.0) }
        committedMovements.asReversed().forEach { movements += it.scale(-1.0) }
        movements += Vec3.ZERO
        remainingOutboundSteps = outboundMovements.size
        remainingStrikeHoldTicks = 0
        holdingStrikeThisTick = false
        configuredStrikeHoldTicks = strikeHoldTicks
        physicalReturnStarted = false
        pendingPhysicalPositionOffset = null
        return true
    }

    private fun startInternal(
        path: List<Vec3>,
        outboundSteps: Int,
        strikeHoldTicks: Int,
        stepWaitTicks: Int,
        preStrikeHoldTicks: Int,
        physicalReturn: Boolean,
    ) {
        check(!active && committedOffset.lengthSqr() < EPSILON) { "A PacketBoot session is already active" }
        require(outboundSteps >= 0) { "Outbound step count must not be negative" }
        require(strikeHoldTicks >= 0) { "Strike hold duration must not be negative" }
        require(preStrikeHoldTicks >= 0) { "Pre-strike hold duration must not be negative" }
        require(stepWaitTicks in 0..SPEAR_KILL_MAX_WAIT_TICKS) { "Step wait duration is outside the configured range" }
        require(outboundSteps <= path.count { it.lengthSqr() >= EPSILON }) {
            "Outbound step count must not exceed movement count"
        }

        movements.addAll(path)
        committedMovements.clear()
        remainingOutboundSteps = outboundSteps
        remainingPreStrikeHoldTicks = 0
        remainingStepWaitTicks = 0
        holdingStrikeThisTick = false
        holdingPreStrikeThisTick = false
        preStrikeHoldPending = outboundSteps > 0 && preStrikeHoldTicks > 0
        configuredStrikeHoldTicks = strikeHoldTicks
        configuredPreStrikeHoldTicks = preStrikeHoldTicks
        configuredStepWaitTicks = stepWaitTicks
        physicalReturnEnabled = physicalReturn
        physicalReturnStarted = false
        lastDeliveredMovement = null
        recovering = false
    }

    fun prepareNextStep(): Vec3? {
        pendingOffset?.let { return it }
        holdingStrikeThisTick = false
        holdingPreStrikeThisTick = false
        return when {
            remainingStrikeHoldTicks > 0 -> {
                remainingStrikeHoldTicks--
                holdingStrikeThisTick = true
                null
            }
            remainingStepWaitTicks > 0 -> {
                remainingStepWaitTicks--
                null
            }
            remainingPreStrikeHoldTicks > 0 -> {
                remainingPreStrikeHoldTicks--
                holdingPreStrikeThisTick = true
                null
            }
            preStrikeHoldPending && remainingOutboundSteps == 1 -> {
                preStrikeHoldPending = false
                remainingPreStrikeHoldTicks = configuredPreStrikeHoldTicks - 1
                holdingPreStrikeThisTick = true
                null
            }
            else -> {
                val movement = movements.firstOrNull()
                if (movement == null || movement.lengthSqr() < EPSILON) {
                    if (movement != null) {
                        movements.removeFirst()
                        finishRecoveryIfComplete()
                    }
                    null
                } else {
                    pendingStepIsOutbound = remainingOutboundSteps > 0
                    pendingStepIsPhysicalReturn = physicalReturnStarted && !pendingStepIsOutbound
                    committedOffset.add(movement).also { pendingOffset = it }
                }
            }
        }
    }

    fun confirmStep(delivered: Boolean) {
        val pending = pendingOffset ?: return
        if (!delivered) {
            pendingOffset = null
            pendingStepIsOutbound = false
            pendingStepIsPhysicalReturn = false
            return
        }

        val completedPhysicalReturnStep = pendingStepIsPhysicalReturn
        val movement = pending.subtract(committedOffset)
        committedOffset = pending
        committedMovements += movement
        lastDeliveredMovement = movement
        movements.removeFirst()
        pendingOffset = null
        if (pendingStepIsOutbound) {
            remainingOutboundSteps--
            if (remainingOutboundSteps == 0) {
                remainingStrikeHoldTicks = configuredStrikeHoldTicks
                recovering = true
                if (physicalReturnEnabled) {
                    physicalReturnStarted = true
                    pendingPhysicalPositionOffset = committedOffset
                }
            }
        } else if (completedPhysicalReturnStep) {
            pendingPhysicalPositionOffset = committedOffset
        }
        pendingStepIsOutbound = false
        pendingStepIsPhysicalReturn = false

        while (movements.firstOrNull()?.lengthSqr()?.let { it < EPSILON } == true) {
            movements.removeFirst()
        }
        remainingStepWaitTicks = if (movements.firstOrNull()?.lengthSqr()?.let { it >= EPSILON } == true) {
            configuredStepWaitTicks
        } else {
            0
        }
        finishRecoveryIfComplete()
    }

    /**
     * Returns the confirmed absolute offset that the local player should adopt during a physical
     * return. Outbound and cancelled packets never produce an update.
     */
    fun consumePhysicalPositionOffset(): Vec3? = pendingPhysicalPositionOffset.also {
        pendingPhysicalPositionOffset = null
    }

    fun beginRecovery(maxSpeed: Double) {
        require(maxSpeed.isFinite() && maxSpeed > 0.0) { "Maximum speed must be finite and positive" }
        pendingOffset = null
        pendingStepIsOutbound = false
        pendingStepIsPhysicalReturn = false
        remainingOutboundSteps = 0
        remainingStrikeHoldTicks = 0
        remainingPreStrikeHoldTicks = 0
        remainingStepWaitTicks = 0
        holdingStrikeThisTick = false
        holdingPreStrikeThisTick = false
        preStrikeHoldPending = false
        configuredStrikeHoldTicks = 0
        configuredPreStrikeHoldTicks = 0
        configuredStepWaitTicks = 0
        movements.clear()

        if (committedOffset.lengthSqr() < EPSILON) {
            clear()
            return
        }

        movements.addAll(buildSpearKillFixedStepMovements(
            direction = committedOffset.scale(-1.0),
            distance = committedOffset.length(),
            maxSpeed = maxSpeed,
        ))
        physicalReturnStarted = physicalReturnEnabled
        if (physicalReturnStarted) {
            pendingPhysicalPositionOffset = committedOffset
        }
        recovering = true
    }

    fun beginExactReturn() {
        if (recovering) return

        pendingOffset = null
        pendingStepIsOutbound = false
        pendingStepIsPhysicalReturn = false
        remainingOutboundSteps = 0
        remainingStrikeHoldTicks = 0
        remainingPreStrikeHoldTicks = 0
        remainingStepWaitTicks = 0
        holdingStrikeThisTick = false
        holdingPreStrikeThisTick = false
        preStrikeHoldPending = false
        configuredStrikeHoldTicks = 0
        configuredPreStrikeHoldTicks = 0
        configuredStepWaitTicks = 0
        movements.clear()

        if (committedOffset.lengthSqr() < EPSILON) {
            clear()
            return
        }

        committedMovements.asReversed().forEach { movements += it.scale(-1.0) }
        physicalReturnStarted = physicalReturnEnabled
        if (physicalReturnStarted) {
            pendingPhysicalPositionOffset = committedOffset
        }
        recovering = true
    }

    /**
     * Returns the exact inverse of confirmed movement when [authoritativeOffset] still describes
     * this session. Unlike a synthesized straight return, this retraces the collision-safe route.
     */
    fun exactRecoveryMovementsFrom(authoritativeOffset: Vec3): List<Vec3>? {
        if (!authoritativeOffset.isFinite() ||
            authoritativeOffset.distanceToSqr(committedOffset) >= EPSILON
        ) {
            return null
        }

        val recordedOffset = committedMovements.fold(Vec3.ZERO, Vec3::add)
        if (recordedOffset.distanceToSqr(committedOffset) >= EPSILON) return null

        return committedMovements.asReversed().map { it.scale(-1.0) }
            .takeIf { it.isNotEmpty() }
    }

    /** Starts a physical recovery using an already collision-validated route back to zero. */
    fun beginPhysicalExactRecoveryFrom(authoritativeOffset: Vec3, recoveryMovements: List<Vec3>) {
        require(authoritativeOffset.isFinite()) { "Authoritative offset must be finite" }
        require(
            recoveryMovements.isNotEmpty() &&
                recoveryMovements.all { it.isFinite() && it.lengthSqr() >= EPSILON },
        ) {
            "Exact recovery must contain finite non-zero movement"
        }
        val recoveredOffset = recoveryMovements.fold(authoritativeOffset, Vec3::add)
        require(recoveredOffset.lengthSqr() < EPSILON) { "Exact recovery must end at the session origin" }

        clear()
        committedOffset = authoritativeOffset
        movements.addAll(recoveryMovements)
        movements += Vec3.ZERO
        physicalReturnEnabled = true
        physicalReturnStarted = true
        pendingPhysicalPositionOffset = authoritativeOffset
        recovering = true
    }

    fun beginRecoveryFrom(authoritativeOffset: Vec3, maxSpeed: Double) =
        beginRecoveryFrom(authoritativeOffset, maxSpeed, physicalReturn = false)

    fun beginPhysicalRecoveryFrom(authoritativeOffset: Vec3, maxSpeed: Double) =
        beginRecoveryFrom(authoritativeOffset, maxSpeed, physicalReturn = true)

    private fun beginRecoveryFrom(
        authoritativeOffset: Vec3,
        maxSpeed: Double,
        physicalReturn: Boolean,
    ) {
        clear()
        committedOffset = authoritativeOffset
        physicalReturnEnabled = physicalReturn
        beginRecovery(maxSpeed)
    }

    fun clear() {
        movements.clear()
        committedMovements.clear()
        pendingOffset = null
        pendingStepIsOutbound = false
        pendingStepIsPhysicalReturn = false
        pendingPhysicalPositionOffset = null
        remainingOutboundSteps = 0
        remainingStrikeHoldTicks = 0
        remainingPreStrikeHoldTicks = 0
        remainingStepWaitTicks = 0
        holdingStrikeThisTick = false
        holdingPreStrikeThisTick = false
        preStrikeHoldPending = false
        configuredStrikeHoldTicks = 0
        configuredPreStrikeHoldTicks = 0
        configuredStepWaitTicks = 0
        physicalReturnEnabled = false
        physicalReturnStarted = false
        lastDeliveredMovement = null
        committedOffset = Vec3.ZERO
        recovering = false
    }

    private fun finishRecoveryIfComplete() {
        if (!recovering || movements.isNotEmpty()) return
        committedOffset = Vec3.ZERO
        lastDeliveredMovement = null
        recovering = false
    }

    private companion object {
        const val EPSILON = 1.0E-12
    }
}

private const val SPEAR_KILL_PACKET_EPSILON = 1.0E-12
private const val SPEAR_KILL_PHYSICAL_RETURN_DISTANCE_SQUARED = 4.0

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

/** Applies confirmed return positions once the client is observed away from the session origin. */
internal class SpearKillPhysicalReturnPositioner(
    private val physicalReturnDistanceSquared: Double = SPEAR_KILL_PHYSICAL_RETURN_DISTANCE_SQUARED,
) {

    private var applyPositionUpdates: Boolean? = null

    init {
        require(physicalReturnDistanceSquared.isFinite() && physicalReturnDistanceSquared >= 0.0) {
            "Physical return distance must be finite and non-negative"
        }
    }

    fun resolve(origin: Vec3, currentPosition: Vec3, confirmedOffset: Vec3): Vec3? {
        val isDisplaced = currentPosition.distanceToSqr(origin) > physicalReturnDistanceSquared
        val shouldApply = applyPositionUpdates == true || isDisplaced
        applyPositionUpdates = shouldApply
        return origin.add(confirmedOffset).takeIf { shouldApply }
    }

    fun clear() {
        applyPositionUpdates = null
    }
}

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

        if (recentVirtualPositions.none { it.distanceToSqr(serverPosition) <= POSITION_EPSILON_SQUARED }) {
            if (recentVirtualPositions.size == MAX_RECENT_POSITIONS) {
                recentVirtualPositions.removeFirst()
            }
            recentVirtualPositions += serverPosition
        }
        remainingTicks = guardTicks
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

object SpearKillSetbackHook {

    @JvmStatic
    fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
        ModuleSpearKill.preparePacketSetback(packet, player)
    }

    @JvmStatic
    fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
        ModuleSpearKill.finishPacketSetback(packet, player)
    }
}
