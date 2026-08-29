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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.exploit.CubeCraftAutomationTransport
import net.ccbluex.liquidbounce.features.module.modules.exploit.ModuleClickTp
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.AStarPathBuilder
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.markAsError
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.hasCooldown
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.input.InputTracker.wasPressedRecently
import net.ccbluex.liquidbounce.utils.math.bottomCenter
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.movement.buildLinearTeleportPath
import net.ccbluex.liquidbounce.utils.movement.remote.RemoteMovementOwnership
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.ccbluex.liquidbounce.utils.network.sendPacketSilently
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.ccbluex.liquidbounce.utils.render.TargetRenderer
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor

/**
 * Optional Reach feature that hits entities at extended range using the former SuperHit travel profiles.
 */
@Suppress("TooManyFunctions")
class ReachHit(
    parent: EventListener?,
    includeTargetRenderer: Boolean = Minecraft.getInstance() != null,
) : ToggleableValueGroup(parent, "Hit", enabled = false), AStarPathBuilder {

    private val modeConfiguration = ReachHitModeConfiguration(this)
    internal val modeChoice = tree(modeConfiguration.choice)
    private val maxRange by float("MaxRange", 100f, 10f..150f)
    private val minRange by float("MinRange", 3.0f, 0f..6f)
    private val attackRange by float("AttackRange", 4.2f, 3f..5f)
    private val tracers by boolean("Tracers", false)

    override val allowDiagonal: Boolean
        get() = modeConfiguration.aStar.diagonal

    var desyncPlayerPosition: Vec3? = null
        private set

    var hoverTarget: LivingEntity? = null
        private set

    private var isExecuting = false
    private var setbackDetected = false
    private var executionGeneration = 0L
    private val executionMode = ReachHitExecutionMode()
    private val automaticRetryGate = ReachHitAutomaticRetryGate(REACH_HIT_AUTOMATIC_RETRY_DELAY_TICKS)
    init {
        if (includeTargetRenderer) {
            tree(TargetRenderer(this) { hoverTarget })
        }
    }

    @Suppress("unused")
    private val hoverHandler = handler<GameTickEvent> {
        if (isExecuting) {
            return@handler
        }

        hoverTarget = resolveCrosshairTarget()
    }

    @Suppress("unused")
    private val attackKeyHandler = handler<KeybindIsPressedEvent> { event ->
        if (event.keyBinding != mc.options.keyAttack || isExecuting) {
            return@handler
        }

        resolveCrosshairTarget() ?: return@handler
        if (!isAttackReady()) {
            return@handler
        }

        event.isPressed = false
    }

    @Suppress("unused")
    private val attackHandler = tickHandler {
        if (!mc.options.keyAttack.wasPressedRecently(250)) {
            return@tickHandler
        }

        val target = resolveCrosshairTarget() ?: return@tickHandler
        tryAttack(target, player.rotation, keepSprint = true)
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> {
        val packet = it.packet
        val travelMode = executionMode.current ?: return@handler

        when (packet) {
            is ServerboundMovePlayerPacket -> {
                if (!travelMode.usesPacketTravel) {
                    return@handler
                }
                val position = desyncPlayerPosition ?: return@handler

                packet.x = position.x
                packet.y = position.y
                packet.z = position.z
                packet.hasPos = true
            }
            is ClientboundPlayerPositionPacket -> {
                if (!travelMode.usesPacketTravel || !isExecuting && desyncPlayerPosition == null) {
                    return@handler
                }
                if (setbackDetected) {
                    return@handler
                }

                setbackDetected = true
                desyncPlayerPosition = null
                if (travelMode != ReachHitMode.ADAPTIVE) {
                    chat(markAsError("Server setback detected, Reach Hit failed!"))
                }
            }
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (!shouldRenderReachHitTracer(tracers, hoverTarget != null)) {
            return@handler
        }

        val target = hoverTarget ?: return@handler

        event.renderEnvironment {
            val cameraPosition = camera.position()
            drawLine(
                player.position().add(0.0, 1.0, 0.0).subtract(cameraPosition),
                target.position().add(0.0, 1.0, 0.0).subtract(cameraPosition),
                Color4b.WHITE.argb,
            )
        }
    }

    override fun onDisabled() {
        executionGeneration++
        desyncPlayerPosition = null
        hoverTarget = null
        setbackDetected = isExecuting
        automaticRetryGate.clear()
        super.onDisabled()
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacyReachHitConfig(jsonObject)
    }

    internal val maximumTargetRange: Float
        get() = maxRange

    internal fun isTargetInConfiguredRange(target: LivingEntity): Boolean =
        isWithinReachHitTargetRange(
            distanceSquared = player.squaredBoxedDistanceTo(target),
            minRange = minRange,
            maxRange = maxRange,
        )

    internal suspend fun tryAttack(
        target: LivingEntity,
        rotation: Rotation,
        keepSprint: Boolean,
        automatedByKillAura: Boolean = false,
    ): Boolean {
        val attemptTick = player.tickCount
        if (automatedByKillAura && !automaticRetryGate.canAttempt(target.id, attemptTick)) {
            return false
        }
        if (!canStartAttack(target, rotation)) {
            return false
        }

        val movementLease = RemoteMovementOwnership.tryAcquire(REACH_HIT_MOVEMENT_OWNER) ?: return false
        var success = false
        try {
            isExecuting = true
            setbackDetected = false
            val travelMode = executionMode.capture(modeChoice.activeMode.travelMode)
            val operationGeneration = executionGeneration
            val origin = player.position()
            val targetPos = target.position()

            success = executeAttack(
                target = target,
                origin = origin,
                targetPos = targetPos,
                rotation = rotation,
                keepSprint = keepSprint,
                operationGeneration = operationGeneration,
                travelMode = travelMode,
            )
        } finally {
            try {
                finishAttack(target.id, attemptTick, automatedByKillAura, success)
            } finally {
                movementLease.close()
            }
        }
        return success
    }

    private suspend fun executeAttack(
        target: LivingEntity,
        origin: Vec3,
        targetPos: Vec3,
        rotation: Rotation,
        keepSprint: Boolean,
        operationGeneration: Long,
        travelMode: ReachHitMode,
    ): Boolean = when (travelMode) {
        ReachHitMode.PACKET -> executePacketHit(
            target, origin, targetPos, rotation, keepSprint, operationGeneration, travelMode,
        )
        ReachHitMode.A_STAR -> executeAStarHit(
            target, origin, targetPos, rotation, keepSprint, operationGeneration,
        )
        ReachHitMode.ADAPTIVE -> executeAdaptiveHit(
            target, origin, targetPos, rotation, keepSprint, operationGeneration,
        )
        ReachHitMode.MOTION -> executeClickTpHit(
            target = target,
            origin = origin,
            targetPos = targetPos,
            rotation = rotation,
            keepSprint = keepSprint,
            operationGeneration = operationGeneration,
            transport = CubeCraftAutomationTransport.MOTION,
            stayTicks = 0,
        )
        ReachHitMode.PULSE -> executePacketHit(
            target, origin, targetPos, rotation, keepSprint, operationGeneration, travelMode,
        )
        ReachHitMode.SENTINEL -> executeClickTpHit(
            target = target,
            origin = origin,
            targetPos = targetPos,
            rotation = rotation,
            keepSprint = keepSprint,
            operationGeneration = operationGeneration,
            transport = CubeCraftAutomationTransport.PACKET,
            stayTicks = modeConfiguration.sentinel.stayTicks,
        )
    }

    private fun finishAttack(
        targetId: Int,
        attemptTick: Int,
        automatedByKillAura: Boolean,
        success: Boolean,
    ) {
        desyncPlayerPosition = null
        isExecuting = false
        setbackDetected = false
        executionMode.clear()
        if (!automatedByKillAura) return

        if (success) {
            automaticRetryGate.recordSuccess()
        } else {
            automaticRetryGate.recordFailure(targetId, attemptTick)
        }
    }

    private fun canStartAttack(target: LivingEntity, rotation: Rotation): Boolean {
        if (!running || isExecuting || !isAttackReady() || !isTargetInConfiguredRange(target)) {
            return false
        }
        if (!target.isAlive || target.isRemoved || !target.shouldBeAttacked()) {
            return false
        }

        return isLookingAtEntity(
            toEntity = target,
            rotation = rotation,
            range = maxRange.toDouble(),
            throughWallsRange = 0.0,
        ) != null
    }

    private suspend fun executePacketHit(
        target: LivingEntity,
        origin: Vec3,
        destination: Vec3,
        rotation: Rotation,
        keepSprint: Boolean,
        operationGeneration: Long,
        travelMode: ReachHitMode,
    ): Boolean {
        if (!travel(origin, destination, rotation, travelMode)) {
            return false
        }

        val attacked = attackTarget(target, destination, keepSprint, operationGeneration)
        if (!setbackDetected) {
            travel(destination, origin, rotation, travelMode)
        }
        return attacked
    }

    private suspend fun executeAStarHit(
        target: LivingEntity,
        origin: Vec3,
        targetPos: Vec3,
        rotation: Rotation,
        keepSprint: Boolean,
        operationGeneration: Long,
    ): Boolean {
        val destination = calculateReachHitDestination(
            origin = origin,
            targetPosition = targetPos,
            playerWidth = player.bbWidth.toDouble(),
            targetWidth = target.bbWidth.toDouble(),
        )
        val outward = findPath(
            start = BlockPos.containing(origin),
            end = BlockPos.containing(destination),
            maxCost = modeConfiguration.aStar.maxCost,
        ).map { it.bottomCenter }
        if (outward.isEmpty() || !travelPath(outward, rotation, onGround = false)) {
            return false
        }

        val attacked = attackTarget(target, outward.last(), keepSprint, operationGeneration)
        if (!setbackDetected) {
            travelPath(buildReachHitAStarReturnPath(origin, outward), rotation, onGround = false)
        }
        return attacked
    }

    private suspend fun executeAdaptiveHit(
        target: LivingEntity,
        origin: Vec3,
        targetPos: Vec3,
        rotation: Rotation,
        keepSprint: Boolean,
        operationGeneration: Long,
    ): Boolean {
        val destination = calculateReachHitDestination(
            origin = origin,
            targetPosition = targetPos,
            playerWidth = player.bbWidth.toDouble(),
            targetWidth = target.bbWidth.toDouble(),
        )
        val stepSizes = calculateReachHitAdaptiveStepSizes(
            initialStep = modeConfiguration.adaptive.initialStep.toDouble(),
            minimumStep = modeConfiguration.adaptive.minimumStep.toDouble(),
            retries = modeConfiguration.adaptive.retries,
        )

        return executeAdaptiveReachHit(
            stepSizes = stepSizes,
            attempt = { attemptStep ->
                setbackDetected = false
                desyncPlayerPosition = null
                val path = buildLinearTeleportPath(player.position(), destination, attemptStep)
                travelPath(path, rotation) && waitForAdaptiveAcceptance()
            },
            onAccepted = { acceptedStep ->
                val attacked = attackTarget(target, destination, keepSprint, operationGeneration)
                if (attacked && !setbackDetected) {
                    val returnPath = buildLinearTeleportPath(destination, origin, acceptedStep)
                    travelPath(returnPath, rotation)
                    waitForAdaptiveAcceptance()
                }
                attacked
            },
            onExhausted = {
                chat(markAsError("Adaptive route was rejected after all smaller-step retries."))
                setbackDetected = false
                desyncPlayerPosition = null
                val current = player.position()
                if (current.distanceToSqr(origin) > REACH_HIT_HOME_DISTANCE_SQUARED) {
                    val recovery = buildLinearTeleportPath(current, origin, stepSizes.last())
                    travelPath(recovery, rotation)
                }
            },
        )
    }

    private suspend fun waitForAdaptiveAcceptance(): Boolean {
        waitTicks(modeConfiguration.adaptive.verifyTicks)
        return !setbackDetected
    }

    private suspend fun executeClickTpHit(
        target: LivingEntity,
        origin: Vec3,
        targetPos: Vec3,
        rotation: Rotation,
        keepSprint: Boolean,
        operationGeneration: Long,
        transport: CubeCraftAutomationTransport,
        stayTicks: Int,
    ): Boolean {
        val destination = calculateReachHitDestination(
            origin = origin,
            targetPosition = targetPos,
            playerWidth = player.bbWidth.toDouble(),
            targetWidth = target.bbWidth.toDouble(),
        )

        var outcome = ReachHitRoundTripOutcome.NOT_STARTED
        val sessionStarted = ModuleClickTp.runCubeCraftAutomationSession(
            transport = transport,
            inheritedMovementOwner = REACH_HIT_MOVEMENT_OWNER,
        ) { teleport ->
            outcome = executeRoundTripReachHit(
                origin = origin,
                destination = destination,
                stayTicks = stayTicks,
                teleport = teleport,
                shouldRecover = {
                    player.position().distanceToSqr(origin) > REACH_HIT_HOME_DISTANCE_SQUARED
                },
                synchronizeRotation = {
                    if (isExecutionActive(operationGeneration)) {
                        sendRotation(rotation)
                    }
                },
                attack = {
                    attackTarget(target, player.position(), keepSprint, operationGeneration)
                },
                wait = ::waitTicks,
            )
        }

        if (!sessionStarted) return false
        if (!outcome.returned && player.position().distanceToSqr(origin) > REACH_HIT_HOME_DISTANCE_SQUARED) {
            val transportName = if (transport == CubeCraftAutomationTransport.MOTION) "Motion" else "Sentinel"
            chat(markAsError("$transportName return failed, use ClickTP or reconnect to resync."))
        }
        return outcome.attacked
    }

    private fun attackTarget(
        target: LivingEntity,
        fallbackPosition: Vec3,
        keepSprint: Boolean,
        operationGeneration: Long,
    ): Boolean {
        if (!isAttackTargetValid(target, operationGeneration)) {
            return false
        }

        val attackPosition = desyncPlayerPosition ?: fallbackPosition
        if (target.squaredBoxedDistanceTo(attackPosition) > attackRange * attackRange) {
            return false
        }

        attackEntity(target, SwingMode.DO_NOT_HIDE, keepSprint)
        return true
    }

    private fun isAttackTargetValid(target: LivingEntity, operationGeneration: Long): Boolean =
        isExecutionActive(operationGeneration) && target.isAlive && !target.isRemoved && target.shouldBeAttacked()

    private fun isExecutionActive(operationGeneration: Long): Boolean =
        running && isExecuting && !setbackDetected && executionGeneration == operationGeneration

    private fun sendRotation(rotation: Rotation) {
        sendPacketSilently(MovePacketType.FULL.generatePacket().apply {
            x = player.x
            y = player.y
            z = player.z
            yRot = rotation.yaw
            xRot = rotation.pitch
            onGround = player.onGround()
        })
    }

    private fun isAttackReady() = isReachHitAttackReady(
        usesAttackCooldown = player.hasCooldown,
        attackStrength = player.getAttackStrengthScale(0.5f),
    )

    private fun resolveCrosshairTarget(): LivingEntity? {
        val camera = mc.cameraEntity ?: return null
        val hitResult = findEntityInCrosshair(maxRange.toDouble(), player.rotation) ?: return null
        val entity = hitResult.entity as? LivingEntity ?: return null
        val distanceSq = player.squaredBoxedDistanceTo(entity)

        if (!entity.shouldBeAttacked() || distanceSq <= minRange.sq() || distanceSq > maxRange.sq()) {
            return null
        }
        return entity.takeIf { hasLineOfSight(camera.eyePosition, hitResult.location, camera) }
    }

    private suspend fun travel(
        from: Vec3,
        to: Vec3,
        rotation: Rotation,
        travelMode: ReachHitMode,
    ): Boolean = when (travelMode) {
        ReachHitMode.PACKET -> travelImmediately(from, to, rotation)
        ReachHitMode.PULSE -> travelWithDelay(from, to, rotation)
        ReachHitMode.A_STAR, ReachHitMode.ADAPTIVE, ReachHitMode.MOTION, ReachHitMode.SENTINEL -> false
    }

    private fun travelImmediately(from: Vec3, to: Vec3, rotation: Rotation): Boolean {
        if (setbackDetected) return false

        val steps = buildReachHitTravelPath(
            ReachHitMode.PACKET,
            from,
            to,
            modeConfiguration.packet.stepSize.toDouble(),
        )
        if (steps.isEmpty()) return false

        var previous = from
        for (step in steps) {
            if (setbackDetected) return false
            travelSegment(previous, step, rotation)
            previous = step
        }
        return !setbackDetected
    }

    private suspend fun travelWithDelay(from: Vec3, to: Vec3, rotation: Rotation): Boolean {
        val steps = buildReachHitTravelPath(
            ReachHitMode.PULSE,
            from,
            to,
            modeConfiguration.pulse.stepSize.toDouble(),
        )
        return travelPath(steps, rotation, delayTicks = modeConfiguration.pulse.delay)
    }

    private suspend fun travelPath(
        path: List<Vec3>,
        rotation: Rotation,
        delayTicks: Int = 0,
        onGround: Boolean = player.onGround(),
    ): Boolean {
        for ((index, step) in path.withIndex()) {
            if (setbackDetected) return false

            sendPosition(step, rotation, onGround)
            if (delayTicks > 0 && index < path.lastIndex) {
                waitTicks(delayTicks)
            }
        }
        return !setbackDetected
    }

    private fun travelSegment(from: Vec3, to: Vec3, rotation: Rotation) {
        val deltaX = to.x - from.x
        val deltaY = to.y - from.y
        val deltaZ = to.z - from.z
        val times = (floor((abs(deltaX) + abs(deltaY) + abs(deltaZ)) / 10) - 1).toInt().coerceAtLeast(0)
        val packetToSend = MovePacketType.FULL

        repeat(times) {
            sendPacketSilently(packetToSend.generatePacket().apply {
                x = from.x
                y = from.y
                z = from.z
                yRot = rotation.yaw
                xRot = rotation.pitch
                onGround = player.onGround()
            })
        }

        sendPacketSilently(packetToSend.generatePacket().apply {
            x = to.x
            y = to.y
            z = to.z
            yRot = rotation.yaw
            xRot = rotation.pitch
            onGround = player.onGround()
        })
        desyncPlayerPosition = to
    }

    private fun sendPosition(position: Vec3, rotation: Rotation, onGround: Boolean = player.onGround()) {
        sendPacketSilently(MovePacketType.FULL.generatePacket().apply {
            x = position.x
            y = position.y
            z = position.z
            yRot = rotation.yaw
            xRot = rotation.pitch
            this.onGround = onGround
        })
        desyncPlayerPosition = position
    }
}
