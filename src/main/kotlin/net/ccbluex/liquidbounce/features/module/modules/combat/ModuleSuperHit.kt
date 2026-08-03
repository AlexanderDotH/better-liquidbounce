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

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.exploit.ModuleClickTp
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.markAsError
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.hasCooldown
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.input.InputTracker.wasPressedRecently
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.movement.buildLinearTeleportPath
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.ccbluex.liquidbounce.utils.network.sendPacketSilently
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.ccbluex.liquidbounce.utils.render.TargetRenderer
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Hits entities at extended range. Packet mode spoofs a round trip, while Sentinel
 * delegates a real forward teleport to ClickTP and leaves the player beside the target.
 */
object ModuleSuperHit : ClientModule("SuperHit", ModuleCategories.COMBAT, disableOnQuit = true) {

    private val mode by enumChoice("Mode", SuperHitMode.PACKET)
    private val maxRange by float("MaxRange", 100f, 10f..150f).apply { tagBy(this) }
    private val minRange by float("MinRange", 3.0f, 0f..6f)
    private val stepSize by float("StepSize", 10f, 1f..20f)
    private val attackRange by float("AttackRange", 4.2f, 3f..5f)

    var desyncPlayerPosition: Vec3? = null
        private set

    var hoverTarget: LivingEntity? = null
        private set

    private var isExecuting = false
    private var setbackDetected = false

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

        if (isExecuting || !isAttackReady()) {
            return@tickHandler
        }

        val target = resolveCrosshairTarget() ?: return@tickHandler

        isExecuting = true
        setbackDetected = false

        val origin = player.position()
        val targetPos = target.position()

        try {
            when (mode) {
                SuperHitMode.PACKET -> executePacketHit(target, origin, targetPos)
                SuperHitMode.SENTINEL -> executeSentinelHit(target, origin, targetPos)
            }
        } finally {
            desyncPlayerPosition = null
            isExecuting = false
            setbackDetected = false
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> {
        if (mode != SuperHitMode.PACKET) {
            return@handler
        }

        val packet = it.packet

        when (packet) {
            is ServerboundMovePlayerPacket -> {
                val position = desyncPlayerPosition ?: return@handler

                packet.x = position.x
                packet.y = position.y
                packet.z = position.z
                packet.hasPos = true
            }
            is ClientboundPlayerPositionPacket -> {
                if (!isExecuting && desyncPlayerPosition == null) {
                    return@handler
                }

                setbackDetected = true
                desyncPlayerPosition = null
                chat(markAsError("Server setback detected - SuperHit failed!"))
                isExecuting = false
            }
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
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
        desyncPlayerPosition = null
        hoverTarget = null
        isExecuting = false
        setbackDetected = false
        super.onDisabled()
    }

    private fun executePacketHit(target: LivingEntity, origin: Vec3, targetPos: Vec3) {
        travelSteps(origin, targetPos)
        attackTarget(target, targetPos)

        if (!setbackDetected) {
            travelSteps(targetPos, origin)
        }
    }

    private suspend fun executeSentinelHit(target: LivingEntity, origin: Vec3, targetPos: Vec3) {
        val destination = calculateSuperHitDestination(
            origin = origin,
            targetPosition = targetPos,
            playerWidth = player.bbWidth.toDouble(),
            targetWidth = target.bbWidth.toDouble(),
        )

        executeSentinelSuperHit(
            destination = destination,
            teleport = ModuleClickTp::teleportCubeCraftPacket,
            attack = { attackTarget(target, player.position()) },
        )
    }

    private fun attackTarget(target: LivingEntity, fallbackPosition: Vec3) {
        if (setbackDetected || !target.isAlive || target.isRemoved) {
            return
        }

        val attackPosition = desyncPlayerPosition ?: fallbackPosition
        if (target.squaredBoxedDistanceTo(attackPosition) <= attackRange * attackRange) {
            attackEntity(target, SwingMode.DO_NOT_HIDE, keepSprint = true)
        }
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

    private fun travelSteps(from: Vec3, to: Vec3) {
        if (setbackDetected) {
            return
        }

        val steps = buildLinearTeleportPath(from, to, stepSize.toDouble())
        if (steps.isEmpty()) {
            return
        }

        var previous = from
        for (step in steps) {
            if (setbackDetected) {
                return
            }
            travelSegment(previous, step)
            previous = step
        }
    }

    private fun travelSegment(from: Vec3, to: Vec3) {
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
                yRot = player.yRot
                xRot = player.xRot
                onGround = player.onGround()
            })
        }

        sendPacketSilently(packetToSend.generatePacket().apply {
            x = to.x
            y = to.y
            z = to.z
            yRot = player.yRot
            xRot = player.xRot
            onGround = player.onGround()
        })

        desyncPlayerPosition = to
    }

}

internal enum class SuperHitMode(
    override val tag: String,
    override val tagAliases: List<String> = emptyList(),
) : Tagged {
    PACKET("Packet"),
    SENTINEL("Sentinel", listOf("Cubecraft", "Cube Craft")),
}

internal fun isSuperHitAttackReady(usesAttackCooldown: Boolean, attackStrength: Float): Boolean {
    return !usesAttackCooldown || attackStrength > SUPER_HIT_MIN_ATTACK_STRENGTH
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

internal suspend fun executeSentinelSuperHit(
    destination: Vec3,
    teleport: suspend (Vec3) -> Boolean,
    attack: () -> Unit,
): Boolean {
    if (!teleport(destination)) {
        return false
    }

    attack()
    return true
}

private const val SUPER_HIT_MIN_ATTACK_STRENGTH = 0.9f
private const val SUPER_HIT_COLLISION_PADDING = 0.1
private const val SUPER_HIT_DIRECTION_EPSILON = 1.0E-9
