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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationEnd
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.chat.chat
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import kotlin.jvm.optionals.getOrNull

/**
 * Explode yourself to fly
 * Takes any kind of damage, preferably explosion damage.
 * Might bypass some anti-cheats.
 */
internal object FlyExplosion : Mode("Explosion"), FlyAutomationProfile {

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = false,
        descend = false,
        landing = true,
        kind = FlyAutomationKind.BURST,
        resource = "explosion damage",
    )

    override fun automationReadiness(): FlyAutomationReadiness = if (strafeSince > 0f) {
        FlyAutomationReadiness.Ready
    } else {
        FlyAutomationReadiness.Arming("Waiting for explosion damage")
    }


    val vertical by float("Vertical", 4f, 0f..10f)
    val startStrafe by float("StartStrafe", 1f, 0.6f..4f)
    val strafeDecrease by float("StrafeDecrease", 0.005f, 0.001f..0.1f)

    private var strafeSince = 0.0f
    private var automationBurstStarted = false
    private var pendingAutomationEnd: FlyAutomationEnd? = null

    override fun enable() {
        automationBurstStarted = false
        pendingAutomationEnd = null
        chat("You need to be damaged by an explosion to fly.")
        super.enable()
    }

    override fun disable() {
        strafeSince = 0f
        automationBurstStarted = false
        pendingAutomationEnd = null
        super.disable()
    }

    override fun consumeAutomaticEnd(): FlyAutomationEnd? {
        if (strafeSince > 0f) automationBurstStarted = true
        return pendingAutomationEnd.also { pendingAutomationEnd = null }
    }

    val repeatable = tickHandler {
        if (strafeSince > 0) {
            if (!player.onGround()) {
                player.deltaMovement = player.deltaMovement.withFlyAutomationStrafe(
                    player = player,
                    speed = strafeSince.toDouble(),
                )
                strafeSince -= strafeDecrease
                if (strafeSince <= 0f && automationBurstStarted) {
                    strafeSince = 0f
                    pendingAutomationEnd = FlyAutomationEnd("Explosion flight burst ended")
                }
            } else {
                strafeSince = 0f
                if (automationBurstStarted) {
                    pendingAutomationEnd = FlyAutomationEnd("Explosion flight landed")
                }
            }
        }
    }

    val packetHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet

        // Check if this is a regular velocity update
        if (packet is ClientboundSetEntityMotionPacket && packet.id == player.id) {
            // Modify packet according to the specified values
            packet.movement.x = 0.0
            packet.movement.y = packet.movement.y * vertical
            packet.movement.z = 0.0

            waitTicks(1)
            strafeSince = startStrafe
            automationBurstStarted = true
        } else if (packet is ClientboundExplodePacket) { // Check if explosion affects velocity
            packet.playerKnockback.getOrNull()?.let { knockback ->
                knockback.x = 0.0
                knockback.y *= vertical
                knockback.z = 0.0

                waitTicks(1)
                strafeSince = startStrafe
                automationBurstStarted = true
            }
        }
    }

}
