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

package net.ccbluex.liquidbounce.utils.entity

import net.ccbluex.liquidbounce.utils.client.player as clientPlayer
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.client.player.ClientInput
import net.minecraft.core.Holder
import net.minecraft.world.entity.EntityFluidInteraction
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class SimulatedPlayer(
    internal val player: Player,
    var input: SimulatedPlayerInput,
    override var pos: Vec3,
    var deltaMovement: Vec3,
    var boundingBox: AABB,
    var yRot: Float,
    var xRot: Float,
    var isSprinting: Boolean,
    var fallDistance: Double,
    internal var jumpTriggerTime: Int,
    internal var jumping: Boolean,
    internal var fallFlying: Boolean,
    var onGround: Boolean,
    var horizontalCollision: Boolean,
    internal var verticalCollision: Boolean,
    internal var wasTouchingWater: Boolean,
    internal var isSwimming: Boolean,
    internal var wasUnderwater: Boolean,
    internal val fluidInteraction: EntityFluidInteraction,
) : PlayerSimulation {

    internal val level: Level
        get() = player.level()

    internal var simulatedTicks: Int = 0

    var clipLedged = false
        internal set

    override fun tick() = simulateTick()

    fun jumpFromGround() = performGroundJump()

    fun jump() = jumpFromGround()

    fun getAttributeValue(attribute: Holder<Attribute>): Double = player.attributes.getValue(attribute)

    fun clone(): SimulatedPlayer = copySimulation()

    class SimulatedPlayerInput(
        val directionalInput: DirectionalInput,
        jumping: Boolean,
        var sprinting: Boolean,
        sneaking: Boolean,
        var ignoreClippingAtLedge: Boolean = false,
    ) : ClientInput() {

        var forceSafeWalk: Boolean = false

        init {
            set(
                forward = directionalInput.forwards,
                backward = directionalInput.backwards,
                left = directionalInput.left,
                right = directionalInput.right,
                jump = jumping,
                sneak = sneaking,
            )
        }

        internal val forwardMovement: Float
            get() = movementForward

        internal val sidewaysMovement: Float
            get() = movementSideways

        fun update() {
            movementForward = when {
                keyPresses.forward == keyPresses.backward -> 0.0f
                keyPresses.forward -> 1.0f
                else -> -1.0f
            }
            movementSideways = when {
                keyPresses.left == keyPresses.right -> 0.0f
                keyPresses.left -> 1.0f
                else -> -1.0f
            }
            if (keyPresses.shift) {
                movementSideways = (movementSideways.toDouble() * 0.3).toFloat()
                movementForward = (movementForward.toDouble() * 0.3).toFloat()
            }
        }

        override fun toString(): String = "SimulatedPlayerInput(" +
            "forwards={${keyPresses.forward}}, backwards={${keyPresses.backward}}, " +
            "left={${keyPresses.left}}, right={${keyPresses.right}}, " +
            "jumping={${keyPresses.jump}}, sprinting=$sprinting, slowDown=${keyPresses.shift})"

        companion object {
            @JvmStatic
            fun fromClientPlayer(
                directionalInput: DirectionalInput,
                jump: Boolean = clientPlayer.input.keyPresses.jump,
                sprinting: Boolean = clientPlayer.isSprinting,
                sneaking: Boolean = clientPlayer.isShiftKeyDown,
            ): SimulatedPlayerInput = createClientSimulationInput(directionalInput, jump, sprinting, sneaking)

            @JvmStatic
            fun guessInput(entity: Player): SimulatedPlayerInput =
                guessSimulationInput(entity, entity.position().subtract(entity.lastPos))
        }
    }

    companion object {
        @JvmStatic
        fun fromClientPlayer(input: SimulatedPlayerInput): SimulatedPlayer = createClientSimulation(input)

        @JvmStatic
        fun fromOtherPlayer(player: Player, input: SimulatedPlayerInput): SimulatedPlayer =
            createOtherPlayerSimulation(player, input, player.position().subtract(player.lastPos))
    }
}
