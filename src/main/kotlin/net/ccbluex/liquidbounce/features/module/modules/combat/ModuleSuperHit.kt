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
import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.markAsError
import net.ccbluex.liquidbounce.utils.combat.TargetPriority
import net.ccbluex.liquidbounce.utils.combat.TargetSelector
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.entity.hasCooldown
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.input.InputTracker.wasPressedRecently
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.math.toVec3f
import net.ccbluex.liquidbounce.utils.movement.buildLinearTeleportPath
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.ccbluex.liquidbounce.utils.network.sendPacketSilently
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.ccbluex.liquidbounce.utils.render.TargetRenderer
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor

/**
 * Hits entities at extended range by stepping position packets to the target,
 * attacking, then stepping back to the origin.
 */
object ModuleSuperHit : ClientModule("SuperHit", ModuleCategories.COMBAT, disableOnQuit = true) {

    private val mode by enumChoice("Mode", SuperHitMode.PACKET)
    private val maxRange by float("MaxRange", 100f, 10f..150f).apply { tagBy(this) }
    private val minRange by float("MinRange", 3.0f, 0f..6f)
    private val stepSize by float("StepSize", 10f, 1f..20f)
    private val attackRange by float("AttackRange", 4.2f, 3f..5f)
    private val cubeCraftStep by float("CubeCraftStep", 4.0f, 0.25f..10f, "blocks")
    private val cubeCraftMaxPackets by int("CubeCraftMaxPackets", 120, 1..500)
    private val cubeCraftReleaseDelay by int("CubeCraftReleaseDelay", 1, 0..5, "ticks")
    private val cubeCraftGround by boolean("CubeCraftGround", true)

    private val targetSelector = tree(TargetSelector(TargetPriority.HURT_TIME))

    var desyncPlayerPosition: Vec3? = null
        private set

    var hoverTarget: LivingEntity? = null
        private set

    private var isExecuting = false
    private var setbackDetected = false
    private var requiresBlink = false
    private var sendingTeleportPacket = false

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
        if (player.hasCooldown) {
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

        if (isExecuting || player.hasCooldown) {
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
                SuperHitMode.CUBECRAFT -> executeCubeCraftHit(target, origin, targetPos)
            }
        } finally {
            requiresBlink = false
            desyncPlayerPosition = null
            isExecuting = false
            setbackDetected = false
            sendingTeleportPacket = false
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> {
        val packet = it.packet

        when (packet) {
            is ServerboundMovePlayerPacket -> {
                if (sendingTeleportPacket) {
                    return@handler
                }

                val position = desyncPlayerPosition ?: return@handler

                packet.x = position.x
                packet.y = position.y
                packet.z = position.z
                packet.hasPos = true
            }
            is ClientboundPlayerPositionPacket -> {
                if (!isExecuting && desyncPlayerPosition == null && !requiresBlink) {
                    return@handler
                }

                setbackDetected = true
                desyncPlayerPosition = null

                if (mode != SuperHitMode.CUBECRAFT) {
                    chat(markAsError("Server setback detected - SuperHit failed!"))
                    isExecuting = false
                }
            }
        }
    }

    @Suppress("unused")
    private val fakeLagHandler = handler<BlinkPacketEvent> { event ->
        if (event.origin == TransferOrigin.OUTGOING && requiresBlink) {
            event.action = BlinkManager.Action.QUEUE
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val target = hoverTarget ?: return@handler

        renderEnvironmentForWorld(event.matrixStack) {
            drawLine(
                relativeToCamera(player.position().add(0.0, 1.0, 0.0)).toVec3f(),
                relativeToCamera(target.position().add(0.0, 1.0, 0.0)).toVec3f(),
                Color4b.WHITE.argb,
            )
        }
    }

    override fun onDisabled() {
        desyncPlayerPosition = null
        hoverTarget = null
        isExecuting = false
        setbackDetected = false
        requiresBlink = false
        sendingTeleportPacket = false
        super.onDisabled()
    }

    private fun executePacketHit(target: LivingEntity, origin: Vec3, targetPos: Vec3) {
        travelSteps(origin, targetPos)
        attackTarget(target, targetPos)

        if (!setbackDetected) {
            travelSteps(targetPos, origin)
        }
    }

    private suspend fun executeCubeCraftHit(target: LivingEntity, origin: Vec3, targetPos: Vec3) {
        requiresBlink = true

        teleportCubeCraft(targetPos, stopOnSetback = true)
        attackTarget(target, targetPos)

        // Cubecraft mode uses real client teleports, so return explicitly instead of relying on a setback.
        setbackDetected = false
        teleportCubeCraft(origin, stopOnSetback = false)

        if (cubeCraftReleaseDelay > 0) {
            waitTicks(cubeCraftReleaseDelay)
        }
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

    private fun resolveCrosshairTarget(): LivingEntity? {
        val rotation = RotationManager.currentRotation ?: player.rotation
        val hitResult = findEntityInCrosshair(maxRange.toDouble(), rotation) { entity ->
            entity is LivingEntity && targetSelector.validate(entity)
        } ?: return null

        val entity = hitResult.entity as? LivingEntity ?: return null
        val distanceSq = player.squaredBoxedDistanceTo(entity)

        if (distanceSq <= minRange.sq() || distanceSq > maxRange.sq()) {
            return null
        }

        return isLookingAtEntity(
            toEntity = entity,
            rotation = rotation,
            range = maxRange.toDouble(),
            throughWallsRange = 0.0,
        )?.entity as? LivingEntity
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

    private fun teleportCubeCraft(pos: Vec3, stopOnSetback: Boolean) {
        travelCubeCraft(
            from = player.position(),
            to = pos,
            stopOnSetback = stopOnSetback,
        )
        player.setPos(pos)
        desyncPlayerPosition = pos
    }

    private fun travelCubeCraft(from: Vec3, to: Vec3, stopOnSetback: Boolean) {
        val path = buildLinearTeleportPath(
            from = from,
            to = to,
            stepDistance = cubeCraftStep.toDouble(),
            maxPackets = cubeCraftMaxPackets,
        )

        for (step in path) {
            if (stopOnSetback && setbackDetected) {
                return
            }

            sendCubeCraftMovePacket(step)
            desyncPlayerPosition = step
        }
    }

    private fun sendCubeCraftMovePacket(pos: Vec3) {
        sendingTeleportPacket = true
        try {
            network.send(
                ServerboundMovePlayerPacket.PosRot(
                    pos.x,
                    pos.y,
                    pos.z,
                    player.yRot,
                    player.xRot,
                    cubeCraftGround,
                    player.horizontalCollision,
                )
            )
        } finally {
            sendingTeleportPacket = false
        }
    }

    private enum class SuperHitMode(
        override val tag: String,
        override val tagAliases: List<String> = emptyList(),
    ) : Tagged {
        PACKET("Packet"),
        CUBECRAFT("Cubecraft", listOf("Cube Craft")),
    }

}
