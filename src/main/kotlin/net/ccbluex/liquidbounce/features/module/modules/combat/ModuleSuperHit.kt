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

import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
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
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.ccbluex.liquidbounce.utils.network.sendPacketSilently
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.ccbluex.liquidbounce.utils.render.TargetRenderer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Hits entities at extended range using several packet travel profiles. Sentinel delegates
 * a verified temporary round trip to ClickTP and returns to the starting position.
 */
@Suppress("TooManyFunctions")
object ModuleSuperHit : ClientModule("SuperHit", ModuleCategories.COMBAT, disableOnQuit = true), AStarPathBuilder {

    private val modeConfiguration = SuperHitModeConfiguration(this)
    internal val modeChoice = tree(modeConfiguration.choice)
    private val maxRange by float("MaxRange", 100f, 10f..150f).apply { tagBy(this) }
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
    private val executionMode = SuperHitExecutionMode()

    private val aStarPathContext = Dispatchers.Default + CoroutineName("$name-AStar")

    init {
        tree(TargetRenderer(this) { hoverTarget })
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

        // Prevent vanilla air swing when SuperHit handles the far target
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
                if (travelMode != SuperHitMode.ADAPTIVE) {
                    chat(markAsError("Server setback detected - SuperHit failed!"))
                }
            }
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (!shouldRenderSuperHitTracer(tracers, hoverTarget != null)) {
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
        super.onDisabled()
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacySuperHitConfig(jsonObject)
    }

    internal val maximumTargetRange: Float
        get() = maxRange

    internal fun isTargetInConfiguredRange(target: LivingEntity): Boolean {
        return isWithinSuperHitTargetRange(
            distanceSquared = player.squaredBoxedDistanceTo(target),
            minRange = minRange,
            maxRange = maxRange,
        )
    }

    internal suspend fun tryAttack(
        target: LivingEntity,
        rotation: Rotation,
        keepSprint: Boolean,
    ): Boolean {
        if (!canStartAttack(target, rotation)) {
            return false
        }

        isExecuting = true
        setbackDetected = false
        val travelMode = executionMode.capture(modeChoice.activeMode.travelMode)
        val operationGeneration = executionGeneration
        val origin = player.position()
        val targetPos = target.position()

        return try {
            when (travelMode) {
                SuperHitMode.PACKET -> executePacketHit(
                    target, origin, targetPos, rotation, keepSprint, operationGeneration, travelMode
                )
                SuperHitMode.A_STAR -> executeAStarHit(
                    target, origin, targetPos, rotation, keepSprint, operationGeneration
                )
                SuperHitMode.ADAPTIVE -> executeAdaptiveHit(
                    target, origin, targetPos, rotation, keepSprint, operationGeneration
                )
                SuperHitMode.MOTION -> executeClickTpHit(
                    target = target,
                    origin = origin,
                    targetPos = targetPos,
                    rotation = rotation,
                    keepSprint = keepSprint,
                    operationGeneration = operationGeneration,
                    transport = CubeCraftAutomationTransport.MOTION,
                    stayTicks = 0,
                )
                SuperHitMode.PULSE -> executePacketHit(
                    target, origin, targetPos, rotation, keepSprint, operationGeneration, travelMode
                )
                SuperHitMode.SENTINEL -> executeClickTpHit(
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
        } finally {
            desyncPlayerPosition = null
            isExecuting = false
            setbackDetected = false
            executionMode.clear()
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
        travelMode: SuperHitMode,
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
        val destination = calculateSuperHitDestination(
            origin = origin,
            targetPosition = targetPos,
            playerWidth = player.bbWidth.toDouble(),
            targetWidth = target.bbWidth.toDouble(),
        )
        val outward = withContext(aStarPathContext) {
            findPath(
                start = BlockPos.containing(origin),
                end = BlockPos.containing(destination),
                maxCost = modeConfiguration.aStar.maxCost,
            ).map { it.bottomCenter }
        }
        if (outward.isEmpty() || !travelPath(outward, rotation, onGround = false)) {
            return false
        }

        val attacked = attackTarget(target, outward.last(), keepSprint, operationGeneration)
        if (!setbackDetected) {
            travelPath(buildAStarReturnPath(origin, outward), rotation, onGround = false)
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
        val destination = calculateSuperHitDestination(
            origin = origin,
            targetPosition = targetPos,
            playerWidth = player.bbWidth.toDouble(),
            targetWidth = target.bbWidth.toDouble(),
        )
        val stepSizes = calculateAdaptiveStepSizes(
            initialStep = modeConfiguration.adaptive.initialStep.toDouble(),
            minimumStep = modeConfiguration.adaptive.minimumStep.toDouble(),
            retries = modeConfiguration.adaptive.retries,
        )

        return executeAdaptiveSuperHit(
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
                if (current.distanceToSqr(origin) > SENTINEL_HOME_DISTANCE_SQUARED) {
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
        val destination = calculateSuperHitDestination(
            origin = origin,
            targetPosition = targetPos,
            playerWidth = player.bbWidth.toDouble(),
            targetWidth = target.bbWidth.toDouble(),
        )

        var outcome = SentinelSuperHitOutcome.NOT_STARTED
        val sessionStarted = ModuleClickTp.runCubeCraftAutomationSession(transport) { teleport ->
            outcome = executeRoundTripSuperHit(
                origin = origin,
                destination = destination,
                stayTicks = stayTicks,
                teleport = teleport,
                shouldRecover = {
                    player.position().distanceToSqr(origin) > SENTINEL_HOME_DISTANCE_SQUARED
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

        if (!sessionStarted) {
            return false
        }
        if (!outcome.returned && player.position().distanceToSqr(origin) > SENTINEL_HOME_DISTANCE_SQUARED) {
            val transportName = if (transport == CubeCraftAutomationTransport.MOTION) "Motion" else "Sentinel"
            chat(markAsError("$transportName return failed - use ClickTP or reconnect to resync."))
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

    private fun isAttackTargetValid(target: LivingEntity, operationGeneration: Long): Boolean {
        return isExecutionActive(operationGeneration) && target.isAlive && !target.isRemoved &&
            target.shouldBeAttacked()
    }

    private fun isExecutionActive(operationGeneration: Long): Boolean {
        return running && isExecuting && !setbackDetected && executionGeneration == operationGeneration
    }

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

    private fun isAttackReady() = isSuperHitAttackReady(
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
        travelMode: SuperHitMode,
    ): Boolean {
        return when (travelMode) {
            SuperHitMode.PACKET -> travelImmediately(from, to, rotation)
            SuperHitMode.PULSE -> travelWithDelay(from, to, rotation)
            SuperHitMode.A_STAR, SuperHitMode.ADAPTIVE, SuperHitMode.MOTION, SuperHitMode.SENTINEL -> false
        }
    }

    private fun travelImmediately(from: Vec3, to: Vec3, rotation: Rotation): Boolean {
        if (setbackDetected) {
            return false
        }

        val steps = buildSuperHitTravelPath(
            SuperHitMode.PACKET,
            from,
            to,
            modeConfiguration.packet.stepSize.toDouble(),
        )
        if (steps.isEmpty()) {
            return false
        }

        var previous = from
        for (step in steps) {
            if (setbackDetected) {
                return false
            }
            travelSegment(previous, step, rotation)
            previous = step
        }

        return !setbackDetected
    }

    private suspend fun travelWithDelay(from: Vec3, to: Vec3, rotation: Rotation): Boolean {
        val steps = buildSuperHitTravelPath(
            SuperHitMode.PULSE,
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
            if (setbackDetected) {
                return false
            }

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

internal class SuperHitExecutionMode {
    var current: SuperHitMode? = null
        private set

    fun capture(configuredMode: SuperHitMode): SuperHitMode {
        check(current == null) { "A SuperHit execution mode is already captured" }
        current = configuredMode
        return configuredMode
    }

    fun clear() {
        current = null
    }
}

internal enum class SuperHitMode(
    override val tag: String,
    override val tagAliases: List<String> = emptyList(),
) : Tagged {
    PACKET("Packet", listOf("Direct", "SinglePacket")),
    A_STAR("AStar"),
    ADAPTIVE("Adaptive"),
    MOTION("Motion"),
    PULSE("Pulse"),
    SENTINEL("Sentinel", listOf("Cubecraft", "Cube Craft"));

    val usesPacketTravel: Boolean
        get() = this == PACKET || this == A_STAR || this == ADAPTIVE || this == PULSE
}

internal fun buildSuperHitTravelPath(
    mode: SuperHitMode,
    from: Vec3,
    to: Vec3,
    stepSize: Double,
): List<Vec3> = when (mode) {
    SuperHitMode.PACKET, SuperHitMode.ADAPTIVE, SuperHitMode.PULSE ->
        buildLinearTeleportPath(from, to, stepSize)
    SuperHitMode.A_STAR, SuperHitMode.MOTION, SuperHitMode.SENTINEL -> emptyList()
}

internal fun calculateAdaptiveStepSizes(
    initialStep: Double,
    minimumStep: Double,
    retries: Int,
): List<Double> {
    require(initialStep > 0.0) { "initialStep must be positive" }
    require(minimumStep > 0.0) { "minimumStep must be positive" }
    require(retries >= 0) { "retries must not be negative" }

    val effectiveMinimum = minimumStep.coerceAtMost(initialStep)
    var step = initialStep
    return List(retries + 1) {
        val current = step
        step = (step / 2.0).coerceAtLeast(effectiveMinimum)
        current
    }
}

internal suspend fun executeAdaptiveSuperHit(
    stepSizes: List<Double>,
    attempt: suspend (Double) -> Boolean,
    onAccepted: suspend (Double) -> Boolean,
    onExhausted: suspend () -> Unit,
): Boolean {
    require(stepSizes.isNotEmpty()) { "stepSizes must not be empty" }

    for (step in stepSizes) {
        if (attempt(step)) {
            return onAccepted(step)
        }
    }

    onExhausted()
    return false
}

internal fun buildAStarReturnPath(origin: Vec3, outward: List<Vec3>): List<Vec3> {
    return outward.dropLast(1).asReversed() + origin
}

internal fun isSuperHitAttackReady(usesAttackCooldown: Boolean, attackStrength: Float): Boolean {
    return !usesAttackCooldown || attackStrength > SUPER_HIT_MIN_ATTACK_STRENGTH
}

internal fun shouldRenderSuperHitTracer(tracersEnabled: Boolean, hasTarget: Boolean): Boolean {
    return tracersEnabled && hasTarget
}

internal fun isWithinSuperHitTargetRange(
    distanceSquared: Double,
    minRange: Float,
    maxRange: Float,
): Boolean {
    return distanceSquared > minRange.sq() && distanceSquared <= maxRange.sq()
}

internal fun calculateSuperHitDestination(
    origin: Vec3,
    targetPosition: Vec3,
    playerWidth: Double,
    targetWidth: Double,
): Vec3 {
    require(playerWidth >= 0.0) { "Player width must not be negative" }
    require(targetWidth >= 0.0) { "Target width must not be negative" }

    val towardOrigin = Vec3(origin.x - targetPosition.x, 0.0, origin.z - targetPosition.z)
    val direction = if (towardOrigin.lengthSqr() > SUPER_HIT_DIRECTION_EPSILON) {
        towardOrigin.normalize()
    } else {
        Vec3(1.0, 0.0, 0.0)
    }
    val collisionClearance = (playerWidth + targetWidth) / 2.0 + SUPER_HIT_COLLISION_PADDING
    val axisProjection = max(abs(direction.x), abs(direction.z))
    val clearance = collisionClearance / axisProjection

    return targetPosition.add(direction.scale(clearance))
}

internal suspend fun executeRoundTripSuperHit(
    origin: Vec3,
    destination: Vec3,
    stayTicks: Int,
    teleport: suspend (Vec3) -> Boolean,
    shouldRecover: () -> Boolean,
    synchronizeRotation: () -> Unit,
    attack: () -> Boolean,
    wait: suspend (Int) -> Unit,
): SentinelSuperHitOutcome {
    require(stayTicks >= 0) { "stayTicks must not be negative" }

    if (!teleport(destination)) {
        val recovered = shouldRecover() && withContext(NonCancellable) { teleport(origin) }
        return SentinelSuperHitOutcome(attacked = false, returned = recovered)
    }

    var attacked = false
    var returned = false
    try {
        synchronizeRotation()
        attacked = attack()
        if (attacked && stayTicks > 0) {
            wait(stayTicks)
        }
    } finally {
        returned = withContext(NonCancellable) { teleport(origin) }
    }

    return SentinelSuperHitOutcome(attacked = attacked, returned = returned)
}

internal data class SentinelSuperHitOutcome(val attacked: Boolean, val returned: Boolean) {
    companion object {
        val NOT_STARTED = SentinelSuperHitOutcome(attacked = false, returned = false)
    }
}

private const val SUPER_HIT_MIN_ATTACK_STRENGTH = 0.9f
private const val SUPER_HIT_COLLISION_PADDING = 0.1
private const val SUPER_HIT_DIRECTION_EPSILON = 1.0E-9
private const val SENTINEL_HOME_DISTANCE_SQUARED = 4.0
