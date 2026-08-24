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
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationEnd
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyAutomaticEndSignal
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.withFlyAutomationStrafe
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.network.protocol.game.ClientboundExplodePacket

/**
 * @anticheat Watchdog (NCP)
 * @anticheatVersion 21.01.25
 * @testedOn hypixel.net
 * @author @liquidsquid1
 */
internal object FlyHypixel : Mode("Hypixel"), FlyAutomationProfile {

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    private val timer by float("Timer", 1.0f, 0.1f..1.0f)

    private var isFlying = false
    private val automaticEnd = FlyAutomaticEndSignal()

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
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
        isFlying = false
        super.enable()
    }

    override fun disable() {
        isFlying = false
        super.disable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        tickUntil { isFlying }

        player.deltaMovement.y = 0.8
        waitTicks(1)
        player.deltaMovement = player.deltaMovement.withFlyAutomationStrafe(player, 1.9)
        player.deltaMovement.y = 1.0
        waitTicks(1)
        player.deltaMovement = player.deltaMovement.multiply(
            1.05,
            1.0,
            1.05
        )
        waitTicks(19)
        player.deltaMovement.y += 0.42

        tickUntil { player.onGround() }
        automaticEnd.mark("Hypixel boost landed")
        ModuleFly.enabled = false
    }

    @Suppress("unused")
    private val timerHandler = tickHandler {
        Timer.requestTimerSpeed(timer, Priority.IMPORTANT_FOR_USAGE_1, ModuleFly)
    }

    @Suppress("unused")
    private val strafeHandler = handler<PlayerMoveEvent> { event ->
        event.movement = event.movement.withFlyAutomationStrafe(player)
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.packet is ClientboundExplodePacket) {
            isFlying = true
        }
    }

}
