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
@file:Suppress("detekt:TooManyFunctions")

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.exploit.disabler.disablers.DisablerRateLimiting
import net.ccbluex.liquidbounce.features.module.modules.exploit.disabler.disablers.RateLimitedPacketDisposition
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableContainerCloseCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableCorrectionDecision
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableMovement
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractablePacketDisposition
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSession
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionEffect
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionRoute
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionState
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableResolvedTarget
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetLock
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.toBlockPos
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.collection.Filter
import net.ccbluex.liquidbounce.utils.entity.interactBlock
import net.ccbluex.liquidbounce.utils.entity.interactEntity
import net.ccbluex.liquidbounce.utils.input.InputTracker.wasPressedRecently
import net.ccbluex.liquidbounce.utils.movement.remote.RemoteMovementOwnership
import net.ccbluex.liquidbounce.utils.raytracing.clip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.PlayerRideableJumping
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

internal sealed interface InteractableRuntimeStatus {
    data class State(val state: InteractableSessionState) : InteractableRuntimeStatus
    data class Failure(val reason: String) : InteractableRuntimeStatus
    data class Recovery(val cause: InteractableSessionCause) : InteractableRuntimeStatus
    data class Terminated(val cause: InteractableSessionCause) : InteractableRuntimeStatus
    data class RecoveryStalled(val cause: InteractableSessionCause) : InteractableRuntimeStatus
    data class Resynchronized(val position: Vec3) : InteractableRuntimeStatus
}

internal class MinecraftReachInteractableRuntime {
    private val session = InteractableSession<InteractableResolvedTarget, InteractablePacketInstruction>()
    private val sessionPort = MinecraftInteractableSessionPort(session, ::executeEffects)
    private val controller = ReachInteractableController(
        MinecraftMovementOwnership,
        MinecraftInteractableTargetPort(),
        MinecraftInteractableRoutePort(),
        sessionPort,
    )

    private var activeSettings: InteractableSettingsSnapshot? = null
    private var nextMovementTick = 0
    private var pendingTransportPacket: ServerboundMovePlayerPacket? = null
    private var pendingSessionIdentity: Any? = null
    private var pendingInstruction: InteractablePacketInstruction? = null
    private var immediatePacket: Packet<*>? = null
    private var immediateDisposition: InteractablePacketDisposition? = null
    private var correctionContext: CorrectionContext? = null
    private val deferredOpenAttempts = ArrayDeque<InteractableSessionEffect.OpenAttempt>()
    private var interactionCaptureActive = false
    private val interactionDispositions = mutableListOf<InteractablePacketDisposition>()

    var status: InteractableRuntimeStatus? = null
        private set

    val active: Boolean
        get() = controller.active

    val renderSnapshot: InteractableRenderSnapshot?
        get() = controller.renderSnapshot

    fun claimUse(settings: InteractableSettingsSnapshot): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        if (gameMode.isDestroying || player.isHandsBusy) return false
        if (!mc.options.keyUse.wasPressedRecently(FRESH_USE_WINDOW_MS)) return false

        val claimed = controller.claim(
            normalInteractionAvailable = normalInteractionAvailable(),
            origin = player.position(),
            settings = settings,
            tick = player.tickCount,
        )
        if (claimed) {
            activeSettings = settings
            status = InteractableRuntimeStatus.State(session.state)
        } else {
            controller.lastMessage?.let { status = InteractableRuntimeStatus.Failure(it.statusReason()) }
        }
        return claimed
    }

    fun tick() {
        val player = mc.player ?: run {
            if (active) hardReset(InteractableSessionCause.WORLD_CHANGE)
            return
        }
        if (mc.connection == null) {
            hardReset(InteractableSessionCause.DISCONNECT)
            return
        }
        val tick = player.tickCount
        controller.tick(tick)
        controller.lastMessage?.let { status = InteractableRuntimeStatus.Failure(it.statusReason()) }
        if (!controller.active) {
            clearTransientState()
            return
        }

        if (session.state.requiresTargetValidation() && !controller.validateTarget()) {
            status = InteractableRuntimeStatus.Failure("TARGET_CHANGED")
            controller.abort(InteractableSessionCause.TARGET_CHANGED, tick)
            controller.reconcileOwnership()
            return
        }
        drainDeferredOpenAttempts()
        dispatchNextMovement(tick)
        status = status.takeIf { it !is InteractableRuntimeStatus.State }
            ?: InteractableRuntimeStatus.State(session.state)
    }

    fun rewriteOrConfirmOutgoing(event: net.ccbluex.liquidbounce.event.events.PacketEvent) {
        if (event.origin != net.ccbluex.liquidbounce.event.events.TransferOrigin.OUTGOING) return
        val packet = event.packet
        when {
            packet === immediatePacket -> confirmImmediatePacket(packet, event.isCancelled)
            interactionCaptureActive && packet.isContainerInteractionPacket() -> {
                interactionDispositions += packet.finalDisposition(event.isCancelled, TransferOrigin.OUTGOING)
            }
            packet is ServerboundMovePlayerPacket && packet === pendingTransportPacket -> {
                confirmPendingTransport(packet, event.isCancelled)
            }
            packet is ServerboundMovePlayerPacket && shouldRewriteInteractableAmbientMovement(
                movementLeaseRequired = session.movementLeaseRequired,
                correctionInProgress = correctionContext != null,
            ) -> {
                session.serverAnchorPosition?.let { packet.rewritePosition(it) }
                packet.finalDisposition(event.isCancelled, TransferOrigin.OUTGOING)
            }
        }
    }

    fun onContainerPacket(event: net.ccbluex.liquidbounce.event.events.PacketEvent) {
        val origin = event.origin
        val packet = event.packet
        when {
            origin == TransferOrigin.INCOMING && packet is ClientboundOpenScreenPacket &&
                session.state is InteractableSessionState.Opening -> {
                val disposition = packet.finalDisposition(event.isCancelled, origin)
                if (disposition == InteractablePacketDisposition.DELIVERED &&
                    session.claimOpenedContainer(packet.containerId, currentTick())
                ) {
                    status = InteractableRuntimeStatus.State(session.state)
                }
            }
            origin == TransferOrigin.INCOMING && packet is ClientboundContainerClosePacket -> {
                packet.finalDisposition(event.isCancelled, origin)
                executeEffects(session.containerClosed(
                    packet.containerId,
                    InteractableContainerCloseCause.SERVER,
                    currentTick(),
                ))
                if (event.isCancelled) closeOwnedContainer(packet.containerId)
            }
            origin == TransferOrigin.OUTGOING && packet is ServerboundContainerClosePacket -> {
                packet.finalDisposition(event.isCancelled, origin)
                executeEffects(session.containerClosed(
                    packet.containerId,
                    InteractableContainerCloseCause.USER,
                    currentTick(),
                ))
            }
        }
        controller.reconcileOwnership()
    }

    fun onScreen(screen: Screen?) {
        val container = screen as? AbstractContainerScreen<*>
        val owned = session.ownedContainerId
        if (owned == null) {
            if (container != null && session.movementLeaseRequired) {
                controller.abort(InteractableSessionCause.CONFLICTING_SCREEN, currentTick())
                controller.reconcileOwnership()
            }
            return
        }
        if (container != null && container.menu.containerId == owned) return
        if (container != null || mc.player?.containerMenu?.containerId != owned) {
            executeEffects(session.containerClosed(owned, InteractableContainerCloseCause.SERVER, currentTick()))
            controller.reconcileOwnership()
        }
    }

    fun abort(cause: InteractableSessionCause) {
        deferredOpenAttempts.clear()
        controller.abort(cause, currentTick())
        controller.reconcileOwnership()
        clearTransientState()
    }

    fun hardReset(cause: InteractableSessionCause) {
        deferredOpenAttempts.clear()
        controller.hardReset(cause)
        clearTransientState()
    }

    fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
        if (!session.movementLeaseRequired) return
        val local = PositionMoveRotation(player.position(), player.deltaMovement, player.yRot, player.xRot)
        val authoritative = PositionMoveRotation.calculateAbsolute(local, packet.change, packet.relatives).position
        correctionContext = CorrectionContext(packet, authoritative, session.origin, player.deltaMovement)
    }

    fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
        val context = correctionContext?.takeIf { it.packet === packet } ?: return
        correctionContext = null
        val recovery = boundedCorrectionRecovery(context.authoritative)
        val decision = session.corrected(context.authoritative, recovery, player.tickCount)
        executeEffects(decision.effects())
        if (decision is InteractableCorrectionDecision.Recovering) {
            context.visualOrigin?.let(player::setPos)
            player.deltaMovement = context.visualVelocity
        }
        controller.reconcileOwnership()
    }

    fun suppressLocalMovement(): Boolean = session.movementLeaseRequired

    private fun dispatchNextMovement(tick: Int) {
        if (tick < nextMovementTick || pendingTransportPacket != null) return
        if (!session.state.acceptsMovement()) return
        val identity = Any()
        val movement = session.prepareMovement(identity) ?: return
        val confirmedOrigin = session.confirmedPosition ?: return
        if (!movement.payload.isSafeToSend(confirmedOrigin, movement.confirmedPosition)) {
            status = InteractableRuntimeStatus.Failure("ROUTE_BLOCKED")
            session.confirmMovement(identity, InteractablePacketDisposition.DROPPED, tick)
            controller.abort(InteractableSessionCause.ROUTE_BLOCKED, tick)
            return
        }

        val packet = movement.payload.toPacket()
        pendingTransportPacket = packet
        pendingSessionIdentity = identity
        pendingInstruction = movement.payload
        val connection = mc.connection
        if (connection == null) {
            clearPendingPacket()
            session.confirmMovement(identity, InteractablePacketDisposition.DROPPED, tick)
            hardReset(InteractableSessionCause.DISCONNECT)
            return
        }
        connection.send(packet)
        if (pendingTransportPacket === packet) {
            clearPendingPacket()
            session.confirmMovement(identity, InteractablePacketDisposition.DROPPED, tick)
        }
    }

    private fun confirmPendingTransport(packet: ServerboundMovePlayerPacket, cancelled: Boolean) {
        pendingInstruction?.applyTo(packet)
        val identity = pendingSessionIdentity ?: return
        val disposition = packet.finalDisposition(cancelled, TransferOrigin.OUTGOING)
        val confirmation = session.confirmMovement(identity, disposition, currentTick())
        clearPendingPacket()
        if (confirmation.committed) {
            nextMovementTick = currentTick() + (activeSettings?.routing?.stepDelayTicks ?: 0)
        }
        confirmation.effects.forEach { effect ->
            if (effect is InteractableSessionEffect.OpenAttempt) {
                deferredOpenAttempts += effect
            } else {
                executeEffect(effect)
            }
        }
        controller.reconcileOwnership()
        clearTransientState()
    }

    private fun drainDeferredOpenAttempts() {
        if (session.state !is InteractableSessionState.Opening) {
            deferredOpenAttempts.clear()
            return
        }
        while (deferredOpenAttempts.isNotEmpty() && session.state is InteractableSessionState.Opening) {
            executeEffect(deferredOpenAttempts.removeFirst())
        }
    }

    private fun confirmImmediatePacket(packet: Packet<*>, cancelled: Boolean) {
        immediateDisposition = packet.finalDisposition(cancelled, TransferOrigin.OUTGOING)
    }

    private fun executeEffects(effects: List<InteractableSessionEffect>) {
        effects.forEach(::executeEffect)
        controller.reconcileOwnership()
        clearTransientState()
    }

    private fun executeEffect(effect: InteractableSessionEffect) {
        when (effect) {
            is InteractableSessionEffect.OpenAttempt -> if (
                !openTarget() && session.state is InteractableSessionState.Opening
            ) {
                status = InteractableRuntimeStatus.Failure("OPEN_ATTEMPT_${effect.attempt}_FAILED")
            }
            is InteractableSessionEffect.CloseOwnedContainer -> closeOwnedContainer(effect.containerId)
            is InteractableSessionEffect.ReturnStarted -> status = InteractableRuntimeStatus.State(session.state)
            is InteractableSessionEffect.RecoveryStarted -> {
                status = InteractableRuntimeStatus.Recovery(effect.cause)
            }
            is InteractableSessionEffect.RecoveryStalled -> {
                status = InteractableRuntimeStatus.RecoveryStalled(effect.cause)
            }
            is InteractableSessionEffect.ReleaseMovementLease -> {
                if (status !is InteractableRuntimeStatus.Resynchronized) {
                    status = if (effect.cause in SILENT_RELEASE_CAUSES) {
                        InteractableRuntimeStatus.State(session.state)
                    } else {
                        InteractableRuntimeStatus.Terminated(effect.cause)
                    }
                }
                clearTransientState()
            }
            is InteractableSessionEffect.AcceptCorrectionLocally -> {
                status = InteractableRuntimeStatus.Resynchronized(effect.authoritativePosition)
                clearTransientState()
            }
        }
    }

    private fun openTarget(): Boolean {
        val target = session.target ?: return false
        if (!controller.validateTarget()) {
            status = InteractableRuntimeStatus.Failure("TARGET_CHANGED")
            executeEffects(session.abort(InteractableSessionCause.TARGET_CHANGED, currentTick()))
            return false
        }
        val anchor = session.serverAnchorPosition ?: return false
        val interaction = resolveInteraction(target, anchor) ?: return false
        val rotation = Rotation.lookingAt(interaction.point, anchor.eyePosition())
        val rotationPacket = InteractablePacketInstruction.Position(
            anchor,
            fullPacket = true,
            onGround = true,
        ).toPacket(rotation)
        if (sendImmediate(rotationPacket) != InteractablePacketDisposition.DELIVERED) return false

        val player = mc.player ?: return false
        val localPosition = player.position()
        val localVelocity = player.deltaMovement
        interactionDispositions.clear()
        interactionCaptureActive = true
        return try {
            player.setPos(anchor)
            val handled = interactWithVanillaHandOrder { hand -> interaction.interact(hand) }
            handled && interactionDispositions.lastOrNull() == InteractablePacketDisposition.DELIVERED
        } finally {
            interactionCaptureActive = false
            player.setPos(localPosition)
            player.deltaMovement = localVelocity
        }
    }

    private fun resolveInteraction(
        target: InteractableResolvedTarget,
        anchor: Vec3,
    ): ResolvedInteraction? = when (val lock = target.lock) {
        is InteractableTargetLock.Block -> resolveBlockInteraction(
            position = lock.position.toBlockPos(),
            initialHit = target.initialHitLocation.let { Vec3(it.x, it.y, it.z) },
            anchor = anchor,
        )
        is InteractableTargetLock.ContainerVehicle -> resolveEntityInteraction(lock.uuid, anchor)
    }

    private fun resolveBlockInteraction(position: BlockPos, initialHit: Vec3, anchor: Vec3): ResolvedInteraction? {
        val level = mc.level ?: return null
        val player = mc.player ?: return null
        val eyes = anchor.eyePosition()
        val interactionRange = activeSettings?.interactionRange ?: return null
        val hit = sequenceOf(initialHit, Vec3.atCenterOf(position)).filter { point ->
            eyes.distanceTo(point) <= interactionRange
        }.map { point ->
            level.clip(eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
        }.firstOrNull { it.type == HitResult.Type.BLOCK && it.blockPos == position } ?: return null
        return ResolvedInteraction(hit.location) { hand ->
            interactBlock(hit, hand, SwingMode.DO_NOT_HIDE)
        }
    }

    private fun resolveEntityInteraction(uuid: java.util.UUID, anchor: Vec3): ResolvedInteraction? {
        val level = mc.level ?: return null
        val entity = level.getEntity(uuid) ?: return null
        val eyes = anchor.eyePosition()
        val point = entity.boundingBox.clip(eyes, entity.boundingBox.center).orElse(entity.boundingBox.center)
        if (eyes.distanceTo(point) > (activeSettings?.interactionRange ?: return null)) return null
        val obstruction = level.clip(eyes, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player!!)
        if (obstruction.type != HitResult.Type.MISS &&
            obstruction.location.distanceToSqr(eyes) < point.distanceToSqr(eyes)
        ) {
            return null
        }
        val hit = EntityHitResult(entity, point)
        return ResolvedInteraction(point) { hand ->
            interactEntity(entity, hit, hand, SwingMode.DO_NOT_HIDE)
        }
    }

    private fun sendImmediate(packet: Packet<*>): InteractablePacketDisposition {
        immediatePacket = packet
        immediateDisposition = null
        mc.connection?.send(packet)
        return (immediateDisposition ?: InteractablePacketDisposition.DROPPED).also {
            immediatePacket = null
            immediateDisposition = null
        }
    }

    private fun closeOwnedContainer(containerId: Int) {
        val player = mc.player ?: return
        if (player.containerMenu.containerId == containerId) player.closeContainer()
    }

    private fun boundedCorrectionRecovery(
        authoritative: Vec3,
    ): List<InteractableMovement<InteractablePacketInstruction>>? {
        val settings = activeSettings ?: return null
        val origin = session.origin ?: return null
        if (authoritative.distanceTo(origin) > settings.routing.maxCost) return null
        val level = mc.level ?: return null
        val player = mc.player ?: return null
        val world = CorrectionRouteWorld(level, player)
        if (!world.isClear(authoritative, origin)) return null
        return interpolatePositions(authoritative, origin, settings.routing.stepDistance).map { position ->
            InteractableMovement(
                InteractablePacketInstruction.Position(position, fullPacket = false, onGround = true),
                position,
            )
        }
    }

    private fun InteractablePacketInstruction.isSafeToSend(from: Vec3, position: Vec3): Boolean {
        if (this is InteractablePacketInstruction.Status) return true
        val level = mc.level ?: return false
        val player = mc.player ?: return false
        if (this is InteractablePacketInstruction.Position && !collisionChecked) {
            val routeWorld = CorrectionRouteWorld(level, player)
            return isSafeVClip(level, player, from, position) &&
                (!requiresStandableEndpoint || routeWorld.isStandable(position))
        }
        return CorrectionRouteWorld(level, player).isClear(from, position)
    }

    private fun isSafeVClip(
        level: net.minecraft.client.multiplayer.ClientLevel,
        player: Entity,
        from: Vec3,
        to: Vec3,
    ): Boolean {
        if (kotlin.math.abs(from.x - to.x) > POSITION_EPSILON ||
            kotlin.math.abs(from.z - to.z) > POSITION_EPSILON
        ) {
            return false
        }
        val minimumY = floor(minOf(from.y, to.y)).toInt()
        val maximumY = ceil(maxOf(from.y, to.y)).toInt()
        val clearanceHeight = ceil(player.getDimensions(Pose.STANDING).height).toInt()
        val protectBedrock = activeSettings?.surfaceFallback?.doNotClipAroundBedrock == true
        for (y in minimumY..maximumY) {
            val position = BlockPos.containing(from.x, y.toDouble(), from.z)
            val top = position.above(clearanceHeight - 1)
            if (level.isOutsideBuildHeight(y) || level.isOutsideBuildHeight(top.y) ||
                !level.isLoaded(position) || !level.isLoaded(top)
            ) {
                return false
            }
            if (protectBedrock && level.getBlockState(position).block === Blocks.BEDROCK) return false
        }
        return level.worldBorder.isWithinBounds(BlockPos.containing(from)) &&
            level.worldBorder.isWithinBounds(BlockPos.containing(to))
    }

    private fun clearPendingPacket() {
        pendingTransportPacket = null
        pendingSessionIdentity = null
        pendingInstruction = null
    }

    private fun clearTransientState() {
        if (controller.active) return
        activeSettings = null
        nextMovementTick = 0
        clearPendingPacket()
        immediatePacket = null
        immediateDisposition = null
        correctionContext = null
        deferredOpenAttempts.clear()
        interactionCaptureActive = false
        interactionDispositions.clear()
    }

    private fun normalInteractionAvailable(): Boolean = when (val hit = mc.hitResult) {
        is BlockHitResult -> hit.type == HitResult.Type.BLOCK
        is EntityHitResult -> mc.player?.isWithinEntityInteractionRange(hit.entity, 0.0) == true
        else -> false
    }

    private fun Vec3.eyePosition(): Vec3 {
        val player = requireNotNull(mc.player)
        return add(0.0, player.getEyeHeight(Pose.STANDING).toDouble(), 0.0)
    }

    private fun currentTick(): Int = mc.player?.tickCount ?: 0

    private data class CorrectionContext(
        val packet: ClientboundPlayerPositionPacket,
        val authoritative: Vec3,
        val visualOrigin: Vec3?,
        val visualVelocity: Vec3,
    )

    private data class ResolvedInteraction(
        val point: Vec3,
        val interact: (InteractionHand) -> InteractionResult?,
    )
}

internal fun interactWithVanillaHandOrder(
    interact: (InteractionHand) -> InteractionResult?,
): Boolean {
    for (hand in InteractionHand.entries) {
        when (interact(hand)) {
            is InteractionResult.Success, is InteractionResult.Fail -> return true
            else -> Unit
        }
    }
    return false
}

internal fun shouldRewriteInteractableAmbientMovement(
    movementLeaseRequired: Boolean,
    correctionInProgress: Boolean,
): Boolean = movementLeaseRequired && !correctionInProgress

private class MinecraftInteractableSessionPort(
    private val session: InteractableSession<InteractableResolvedTarget, InteractablePacketInstruction>,
    private val effectSink: (List<InteractableSessionEffect>) -> Unit,
) : ControllerSessionPort<InteractableResolvedTarget, InteractableSessionRoute<InteractablePacketInstruction>> {
    override val active: Boolean
        get() = session.movementLeaseRequired

    override fun beginPlanning(
        target: InteractableResolvedTarget,
        origin: Vec3,
        settings: InteractableSessionSettings,
        tick: Int,
    ) = session.beginPlanning(target, origin, settings, tick)

    override fun acceptRoute(route: InteractableSessionRoute<InteractablePacketInstruction>, tick: Int) {
        check(session.acceptRoute(route, tick)) { "Interactable session rejected its planned route" }
    }

    override fun tick(tick: Int) = effectSink(session.tick(tick))

    override fun abort(cause: InteractableSessionCause, tick: Int) = effectSink(session.abort(cause, tick))

    override fun hardReset(cause: InteractableSessionCause) = effectSink(session.hardReset(cause))
}

private object MinecraftMovementOwnership : ControllerMovementOwnership {
    override fun tryAcquire(owner: String): ControllerMovementLease? =
        RemoteMovementOwnership.tryAcquire(owner)?.let(::MinecraftMovementLease)
}

private class MinecraftMovementLease(
    private val delegate: RemoteMovementOwnership.Lease,
) : ControllerMovementLease {
    override val active: Boolean
        get() = delegate.active

    override fun close() = delegate.close()
}

private fun InteractablePacketInstruction.toPacket(
    rotation: Rotation? = null,
): ServerboundMovePlayerPacket {
    val player = requireNotNull(mc.player)
    return when (this) {
        is InteractablePacketInstruction.Status -> ServerboundMovePlayerPacket.StatusOnly(
            onGround,
            player.horizontalCollision,
        )
        is InteractablePacketInstruction.Position -> {
            val resolvedRotation = rotation ?: Rotation(player.yRot, player.xRot)
            if (fullPacket) {
                ServerboundMovePlayerPacket.PosRot(
                    position.x,
                    position.y,
                    position.z,
                    resolvedRotation.yRot,
                    resolvedRotation.xRot,
                    onGround,
                    player.horizontalCollision,
                )
            } else {
                ServerboundMovePlayerPacket.Pos(
                    position.x,
                    position.y,
                    position.z,
                    onGround,
                    player.horizontalCollision,
                )
            }
        }
    }
}

private fun InteractablePacketInstruction.applyTo(packet: ServerboundMovePlayerPacket) {
    packet.onGround = onGround
    if (this !is InteractablePacketInstruction.Position) return
    packet.rewritePosition(position)
    if (fullPacket) {
        packet.yRot = mc.player?.yRot ?: packet.yRot
        packet.xRot = mc.player?.xRot ?: packet.xRot
        packet.hasRot = true
    }
}

private fun ServerboundMovePlayerPacket.rewritePosition(position: Vec3) {
    x = position.x
    y = position.y
    z = position.z
    hasPos = true
}

private fun Packet<*>.finalDisposition(
    cancelled: Boolean,
    origin: TransferOrigin,
): InteractablePacketDisposition {
    if (BlinkManager.takeQueued(this, origin)) {
        return InteractablePacketDisposition.QUEUED
    }
    if (origin == TransferOrigin.INCOMING) {
        return if (cancelled) InteractablePacketDisposition.CANCELLED else InteractablePacketDisposition.DELIVERED
    }
    return when (DisablerRateLimiting.takeDisposition(this)) {
        RateLimitedPacketDisposition.QUEUED -> InteractablePacketDisposition.QUEUED
        RateLimitedPacketDisposition.DROPPED -> InteractablePacketDisposition.DROPPED
        null -> if (cancelled) InteractablePacketDisposition.CANCELLED else InteractablePacketDisposition.DELIVERED
    }
}

private fun Packet<*>.isContainerInteractionPacket(): Boolean =
    this is ServerboundUseItemOnPacket || this is ServerboundInteractPacket

private fun InteractableSessionState.requiresTargetValidation(): Boolean =
    this is InteractableSessionState.Outbound ||
        this is InteractableSessionState.Opening ||
        this is InteractableSessionState.Holding

private fun InteractableSessionState.acceptsMovement(): Boolean =
    this is InteractableSessionState.Outbound ||
        this is InteractableSessionState.Returning ||
        this is InteractableSessionState.Recovering

private fun InteractableCorrectionDecision.effects(): List<InteractableSessionEffect> = when (this) {
    InteractableCorrectionDecision.Ignored -> emptyList()
    is InteractableCorrectionDecision.Recovering -> effects
    is InteractableCorrectionDecision.Completed -> effects
    is InteractableCorrectionDecision.AcceptLocally -> effects
}

private fun InteractableControllerMessage.statusReason(): String = when (this) {
    InteractableControllerMessage.MovementBusy -> "REMOTE_MOVEMENT_BUSY"
    is InteractableControllerMessage.TargetRejected -> reason
    is InteractableControllerMessage.RouteFailed -> reason
}

private fun interpolatePositions(from: Vec3, to: Vec3, stepDistance: Double): List<Vec3> {
    val distance = from.distanceTo(to)
    if (distance <= 1.0E-9) return emptyList()
    val count = ceil(distance / stepDistance).toInt().coerceAtLeast(1)
    return (1..count).map { index -> if (index == count) to else from.lerp(to, index.toDouble() / count) }
}

private class CorrectionRouteWorld(
    private val level: net.minecraft.client.multiplayer.ClientLevel,
    private val player: Entity,
) {
    private val dimensions = player.getDimensions(Pose.STANDING)

    fun isClear(from: Vec3, to: Vec3): Boolean = interpolatePositions(from, to, SWEEP_STEP).all(::isStandable)

    fun isStandable(position: Vec3): Boolean {
        val node = BlockPos.containing(position)
        if (!level.isLoaded(node) || level.isOutsideBuildHeight(node.y)) return false
        val box = dimensions.makeBoundingBox(position).deflate(1.0E-7)
        if (!level.worldBorder.isWithinBounds(box) || !level.noCollision(player, box)) return false
        return level.getBlockCollisions(player, box.move(0.0, -SUPPORT_DEPTH, 0.0)).any { !it.isEmpty }
    }

    private companion object {
        const val SUPPORT_DEPTH = 0.05
        const val SWEEP_STEP = 0.25
    }
}

private const val FRESH_USE_WINDOW_MS = 150L
private const val POSITION_EPSILON = 1.0E-6
private val SILENT_RELEASE_CAUSES = setOf(
    InteractableSessionCause.COMPLETED,
    InteractableSessionCause.DISABLE,
    InteractableSessionCause.WORLD_CHANGE,
    InteractableSessionCause.DISCONNECT,
    InteractableSessionCause.DEATH,
)
