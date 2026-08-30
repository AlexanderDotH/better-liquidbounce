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

import net.ccbluex.liquidbounce.common.Tagged
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

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
    val boostActive: Boolean
        get() = state == State.HURT
    val spoofServerYaw: Boolean
        get() = state != State.ARMED

    init {
        require(timeoutTicks > 0) { "Timeout must be positive" }
    }

    fun tick(currentY: Double, hurtTime: Int, damageConfirmed: Boolean = false): CubecraftDamageFlyAction =
        when (state) {
            State.ARMED -> when {
                damageConfirmed || hurtTime > 0 -> beginHurt(currentY)
                currentY < startY - HEIGHT_EPSILON -> {
                    state = State.AWAITING_DAMAGE
                    remainingTicks = timeoutTicks
                    CubecraftDamageFlyAction.TRIGGER_DAMAGE
                }
                else -> CubecraftDamageFlyAction.NONE
            }
            State.AWAITING_DAMAGE -> awaitDamage(currentY, hurtTime, damageConfirmed)
            State.HURT -> finishHurt(currentY, hurtTime, damageConfirmed)
        }

    fun cancel() {
        state = State.ARMED
    }

    private fun awaitDamage(currentY: Double, hurtTime: Int, damageConfirmed: Boolean): CubecraftDamageFlyAction {
        if (damageConfirmed || hurtTime > 0) return beginHurt(currentY)
        remainingTicks--
        if (remainingTicks > 0) return CubecraftDamageFlyAction.NONE
        rearm(currentY)
        return CubecraftDamageFlyAction.RESTORE_YAW
    }

    private fun finishHurt(currentY: Double, hurtTime: Int, damageConfirmed: Boolean): CubecraftDamageFlyAction {
        if (hurtTime > 0 || damageConfirmed) return CubecraftDamageFlyAction.NONE
        rearm(currentY)
        return CubecraftDamageFlyAction.RESTORE_YAW
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

internal fun performCubecraftSelfDamage(
    method: CubecraftSelfDamageMethod,
    baseY: Double,
    sendPosition: (Double, Boolean) -> Unit,
) {
    if (method == CubecraftSelfDamageMethod.VERUS) {
        sendPosition(baseY, false)
        sendPosition(baseY + VERUS_VERTICAL_OFFSET, false)
        sendPosition(baseY, false)
        sendPosition(baseY, true)
        return
    }

    var offsetY = SENTINEL_INITIAL_OFFSET
    var motionY = 0.0
    while (offsetY > 0.0) {
        sendPosition(baseY + offsetY, offsetY == SENTINEL_INITIAL_OFFSET)
        offsetY += motionY
        motionY = (motionY - SENTINEL_GRAVITY) * SENTINEL_DRAG
    }
    sendPosition(baseY, true)
}

internal fun cubecraftDamageServerYaw(clientYaw: Float, fakeStrafe: Boolean, yawOffset: Float): Float =
    Mth.wrapDegrees(clientYaw + if (fakeStrafe) yawOffset else 0f)

internal fun redirectCubecraftDamageKnockback(
    velocity: Vec3,
    clientYaw: Float,
    minimumHorizontalSpeed: Double,
    minimumVerticalSpeed: Double,
): Vec3 {
    val horizontalSpeed = max(velocity.horizontalDistance(), minimumHorizontalSpeed)
    val verticalSpeed = max(velocity.y, minimumVerticalSpeed)
    val yawRadians = Math.toRadians(clientYaw.toDouble())
    return Vec3(-sin(yawRadians) * horizontalSpeed, verticalSpeed, cos(yawRadians) * horizontalSpeed)
}

private const val VERUS_VERTICAL_OFFSET = 3.25
private const val SENTINEL_INITIAL_OFFSET = 4.0
private const val SENTINEL_GRAVITY = 0.08
private const val SENTINEL_DRAG = 0.98
