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
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.exploit.ModulePingSpoof
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
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.contract.SpeedState
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.movement.stopXZVelocity
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

/**
 * @anticheat Sentinel
 * @anticheatVersion 20.04.2024
 * @testedOn cubecraft.net
 *
 * @note Tested in SkyWars - fly as long as you want. REQUIRES PING SPOOF TO BE ENABLED.
 *
 * Thanks to the_bi11iona1re for making me aware that Sentinal folds to Verus Damage exploit.
 */
internal object FlySentinel20thApr : Mode("Sentinel20thApr"), FlyAutomationProfile {

    private val horizontalSpeed by float("HorizontalSpeed", 3.5f, 0.1f..10f)
    private val constantSpeed by boolean("ConstantSpeed", false)
    private val verticalSpeed by float("VerticalSpeed", 0.7f, 0.1f..1f)
    private val reboostTicks by int("ReboostTicks", 30, 10..50)
    private val boostOnce by boolean("BoostOnce", false)
    private val nostalgia by boolean("Nostalgia", false)


    private var hasBeenHurt = false
    private var hasBeenTeleported = false
    private val automaticEnd = FlyAutomaticEndSignal()

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
        descend = true,
        landing = true,
        kind = FlyAutomationKind.CONTINUOUS,
    )

    override fun automationReadiness(): FlyAutomationReadiness = if (hasBeenHurt) {
        FlyAutomationReadiness.Ready
    } else {
        FlyAutomationReadiness.Arming("Waiting for the Sentinel damage boost")
    }

    override fun consumeAutomaticEnd(): FlyAutomationEnd? = automaticEnd.consume()

    override fun enable() {
        if (!ModulePingSpoof.enabled) {
            ModulePingSpoof.enabled = true
        }

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
    }

    val repeatable = tickHandler {
        boost()
        waitTicks(reboostTicks)

        if (boostOnce) {
            automaticEnd.mark("Configured single Sentinel boost completed")
            FlyModuleControl.disable()
            player.stopXZVelocity()
        }
    }

    val moveHandler = handler<PlayerMoveEvent> { event ->
        if (player.hurtTime > 0  && !hasBeenHurt) {
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

        if (constantSpeed) {
            event.movement = event.movement.withFlyAutomationStrafe(player, horizontalSpeed.toDouble())
        }
    }

    private fun boost() {
        hasBeenHurt = false
        network.send(
            ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, false,
            player.horizontalCollision))
        network.send(
            ServerboundMovePlayerPacket.Pos(player.x, player.y + 3.25, player.z,
            false, player.horizontalCollision))
        network.send(
            ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, false,
            player.horizontalCollision))
        network.send(
            ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, true,
            player.horizontalCollision))
    }

}
