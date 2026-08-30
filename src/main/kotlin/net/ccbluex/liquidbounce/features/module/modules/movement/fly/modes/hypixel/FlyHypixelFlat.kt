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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.hypixel

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.runtime.FlyModuleControl
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationEnd
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyAutomaticEndSignal
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.withFlyAutomationStrafe
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.entity.horizontalSpeed
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import kotlin.random.Random

/**
 * @anticheat Watchdog (NCP)
 * @anticheatVersion 21.01.25
 * @testedOn hypixel.net
 * @author @liquidsquid1
 */
internal object FlyHypixelFlat : Mode("HypixelFlat"), FlyAutomationProfile {


    private val timer by float("Timer", 1.0f, 0.1f..1.0f)
    private val flySpeed by float("Speed", 1.66f, 0.8f..2.0f)

    private var flyTicks = 0
    private var isFlying = false
    private val automaticEnd = FlyAutomaticEndSignal()

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = false,
        descend = false,
        landing = true,
        kind = FlyAutomationKind.BURST,
    )

    override fun automationReadiness(): FlyAutomationReadiness = if (isFlying) {
        FlyAutomationReadiness.Ready
    } else {
        FlyAutomationReadiness.Arming("Waiting for an explosion boost")
    }

    override fun consumeAutomaticEnd(): FlyAutomationEnd? = automaticEnd.consume()

    override fun enable() {
        automaticEnd.reset()
        flyTicks = 0
        isFlying = false
        super.enable()
    }

    override fun disable() {
        flyTicks = 0
        isFlying = false
        super.disable()
    }

    @Suppress("unused")
    private val speedHandler = tickHandler {
        tickUntil { isFlying }

        player.deltaMovement = player.deltaMovement.withFlyAutomationStrafe(player, 0.8)
        waitTicks(1)
        player.deltaMovement = player.deltaMovement.withFlyAutomationStrafe(player, flySpeed.toDouble())

        tickUntil { player.onGround() }
        automaticEnd.mark("Hypixel flat boost landed")
        FlyModuleControl.disable()
    }

    @Suppress("unused")
    private val velocityHandler = tickHandler {
        if (!isFlying) {
            return@tickHandler
        }

        flyTicks++
        if (flyTicks > 30) {
            return@tickHandler
        }

        Timer.requestTimerSpeed(timer, Priority.IMPORTANT_FOR_USAGE_1, FlyModuleControl.module)
        player.deltaMovement.y = 0.0314 + (Random.nextDouble() / 1000f)
        player.deltaMovement = player.deltaMovement.withFlyAutomationStrafe(player, player.horizontalSpeed)
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.packet is ClientboundExplodePacket) {
            isFlying = true
        }
    }

}
