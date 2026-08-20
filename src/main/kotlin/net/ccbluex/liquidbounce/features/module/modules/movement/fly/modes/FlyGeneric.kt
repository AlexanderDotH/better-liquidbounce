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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.additions.rawInput
import net.ccbluex.liquidbounce.additions.suppressSneak
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.BlockShapeEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerJumpEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.GroundPacketDeliveryTracker
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.outgoingMovementPacket
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.math.withLength
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Input
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.jvm.optionals.getOrNull

private const val VANILLA_CHECK_BYPASS_INTERVAL = 40
private const val VANILLA_CHECK_BYPASS_Y_OFFSET = 0.04

internal fun shouldRunVanillaFlyCheckBypass(enabled: Boolean, tickCount: Int) =
    enabled && tickCount % VANILLA_CHECK_BYPASS_INTERVAL == 0

internal fun vanillaFlyCheckBypassY(currentY: Double) = currentY - VANILLA_CHECK_BYPASS_Y_OFFSET

internal fun applyVanillaFlyCheckBypass(packet: ServerboundMovePlayerPacket, currentY: Double) {
    packet.y = vanillaFlyCheckBypassY(currentY)
}

internal enum class VanillaFlyCheckBypassMode(override val tag: String) : Tagged {
    MOTION("Motion"),
    PACKET("Packet"),
}

internal fun resolveVanillaFlyCheckBypassMode(
    configuredMode: VanillaFlyCheckBypassMode,
    isFallFlying: Boolean,
) = if (isFallFlying) VanillaFlyCheckBypassMode.PACKET else configuredMode

internal fun shouldSendVanillaFlyPacketBypass(
    eventState: EventState,
    enabled: Boolean,
    tickCount: Int,
    configuredMode: VanillaFlyCheckBypassMode,
    isFallFlying: Boolean,
    movementSuspended: Boolean = false,
) = !movementSuspended &&
    eventState == EventState.POST &&
    shouldRunVanillaFlyCheckBypass(enabled, tickCount) &&
    resolveVanillaFlyCheckBypassMode(configuredMode, isFallFlying) == VanillaFlyCheckBypassMode.PACKET

internal fun resolveVanillaFlyElytraVerticalMotion(
    isFallFlying: Boolean,
    movementY: Double,
    requestedVerticalMotion: Double,
) = if (isFallFlying && movementY < 0.0) requestedVerticalMotion else movementY

internal fun shouldSuppressVanillaFlyServerSneak(input: Input) = input.shift && !input.jump

internal enum class VanillaFlyNoFallAction {
    NONE,
    GROUND_PACKET,
    PACKET_JUMP,
}

internal object VanillaFlyNoFall {
    private const val GROUND_PROBE_DEPTH = 10.0
    private const val GROUND_PROBE_EPSILON = 1.0E-7
    private const val PACKET_JUMP_Y_OFFSET = 1.0E-9
    private const val SERVER_FALL_DISTANCE_MARGIN = 0.25

    val packetType = MovePacketType.FULL

    fun shouldRun(
        enabled: Boolean,
        fallDamagePossible: Boolean,
        spearKillPacketRouteActive: Boolean,
    ) = enabled && fallDamagePossible && !spearKillPacketRouteActive

    fun shouldSendGroundPacket(
        fallDistance: Double,
        verticalMovement: Double,
        safeFallDistance: Double,
        tickCount: Int,
    ) = tickCount > 20 && fallDistance - verticalMovement > safeFallDistance

    fun shouldSendPacketJump(
        onGround: Boolean,
        fallDistance: Double,
        safeFallDistance: Double,
    ) = !onGround && fallDistance > safeFallDistance

    fun maximumSafeServerFallDistance(safeFallDistance: Double) =
        (safeFallDistance - SERVER_FALL_DISTANCE_MARGIN).coerceAtLeast(0.0)

    fun resolveAction(
        eligible: Boolean,
        nearGround: Boolean,
        groundPacketDue: Boolean,
        packetJumpDue: Boolean,
    ) = when {
        !eligible -> VanillaFlyNoFallAction.NONE
        nearGround && groundPacketDue -> VanillaFlyNoFallAction.GROUND_PACKET
        !nearGround && packetJumpDue -> VanillaFlyNoFallAction.PACKET_JUMP
        else -> VanillaFlyNoFallAction.NONE
    }

    fun groundProbeBox(playerBoundingBox: AABB) = AABB(
        playerBoundingBox.minX,
        playerBoundingBox.minY - GROUND_PROBE_DEPTH - GROUND_PROBE_EPSILON,
        playerBoundingBox.minZ,
        playerBoundingBox.maxX,
        playerBoundingBox.minY,
        playerBoundingBox.maxZ,
    )

    fun applyPacketJump(packet: ServerboundMovePlayerPacket) {
        packet.y += PACKET_JUMP_Y_OFFSET
    }

    inline fun sendProtectedGroundPacket(
        tracker: GroundPacketDeliveryTracker,
        packet: ServerboundMovePlayerPacket,
        send: (ServerboundMovePlayerPacket) -> Unit,
    ) {
        tracker.protect(packet)
        try {
            send(packet)
        } finally {
            tracker.discard(packet)
        }
    }

    fun confirmGroundPacketDelivery(
        tracker: GroundPacketDeliveryTracker,
        packet: ServerboundMovePlayerPacket,
        cancelled: Boolean,
    ) = tracker.confirmFinalState(packet, cancelled)
}

/**
 * Mirrors the server's last confirmed movement position and accumulated downward distance. A fast client-side
 * descent can then be represented by several safe grounded packets without changing the requested endpoint.
 */
internal class VanillaFlyServerFallState {

    var position: Vec3? = null
        private set

    var fallDistance = 0.0
        private set

    fun initialize(position: Vec3, fallDistance: Double) {
        if (this.position != null || !position.isFinite || !fallDistance.isFinite()) {
            return
        }

        this.position = position
        this.fallDistance = maxOf(this.fallDistance, fallDistance.coerceAtLeast(0.0))
    }

    fun groundingPositions(target: Vec3, safeFallDistance: Double): List<Vec3> {
        val start = position ?: return emptyList()
        if (!target.isFinite || !safeFallDistance.isFinite()) {
            return emptyList()
        }

        val totalDescent = start.y - target.y
        val maximumSafeDescent = VanillaFlyNoFall.maximumSafeServerFallDistance(safeFallDistance)
        if (totalDescent <= 0.0 || maximumSafeDescent <= 0.0) {
            return emptyList()
        }

        val groundingPositions = mutableListOf<Vec3>()
        var accumulatedDescent = 0.0
        var currentFallDistance = fallDistance
        while (currentFallDistance + totalDescent - accumulatedDescent > maximumSafeDescent) {
            accumulatedDescent += (maximumSafeDescent - currentFallDistance).coerceAtLeast(0.0)
            groundingPositions += start.lerp(target, accumulatedDescent / totalDescent)
            currentFallDistance = 0.0
        }

        return groundingPositions
    }

    fun confirm(position: Vec3, onGround: Boolean) {
        if (!position.isFinite) {
            clear()
            return
        }

        this.position?.let { previousPosition ->
            fallDistance += (previousPosition.y - position.y).coerceAtLeast(0.0)
        }
        if (onGround) {
            fallDistance = 0.0
        }
        this.position = position
    }

    fun invalidatePosition() {
        position = null
    }

    fun clear() {
        position = null
        fallDistance = 0.0
    }
}

internal inline fun applyVanillaFlyElytraVerticalMotion(
    event: PlayerMoveEvent,
    isFallFlying: Boolean,
    requestedVerticalMotion: Double,
    setVelocityY: (Double) -> Unit,
) {
    if (!isFallFlying || event.movement.y >= 0.0) {
        return
    }

    val resolvedMotion = resolveVanillaFlyElytraVerticalMotion(
        isFallFlying = true,
        movementY = event.movement.y,
        requestedVerticalMotion = requestedVerticalMotion,
    )
    event.movement.y = resolvedMotion
    setVelocityY(resolvedMotion)
}

private class VanillaFlyBaseSpeed(speedRange: ClosedFloatingPointRange<Float>) : ValueGroup("BaseSpeed") {
    val horizontalSpeed by float("Horizontal", 0.44f, speedRange)
    val verticalSpeed by float("Vertical", 0.44f, speedRange)
}

private class VanillaFlySprintSpeed(
    parent: Mode,
    speedRange: ClosedFloatingPointRange<Float>,
) : ToggleableValueGroup(parent, "SprintSpeed", true) {
    val horizontalSpeed by float("Horizontal", 1f, speedRange)
    val verticalSpeed by float("Vertical", 1f, speedRange)
}

/**
 * Runtime shared by ordinary Vanilla Fly and packet-sequenced Fly. Subclasses may change how a
 * collision-resolved movement is transmitted, but local movement and Vanilla safety behavior stay identical.
 */
internal abstract class VanillaFlyMode(
    name: String,
    speedRange: ClosedFloatingPointRange<Float>,
) : Mode(name) {

    private val glide by float("Glide", 0.0f, -1f..1f)

    private val bypassVanillaCheck by boolean("BypassVanillaCheck", true)
    private val bypassMode by enumChoice("BypassMode", VanillaFlyCheckBypassMode.PACKET)
    private val noFall by boolean("NoFall", false)
    private val noFallDeliveryTracker = GroundPacketDeliveryTracker()
    private val noFallServerState = VanillaFlyServerFallState()
    private var deliveredMovementPacketsThisTick = 0

    private val baseSpeed = VanillaFlyBaseSpeed(speedRange)
    private val sprintSpeed = VanillaFlySprintSpeed(this, speedRange)

    init {
        tree(baseSpeed)
        tree(sprintSpeed)
    }

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    private val useSprintSpeed
        get() = mc.options.keySprint.isDown && sprintSpeed.enabled

    private val horizontalSpeed
        get() = if (useSprintSpeed) sprintSpeed.horizontalSpeed else baseSpeed.horizontalSpeed

    private val verticalSpeed
        get() = if (useSprintSpeed) sprintSpeed.verticalSpeed else baseSpeed.verticalSpeed

    protected val requestedVerticalMotion
        get() = when {
            mc.options.keyJump.isDown && !mc.options.keyShift.isDown -> verticalSpeed.toDouble()
            mc.options.keyShift.isDown && !mc.options.keyJump.isDown -> -verticalSpeed.toDouble()
            else -> glide.toDouble()
        }

    override fun disable() {
        clearVanillaFlyRuntime()
        onVanillaFlyRuntimeReset()
        super.disable()
    }

    protected open val movementSuspended: Boolean
        get() = false

    protected open fun onVanillaFlyRuntimeReset() = Unit

    protected open fun onVanillaFlyMovementSuspended() = Unit

    protected val existingDeliveredMovementPacketCount: Int
        get() = deliveredMovementPacketsThisTick

    protected fun isTrackedNoFallGroundPacket(packet: ServerboundMovePlayerPacket) =
        noFallDeliveryTracker.reassertGround(packet)

    protected fun forecastNoFallPacketCount(target: Vec3): Int {
        if (!noFallEligible) {
            return 0
        }

        noFallServerState.initialize(player.position(), player.fallDistance.toDouble())
        return noFallServerState.groundingPositions(
            target = target,
            safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        ).size
    }

    protected fun forecastPostBypassPacketCount(): Int = if (
        shouldSendVanillaFlyPacketBypass(
            eventState = EventState.POST,
            enabled = bypassVanillaCheck,
            tickCount = player.tickCount,
            configuredMode = bypassMode,
            isFallFlying = player.isFallFlying,
            movementSuspended = movementSuspended,
        )
    ) {
        1
    } else {
        0
    }

    protected fun clearVanillaFlyRuntime() {
        noFallDeliveryTracker.clear()
        noFallServerState.clear()
        deliveredMovementPacketsThisTick = 0
    }

    private val noFallEligible
        get() = VanillaFlyNoFall.shouldRun(
            enabled = noFall,
            fallDamagePossible = !player.isCreative && !player.isSpectator &&
                !player.abilities.invulnerable && !player.abilities.flying,
            spearKillPacketRouteActive = ModuleSpearKill.usesPacketMovement,
        )

    private fun isNoFallGroundNearby(): Boolean {
        if (player.onGround()) {
            return true
        }

        val probeBox = VanillaFlyNoFall.groundProbeBox(player.boundingBox)
        val minimum = BlockPos.containing(probeBox.minX, probeBox.minY, probeBox.minZ)
        val maximum = BlockPos.containing(probeBox.maxX, probeBox.maxY, probeBox.maxZ)
        if (!world.hasChunksAt(minimum, maximum)) {
            return false
        }

        return world.getBlockCollisions(player, probeBox).anyNotEmpty()
    }

    private fun runNoFall() {
        if (!noFallEligible) {
            noFallDeliveryTracker.clear()
            noFallServerState.clear()
            return
        }

        noFallServerState.initialize(player.position(), player.fallDistance.toDouble())
        val safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
        val action = VanillaFlyNoFall.resolveAction(
            eligible = true,
            nearGround = isNoFallGroundNearby(),
            groundPacketDue = VanillaFlyNoFall.shouldSendGroundPacket(
                fallDistance = player.fallDistance.toDouble(),
                verticalMovement = player.deltaMovement.y,
                safeFallDistance = safeFallDistance,
                tickCount = player.tickCount,
            ),
            packetJumpDue = VanillaFlyNoFall.shouldSendPacketJump(
                onGround = player.onGround(),
                fallDistance = player.fallDistance.toDouble(),
                safeFallDistance = safeFallDistance,
            ),
        )

        when (action) {
            VanillaFlyNoFallAction.NONE -> Unit
            VanillaFlyNoFallAction.GROUND_PACKET -> sendNoFallGroundPacket()
            VanillaFlyNoFallAction.PACKET_JUMP -> sendNoFallPacketJump()
        }
    }

    private fun sendNoFallGroundPacket(position: Vec3? = null): Boolean {
        val groundPosition = position ?: noFallServerState.position
        val packet = VanillaFlyNoFall.packetType.generatePacket().apply {
            groundPosition ?: return@apply
            x = groundPosition.x
            y = groundPosition.y
            z = groundPosition.z
        }
        VanillaFlyNoFall.sendProtectedGroundPacket(noFallDeliveryTracker, packet) { network.send(it) }
        return groundPosition == null ||
            noFallServerState.position == groundPosition && noFallServerState.fallDistance == 0.0
    }

    private fun sendNoFallPacketJump() {
        network.send(VanillaFlyNoFall.packetType.generatePacket().apply(VanillaFlyNoFall::applyPacketJump))
        player.resetFallDistance()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        deliveredMovementPacketsThisTick = 0
        if (movementSuspended) {
            player.deltaMovement = Vec3.ZERO
            noFallDeliveryTracker.clear()
            noFallServerState.clear()
            onVanillaFlyMovementSuspended()
            return@tickHandler
        }

        player.deltaMovement = player.deltaMovement.withStrafe(speed = horizontalSpeed.toDouble())
        player.deltaMovement.y = requestedVerticalMotion

        if (
            shouldRunVanillaFlyCheckBypass(bypassVanillaCheck, player.tickCount) &&
            resolveVanillaFlyCheckBypassMode(bypassMode, player.isFallFlying) == VanillaFlyCheckBypassMode.MOTION
        ) {
            player.deltaMovement.y = -VANILLA_CHECK_BYPASS_Y_OFFSET
        }

        runNoFall()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        clearVanillaFlyRuntime()
        onVanillaFlyRuntimeReset()
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        clearVanillaFlyRuntime()
        onVanillaFlyRuntimeReset()
    }

    @Suppress("unused")
    private val noFallSafetyPacketHandler = handler<PacketEvent>(priority = SAFETY_FEATURE) { event ->
        val packet = event.outgoingMovementPacket ?: return@handler
        noFallDeliveryTracker.reassertGround(packet)
    }

    @Suppress("unused")
    private val noFallSegmentationPacketHandler = handler<PacketEvent>(
        priority = (READ_FINAL_STATE + 1).toShort(),
    ) { event ->
        val packet = event.outgoingMovementPacket ?: return@handler
        if (event.isCancelled) {
            return@handler
        }
        if (!noFallEligible) {
            noFallServerState.clear()
            return@handler
        }
        if (noFallDeliveryTracker.reassertGround(packet)) {
            return@handler
        }

        noFallServerState.initialize(player.position(), player.fallDistance.toDouble())
        val serverPosition = noFallServerState.position ?: return@handler
        val target = Vec3(
            packet.getX(serverPosition.x),
            packet.getY(serverPosition.y),
            packet.getZ(serverPosition.z),
        )
        noFallServerState.groundingPositions(
            target = target,
            safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        ).forEach { groundingPosition ->
            if (!sendNoFallGroundPacket(groundingPosition)) {
                event.cancelEvent()
                return@handler
            }
        }
    }

    @Suppress("unused")
    private val noFallFinalPacketHandler = handler<PacketEvent>(priority = READ_FINAL_STATE) { event ->
        if (event.origin == TransferOrigin.INCOMING &&
            event.packet is ClientboundPlayerPositionPacket &&
            !event.isCancelled
        ) {
            noFallDeliveryTracker.clear()
            noFallServerState.invalidatePosition()
            return@handler
        }

        val packet = event.outgoingMovementPacket ?: return@handler
        if (VanillaFlyNoFall.confirmGroundPacketDelivery(noFallDeliveryTracker, packet, event.isCancelled)) {
            player.resetFallDistance()
        }
        if (!event.isCancelled) {
            deliveredMovementPacketsThisTick++
        }
        if (event.isCancelled || !noFallEligible) {
            return@handler
        }

        val serverPosition = noFallServerState.position ?: return@handler
        noFallServerState.confirm(
            position = Vec3(
                packet.getX(serverPosition.x),
                packet.getY(serverPosition.y),
                packet.getZ(serverPosition.z),
            ),
            onGround = packet.isOnGround,
        )
    }

    @Suppress("unused")
    private val networkMovementHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        if (!shouldSendVanillaFlyPacketBypass(
                eventState = event.state,
                enabled = bypassVanillaCheck,
                tickCount = player.tickCount,
                configuredMode = bypassMode,
                isFallFlying = player.isFallFlying,
                movementSuspended = movementSuspended,
            )
        ) {
            return@handler
        }

        network.send(MovePacketType.POSITION_AND_ON_GROUND.generatePacket().apply {
            applyVanillaFlyCheckBypass(this, player.y)
        })
    }

    @Suppress("unused")
    private val moveHandler = handler<PlayerMoveEvent>(priority = SAFETY_FEATURE) { event ->
        if (movementSuspended) {
            return@handler
        }
        applyVanillaFlyElytraVerticalMotion(
            event = event,
            isFallFlying = player.isFallFlying,
            requestedVerticalMotion = requestedVerticalMotion,
        ) { player.deltaMovement.y = it }
    }

    @Suppress("unused")
    private val inputPacketHandler = handler<PacketEvent> { event ->
        if (movementSuspended || event.origin != TransferOrigin.OUTGOING) return@handler

        val packet = event.packet as? ServerboundPlayerInputPacket ?: return@handler
        if (shouldSuppressVanillaFlyServerSneak(packet.rawInput)) {
            packet.suppressSneak = true
        }
    }

}

internal object FlyVanilla : VanillaFlyMode("Vanilla", 0.1f..10f)

private data class FlightAbilitiesSnapshot(
    val mayfly: Boolean,
    val flying: Boolean,
    val flyingSpeed: Float
)

private fun LocalPlayer.flightAbilitiesSnapshot() = FlightAbilitiesSnapshot(
    abilities.mayfly,
    abilities.flying,
    abilities.flyingSpeed
)

private fun LocalPlayer.restoreFlightAbilities(snapshot: FlightAbilitiesSnapshot) {
    val hasVanillaFlight = isCreative || isSpectator

    abilities.mayfly = snapshot.mayfly || hasVanillaFlight
    abilities.flyingSpeed = snapshot.flyingSpeed

    if (!abilities.mayfly) {
        abilities.flying = false
        return
    }

    if (!hasVanillaFlight) {
        abilities.flying = snapshot.flying
    }
}

internal object FlyCreative : Mode("Creative") {

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    private var previousAbilities = FlightAbilitiesSnapshot(mayfly = false, flying = false, flyingSpeed = 0.05f)

    private val speed by float("Speed", 0.1f, 0.1f..5f)

    private object SprintSpeed : ToggleableValueGroup(this, "SprintSpeed", true) {
        val speed by float("Speed", 0.1f, 0.1f..5f)
    }

    init {
        tree(SprintSpeed)
    }

    private val maxVelocity by float("MaxVelocity", 4f, 1f..20f)

    private val bypassVanillaCheck by boolean("BypassVanillaCheck", true)

    private val forceFlight by boolean("ForceFlight", true)

    override fun enable() {
        previousAbilities = player.flightAbilitiesSnapshot()
        player.abilities.mayfly = true
    }

    private fun shouldFlyDown(): Boolean {
        if (!bypassVanillaCheck) return false
        if (player.tickCount % 40 != 0) return false

        // check if the player is above a block or in midair
        // if the player is right above a block, we don't need to fly down
        if (world.getBlockStates(player.boundingBox.move(0.0, -0.55, 0.0)).anyMatch { !it.isAir }) return false

        return true
    }

    val repeatable = tickHandler {
        player.abilities.flyingSpeed =
            if (mc.options.keySprint.isDown && SprintSpeed.enabled) SprintSpeed.speed else speed

        if (forceFlight) player.abilities.flying = true

        if (player.deltaMovement.lengthSqr() > maxVelocity.sq()) {
            player.deltaMovement = player.deltaMovement.withLength(maxVelocity.toDouble())
        }

        if (shouldFlyDown()) {
            network.send(MovePacketType.POSITION_AND_ON_GROUND.generatePacket())
        }

    }

    val packetHandler = handler<PacketEvent> { event ->
        if (shouldFlyDown() && event.packet is ServerboundMovePlayerPacket) {
            event.packet.y = player.yLast - 0.04
        }
    }

    override fun disable() {
        player.restoreFlightAbilities(previousAbilities)
    }

}

internal object FlyAirWalk : Mode("AirWalk") {

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    val onGround by boolean("OnGround", true)

    val packetHandler = handler<PacketEvent> { event ->
        if (event.packet is ServerboundMovePlayerPacket) {
            event.packet.onGround = onGround
        }
    }

    @Suppress("unused")
    val shapeHandler = handler<BlockShapeEvent> { event ->
        if (event.state.block !is LiquidBlock && event.pos.y < player.y) {
            event.shape = Shapes.block()
        }
    }

    @Suppress("unused")
    val jumpEvent = handler<PlayerJumpEvent> { event ->
        event.cancelEvent()
    }
}

/**
 * Explode yourself to fly
 * Takes any kind of damage, preferably explosion damage.
 * Might bypass some anti-cheats.
 */
internal object FlyExplosion : Mode("Explosion") {

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    val vertical by float("Vertical", 4f, 0f..10f)
    val startStrafe by float("StartStrafe", 1f, 0.6f..4f)
    val strafeDecrease by float("StrafeDecrease", 0.005f, 0.001f..0.1f)

    private var strafeSince = 0.0f

    override fun enable() {
        chat("You need to be damaged by an explosion to fly.")
        super.enable()
    }

    val repeatable = tickHandler {
        if (strafeSince > 0) {
            if (!player.onGround()) {
                player.deltaMovement = player.deltaMovement.withStrafe(speed = strafeSince.toDouble())
                strafeSince -= strafeDecrease
            } else {
                strafeSince = 0f
            }
        }
    }

    val packetHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet

        // Check if this is a regular velocity update
        if (packet is ClientboundSetEntityMotionPacket && packet.id == player.id) {
            // Modify packet according to the specified values
            packet.movement.x = 0.0
            packet.movement.y = packet.movement.y * vertical
            packet.movement.z = 0.0

            waitTicks(1)
            strafeSince = startStrafe
        } else if (packet is ClientboundExplodePacket) { // Check if explosion affects velocity
            packet.playerKnockback.getOrNull()?.let { knockback ->
                knockback.x = 0.0
                knockback.y *= vertical
                knockback.z = 0.0

                waitTicks(1)
                strafeSince = startStrafe
            }
        }
    }

}

internal object FlyJetpack : Mode("Jetpack") {

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    val repeatable = handler<GameTickEvent> {
        if (player.input.keyPresses.jump) {
            val deltaMovement = player.deltaMovement
            player.deltaMovement = Vec3(
                deltaMovement.x * 1.1,
                deltaMovement.y + 0.15,
                deltaMovement.z * 1.1,
            )
        }
    }

}
