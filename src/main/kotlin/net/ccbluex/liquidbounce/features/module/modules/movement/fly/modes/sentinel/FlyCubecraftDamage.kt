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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentinel

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * CubeCraft damage boost with a client-only look direction and a separately spoofed server yaw.
 */
internal object FlyCubecraftDamage : Mode("CubecraftDamage") {

    private val damageMethod by enumChoice("DamageMethod", CubecraftSelfDamageMethod.VERUS)
    private val damageTimeout by int("DamageTimeout", 30, 5..60, "ticks")
    private val horizontalBoost by float("HorizontalBoost", 0.4f, 0.1f..3f)
    private val verticalBoost by float("VerticalBoost", 0.4f, 0f..1.5f)

    private object FakeStrafe : ToggleableValueGroup(this@FlyCubecraftDamage, "FakeStrafe", true) {
        val serverYawOffset by float("ServerYawOffset", 180f, 0f..180f, "degrees")
    }

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    init {
        tree(FakeStrafe)
    }

    private lateinit var cycle: CubecraftDamageFlyCycle
    private var damageConfirmed = false
    private var velocityRedirected = false

    override fun enable() {
        if (ModuleSpeed.enabled) {
            ModuleSpeed.enabled = false
        }

        cycle = CubecraftDamageFlyCycle(player.y, damageTimeout)
        damageConfirmed = false
        velocityRedirected = false
        super.enable()
    }

    override fun disable() {
        val restoreYaw = ::cycle.isInitialized && cycle.spoofServerYaw
        if (::cycle.isInitialized) {
            cycle.cancel()
        }

        if (restoreYaw) {
            restoreServerYaw()
        }

        damageConfirmed = false
        velocityRedirected = false
        super.disable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!::cycle.isInitialized || player.isDeadOrDying) {
            return@tickHandler
        }

        val action = cycle.tick(player.y, player.hurtTime, damageConfirmed)
        damageConfirmed = false

        when (action) {
            CubecraftDamageFlyAction.NONE -> Unit
            CubecraftDamageFlyAction.TRIGGER_DAMAGE -> {
                velocityRedirected = false
                selfDamage()
            }
            CubecraftDamageFlyAction.APPLY_BOOST -> {
                if (!velocityRedirected) {
                    player.deltaMovement = redirectKnockback(player.deltaMovement)
                }

                velocityRedirected = false
            }
            CubecraftDamageFlyAction.RESTORE_YAW -> restoreServerYaw()
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(priority = EventPriorityConvention.FINAL_DECISION) { event ->
        if (!::cycle.isInitialized) {
            return@handler
        }

        when (val packet = event.packet) {
            is ServerboundMovePlayerPacket -> {
                val damageWindow = cycle.spoofServerYaw || damageConfirmed || player.hurtTime > 0
                if (event.origin == TransferOrigin.OUTGOING && damageWindow && packet.hasRot && FakeStrafe.running) {
                    packet.yRot = cubecraftDamageServerYaw(
                        clientYaw = player.yRot,
                        fakeStrafe = true,
                        yawOffset = FakeStrafe.serverYawOffset,
                    )
                }
            }
            is ClientboundDamageEventPacket -> {
                if (!event.isCancelled && event.origin == TransferOrigin.INCOMING && packet.entityId == player.id) {
                    damageConfirmed = true
                }
            }
            is ClientboundSetEntityMotionPacket -> {
                if (event.isCancelled || event.origin != TransferOrigin.INCOMING || packet.id != player.id) {
                    return@handler
                }

                if (!cycle.acceptsVelocity && !damageConfirmed && player.hurtTime <= 0) {
                    return@handler
                }

                val redirected = redirectKnockback(packet.movement)
                packet.movement.x = redirected.x
                packet.movement.y = redirected.y
                packet.movement.z = redirected.z
                velocityRedirected = true
            }
        }
    }

    private fun selfDamage() {
        network.send(
            ServerboundMovePlayerPacket.PosRot(
                player.x,
                player.y,
                player.z,
                cubecraftDamageServerYaw(player.yRot, FakeStrafe.running, FakeStrafe.serverYawOffset),
                player.xRot,
                player.onGround(),
                player.horizontalCollision,
            )
        )

        when (damageMethod) {
            CubecraftSelfDamageMethod.VERUS -> damageVerus()
            CubecraftSelfDamageMethod.SENTINEL -> damageSentinel()
        }
    }

    private fun damageVerus() {
        sendPosition(player.y, onGround = false)
        sendPosition(player.y + VERUS_VERTICAL_OFFSET, onGround = false)
        sendPosition(player.y, onGround = false)
        sendPosition(player.y, onGround = true)
    }

    private fun damageSentinel() {
        val baseY = player.y
        var offsetY = SENTINEL_INITIAL_OFFSET
        var motionY = 0.0

        while (offsetY > 0.0) {
            sendPosition(baseY + offsetY, onGround = offsetY == SENTINEL_INITIAL_OFFSET)
            offsetY += motionY
            motionY = (motionY - SENTINEL_GRAVITY) * SENTINEL_DRAG
        }

        sendPosition(baseY, onGround = true)
    }

    private fun sendPosition(y: Double, onGround: Boolean) {
        network.send(
            ServerboundMovePlayerPacket.Pos(
                player.x,
                y,
                player.z,
                onGround,
                player.horizontalCollision,
            )
        )
    }

    private fun redirectKnockback(velocity: Vec3): Vec3 {
        return redirectCubecraftDamageKnockback(
            velocity = velocity,
            clientYaw = player.yRot,
            minimumHorizontalSpeed = horizontalBoost.toDouble(),
            minimumVerticalSpeed = verticalBoost.toDouble(),
        )
    }

    private fun restoreServerYaw() {
        network.send(
            ServerboundMovePlayerPacket.PosRot(
                player.x,
                player.y,
                player.z,
                player.yRot,
                player.xRot,
                player.onGround(),
                player.horizontalCollision,
            )
        )
    }

    private const val VERUS_VERTICAL_OFFSET = 3.25
    private const val SENTINEL_INITIAL_OFFSET = 4.0
    private const val SENTINEL_GRAVITY = 0.08
    private const val SENTINEL_DRAG = 0.98

}

internal enum class CubecraftSelfDamageMethod(override val tag: String) : Tagged {
    VERUS("Verus"),
    SENTINEL("Sentinel"),
}

internal enum class CubecraftDamageFlyAction {
    NONE,
    TRIGGER_DAMAGE,
    APPLY_BOOST,
    RESTORE_YAW,
}

internal class CubecraftDamageFlyCycle(
    startY: Double,
    private val timeoutTicks: Int,
) {

    private enum class State {
        ARMED,
        AWAITING_DAMAGE,
        HURT,
    }

    private var state = State.ARMED
    private var startY = startY
    private var remainingTicks = timeoutTicks

    val acceptsVelocity: Boolean
        get() = state != State.ARMED

    val spoofServerYaw: Boolean
        get() = state != State.ARMED

    init {
        require(timeoutTicks > 0) { "Timeout must be positive" }
    }

    fun tick(
        currentY: Double,
        hurtTime: Int,
        damageConfirmed: Boolean = false,
    ): CubecraftDamageFlyAction {
        return when (state) {
            State.ARMED -> when {
                damageConfirmed || hurtTime > 0 -> beginHurt(currentY)
                currentY < startY - HEIGHT_EPSILON -> {
                    state = State.AWAITING_DAMAGE
                    remainingTicks = timeoutTicks
                    CubecraftDamageFlyAction.TRIGGER_DAMAGE
                }
                else -> CubecraftDamageFlyAction.NONE
            }
            State.AWAITING_DAMAGE -> {
                if (damageConfirmed || hurtTime > 0) {
                    beginHurt(currentY)
                } else {
                    remainingTicks--
                    if (remainingTicks <= 0) {
                        rearm(currentY)
                        CubecraftDamageFlyAction.RESTORE_YAW
                    } else {
                        CubecraftDamageFlyAction.NONE
                    }
                }
            }
            State.HURT -> {
                if (hurtTime <= 0 && !damageConfirmed) {
                    rearm(currentY)
                    CubecraftDamageFlyAction.RESTORE_YAW
                } else {
                    CubecraftDamageFlyAction.NONE
                }
            }
        }
    }

    fun cancel() {
        state = State.ARMED
    }

    private fun beginHurt(currentY: Double): CubecraftDamageFlyAction {
        startY = currentY
        state = State.HURT
        return CubecraftDamageFlyAction.APPLY_BOOST
    }

    private fun rearm(currentY: Double) {
        startY = currentY
        state = State.ARMED
    }

    private companion object {
        const val HEIGHT_EPSILON = 0.01
    }

}

internal fun cubecraftDamageServerYaw(
    clientYaw: Float,
    fakeStrafe: Boolean,
    yawOffset: Float,
): Float {
    return Mth.wrapDegrees(clientYaw + if (fakeStrafe) yawOffset else 0f)
}

internal fun redirectCubecraftDamageKnockback(
    velocity: Vec3,
    clientYaw: Float,
    minimumHorizontalSpeed: Double,
    minimumVerticalSpeed: Double,
): Vec3 {
    val horizontalSpeed = max(velocity.horizontalDistance(), minimumHorizontalSpeed)
    val verticalSpeed = max(velocity.y, minimumVerticalSpeed)
    val yawRadians = Math.toRadians(clientYaw.toDouble())

    return Vec3(
        -sin(yawRadians) * horizontalSpeed,
        verticalSpeed,
        cos(yawRadians) * horizontalSpeed,
    )
}
