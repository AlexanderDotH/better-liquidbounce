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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.polar

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
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
import net.ccbluex.liquidbounce.utils.network.handlePacket
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket

/**
 * @anticheat Hycraft (Polar)
 * @anticheat Version 15.05.2024
 * @testedOn mc.hycraft.us
 *
 * @note Tested in Bedwars, Skywars. Pretty much flagless
 */
internal object FlyHycraftDamage : Mode("HycraftDamage"), FlyAutomationProfile {

    private var damageTaken = false
    private var release = false
    private var ticks = 0
    private val automaticEnd = FlyAutomaticEndSignal()

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
        descend = false,
        landing = false,
        kind = FlyAutomationKind.BURST,
    )

    override fun automationReadiness(): FlyAutomationReadiness = when {
        release -> FlyAutomationReadiness.Ready
        damageTaken -> FlyAutomationReadiness.Arming("Waiting for the Hycraft velocity boost")
        else -> FlyAutomationReadiness.Arming("Waiting for damage")
    }

    override fun consumeAutomaticEnd(): FlyAutomationEnd? = automaticEnd.consume()

    override fun enable() {
        ticks = 0
        damageTaken = false
        release = false
        automaticEnd.reset()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        waitTicks(1)

        if (ticks > 0) {
            ticks--
        }
    }

    /**
     * Used to works on different servers as well but now only Hycraft
     */
    @Suppress("unused")
    private val packetHandler = handler<BlinkPacketEvent> { event ->
        val packet = event.packet

        if (event.origin != TransferOrigin.INCOMING) {
            return@handler
        }

        event.action = when (packet) {
            is ClientboundDamageEventPacket if packet.entityId == player.id && ticks <= 0 -> {
                damageTaken = true
                ticks = 40
                handlePacket(packet)
                net.ccbluex.liquidbounce.event.events.BlinkPacketAction.QUEUE
            }

            is ClientboundSetEntityMotionPacket if packet.id == player.id && damageTaken -> {
                damageTaken = false
                release = true
                handlePacket(packet)
                net.ccbluex.liquidbounce.event.events.BlinkPacketAction.QUEUE
            }

            is ClientboundPingPacket -> {
                if (ticks <= 0) {
                    if (release) {
                        automaticEnd.mark("Hycraft damage boost completed")
                        FlyModuleControl.disable()
                    }
                    return@handler
                }

                ticks--
                net.ccbluex.liquidbounce.event.events.BlinkPacketAction.QUEUE
            }

            // Prevent [PacketQueueManager] from flushing queued packets
            else -> net.ccbluex.liquidbounce.event.events.BlinkPacketAction.PASS
        }

    }

}
