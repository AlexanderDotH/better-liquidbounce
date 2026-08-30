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
package net.ccbluex.liquidbounce.features.module.modules.movement.step

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.entity.airTicks
import net.ccbluex.liquidbounce.utils.entity.canStep
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.utils.kotlin.Priority

internal object StepVulcan286 : Mode("Vulcan286") {
    private var stepCounter = 0
    private var stepping = false

    @Suppress("unused")
    private val movementInputHandler = sequenceHandler<MovementInputEvent> { event ->
        if (player.canStep(1.0) && !stepping) {
            event.jump = true
            stepCounter++
            stepping = true
            waitTicks(2)
            if (stepCounter % 2 == 0) {
                player.deltaMovement.y = 0.24680001947880004
                player.deltaMovement = player.deltaMovement.withStrafe(speed = 0.2)
            }
            waitTicks(1)
            if (stepCounter % 2 == 0) player.deltaMovement.y = 0.0
            waitTicks(1)
            player.deltaMovement.y = -0.17
            stepping = false
        }
    }

    override fun disable() {
        stepping = false
        stepCounter = 0
        super.disable()
    }
}

internal object StepBlocksMC : Mode("BlocksMC") {
    private var baseTimer by float("BaseTimer", 3.0f, 0.1f..5.0f)
    private var recoveryTimer by float("RecoveryTimer", 0.6f, 0.1f..5.0f)
    private var stepping = false

    @Suppress("unused")
    private val movementInputHandler = sequenceHandler<MovementInputEvent> { event ->
        if (player.canStep(1.0) && !stepping) {
            event.jump = true
            stepping = true
            Timer.requestTimerSpeed(baseTimer, Priority.IMPORTANT_FOR_USAGE_1, ModuleStep, 3)
            player.deltaMovement.y = 0.42
            waitTicks(1)
            player.deltaMovement.y = 0.33
            waitTicks(1)
            player.deltaMovement.y = 0.25
            waitTicks(2)
            player.deltaMovement = player.deltaMovement.withStrafe(speed = 0.281)
            player.deltaMovement.y -= player.y % 1.0
            Timer.requestTimerSpeed(recoveryTimer, Priority.IMPORTANT_FOR_USAGE_1, ModuleStep, 2)
            stepping = false
        }
    }

    @Suppress("unused")
    private val fakeLagHandler = handler<BlinkPacketEvent> { event ->
        if (event.origin == TransferOrigin.OUTGOING && stepping) event.action = net.ccbluex.liquidbounce.event.events.BlinkPacketAction.QUEUE
    }

    override fun disable() {
        stepping = false
        super.disable()
    }

    override val running: Boolean
        get() = super.running && !ModuleSpeed.running
}

internal object StepHypixel : Mode("Hypixel") {
    private val alternateBypass by boolean("AlternateBypass", false)
    private val spoof by boolean("Spoof", false)
    private var stepping = false
    private val stepHeight get() = when {
        player.canStep(1.0) -> 1.0
        player.canStep(1.25) -> 1.25
        else -> 1.5
    }

    @Suppress("unused")
    private val movementInputHandler = sequenceHandler<MovementInputEvent> { event ->
        if (player.canStep(1.5) && !stepping) performStep(event)
    }

    private suspend fun performStep(event: MovementInputEvent) {
        val currentStepHeight = stepHeight
        event.jump = true
        stepping = true
        player.deltaMovement.y = 0.42
        waitTicks(1)
        if (currentStepHeight > 1.0) player.deltaMovement.y += 0.061
        waitTicks(2)
        if (currentStepHeight == 1.0) {
            player.deltaMovement.y -= 0.14
        } else {
            player.deltaMovement.y -= 0.095
            if (currentStepHeight > 1.25) {
                waitTicks(5)
                if (alternateBypass) player.setOnGround(true) else player.deltaMovement.y = 0.42
            }
        }
        stepping = false
        player.deltaMovement = player.deltaMovement.withStrafe(speed = 0.1838601407459074)
    }

    @Suppress("unused")
    private val networkTickHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        if (spoof && player.airTicks == 8) event.ground = true
    }

    override fun disable() {
        stepping = false
        super.disable()
    }
}
