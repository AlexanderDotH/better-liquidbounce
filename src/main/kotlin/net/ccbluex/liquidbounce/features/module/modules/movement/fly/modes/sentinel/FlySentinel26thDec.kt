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
import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.runtime.FlyModuleControl
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationEnd
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyAutomaticEndSignal
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.flyAutomationJump
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.flyAutomationSneak
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.withFlyAutomationStrafe
import net.ccbluex.liquidbounce.features.module.modules.movement.sentinel.isSentinelOutgoingMovementPacket
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.contract.SpeedState
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.movement.stopXZVelocity
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

/**
 * @anticheat Sentinel
 * @anticheatVersion 26.12.2025
 * @testedOn cubecraft.net
 *
 * @note Tested in Egg Wars, ticks higher than 25 seems silent flags
 *
 * Thanks to the_bi11iona1re for making me aware that Sentinal folds to Verus Damage exploit.
 * Unpatched by @hax0r31337
 */
internal object FlySentinel26thDec : Mode("Sentinel26thDec"), FlyAutomationProfile {

    private val horizontalSpeed by float("HorizontalSpeed", 3.5f, 0.1f..10f)
    private val verticalSpeed by float("VerticalSpeed", 0.7f, 0.1f..5f)
    private val ticks by int("Ticks", 20, 10..50)
    private val timer by float("Timer", 0.5f, 0.1f..1f)
    private val nostalgia by boolean("Nostalgia", false)


    private var hasBeenHurt = false
    private var hasBeenTeleported = false
    private val automaticEnd = FlyAutomaticEndSignal()

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
        descend = true,
        landing = false,
        kind = FlyAutomationKind.BURST,
    )

    override fun automationReadiness(): FlyAutomationReadiness = if (hasBeenHurt) {
        FlyAutomationReadiness.Ready
    } else {
        FlyAutomationReadiness.Arming("Waiting for the Sentinel damage boost")
    }

    override fun consumeAutomaticEnd(): FlyAutomationEnd? = automaticEnd.consume()

    override fun enable() {
        if (SpeedState.enabled) {
            SpeedState.disable()
        }

        hasBeenHurt = false
        hasBeenTeleported = false
        automaticEnd.reset()

        chat(regular(translation("liquidbounce.module.fly.messages.cubecraft20thAprBoostUsage")))
        super.enable()
    }

    override fun disable() {
        player.stopXZVelocity()

        Timer.requestTimerSpeed(1.0f, Priority.IMPORTANT_FOR_USAGE_1, FlyModuleControl.module)

        BlinkManager.flush {
            true
        }
    }

    val repeatable = tickHandler {
        if (!player.onGround()) {
            automaticEnd.mark("Sentinel boost requires starting on the ground")
            FlyModuleControl.disable()
            return@tickHandler
        }

        boost()

        Timer.requestTimerSpeed(timer, Priority.IMPORTANT_FOR_USAGE_1, FlyModuleControl.module, resetAfterTicks = ticks)
        waitTicks(ticks)

        automaticEnd.mark("Sentinel damage boost completed")
        FlyModuleControl.disable()
        player.stopXZVelocity()
    }

    val moveHandler = handler<PlayerMoveEvent> { event ->
        if (player.hurtTime > 0 && !hasBeenHurt) {
            hasBeenHurt = true
            player.deltaMovement = player.deltaMovement.withFlyAutomationStrafe(player, horizontalSpeed.toDouble())
            notification(
                "Fly",
                translation("liquidbounce.module.fly.messages.cubecraft20thAprBoostMessage"),
                NotificationEvent.Severity.INFO
            )

            // Nostalgia mode
            if (!hasBeenTeleported && nostalgia) {
                hasBeenTeleported = true
                player.setPos(
                    player.x,
                    player.y + 0.42,
                    player.z
                )
            }
        }

        if (!hasBeenHurt) {
            return@handler
        }

        event.movement.y = when {
            flyAutomationJump(mc.options.keyJump.isDown) -> verticalSpeed.toDouble()
            flyAutomationSneak(mc.options.keyShift.isDown) -> (-verticalSpeed).toDouble()
            else -> 0.0
        }

        event.movement = event.movement.withFlyAutomationStrafe(player, horizontalSpeed.toDouble())
    }

    private fun boost() {
        hasBeenHurt = false

        var y = 4.0
        var motionY = 0.0

        while (y > 0) {
            network.send(ServerboundMovePlayerPacket.Pos(player.x, player.y + y, player.z,
                y == 4.0, player.horizontalCollision))

            y += motionY

            motionY -= 0.08
            motionY *= 0.98
        }

        network.send(ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, true,
            player.horizontalCollision))
    }

    @Suppress("unused")
    private val fakeLagHandler = handler<BlinkPacketEvent> { event ->
        if (!hasBeenHurt || player.isDeadOrDying) {
            return@handler
        }

        event.action = if (isSentinelOutgoingMovementPacket(event.origin, event.packet)) {
            net.ccbluex.liquidbounce.event.events.BlinkPacketAction.QUEUE
        } else {
            net.ccbluex.liquidbounce.event.events.BlinkPacketAction.PASS
        }
    }
}
