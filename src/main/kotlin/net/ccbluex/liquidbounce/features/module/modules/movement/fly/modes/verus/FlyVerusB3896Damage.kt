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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.verus

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly.modes
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationEnd
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyAutomaticEndSignal
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.withFlyAutomationStrafe
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.movement.stopXZVelocity
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

/**
 * @anticheat Verus
 * @anticheatVersion b3896
 * @testedOn eu.loyisa.cn
 * @note it gives you ~2 flags for damage
 */
internal object FlyVerusB3896Damage : Mode("VerusB3896Damage"), FlyAutomationProfile {

    override val parent: ModeValueGroup<*>
        get() = modes

    private var flyTicks = 0
    private var shouldStop = false
    private var gotDamage = false
    private val automaticEnd = FlyAutomaticEndSignal()

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = false,
        descend = false,
        landing = false,
        kind = FlyAutomationKind.BURST,
    )

    override fun automationReadiness(): FlyAutomationReadiness = when {
        shouldStop -> FlyAutomationReadiness.Unavailable("Verus self-damage failed")
        gotDamage -> FlyAutomationReadiness.Ready
        else -> FlyAutomationReadiness.Arming("Waiting for Verus self-damage")
    }

    override fun consumeAutomaticEnd(): FlyAutomationEnd? = automaticEnd.consume()

    override fun enable() {
        flyTicks = 0
        shouldStop = false
        gotDamage = false
        automaticEnd.reset()
        network.send(
            ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, false,
            player.horizontalCollision))
        network.send(
            ServerboundMovePlayerPacket.Pos(player.x, player.y + 3.25, player.z, false,
            player.horizontalCollision))
        network.send(
            ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, false,
            player.horizontalCollision))
        network.send(
            ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, true,
            player.horizontalCollision))
    }

    @Suppress("unused")
    val failRepeatable = tickHandler {
        if (!gotDamage) {
            waitTicks(20)
            if (!gotDamage) {
                chat("Failed to self-damage")
                shouldStop = true
            }
        }
    }
    val repeatable = tickHandler {
        if (player.hurtTime > 0) {
            gotDamage = true
        }

        if (!gotDamage) {
            return@tickHandler
        }

        if (++flyTicks > 20 || shouldStop) {
            automaticEnd.mark(
                if (shouldStop) "Verus self-damage failed" else "Verus damage boost completed",
            )
            ModuleFly.enabled = false
            return@tickHandler
        }

        player.deltaMovement = player.deltaMovement.withFlyAutomationStrafe(player, 9.95)
        player.deltaMovement.y = 0.0
        Timer.requestTimerSpeed(0.1f, Priority.IMPORTANT_FOR_USAGE_2, ModuleFly)
    }

    override fun disable() {
        flyTicks = 0
        shouldStop = false
        gotDamage = false
        player.stopXZVelocity()
    }
}
