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
package net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features

import com.google.common.collect.Lists
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.network.isLocalPlayerDamage
import net.ccbluex.liquidbounce.utils.network.isLocalPlayerVelocity

object KillAuraVelocityHit : ToggleableValueGroup(ModuleKillAura, "VelocityHit", false) {

    val extendRange by float("ExtendRange", 1.0f, 0.1f..2.0f)
    private val whenLag by boolean("OnlyWhileLagging", false)

    private var considerVelocityHit = false
    private var damageReceived = false
    private var onGroundTicks = 0
    private var isPossible = false

    private var timer = Chronometer()
    private var lastPacketTime = Lists.newLinkedList<Long>()

    private const val SAMPLE_SIZE = 10

    val isVelocityHitPossible
        get() = super.running && isPossible

    @Suppress("unused")
    private val packetHandler = sequenceHandler<PacketEvent>(priority = 1) { event ->
        val packet = event.packet

        if (event.origin == TransferOrigin.INCOMING) {
            addRecentPacketTime()
        }

        when {
            packet.isLocalPlayerDamage() -> {
                damageReceived = true
            }
            packet.isLocalPlayerVelocity(considerExplosion = false) && damageReceived -> {
                considerVelocityHit = true
            }
        }
    }

    @Suppress("unused")
    private val gameHandler = tickHandler {
        if (player.isDeadOrDying || player.isSpectator) {
            return@tickHandler
        }

        val enemy = ModuleKillAura.targetTracker.target
        var lagging = isLagging() || BlinkManager.isLagging

        if (!whenLag) {
            lagging = true
        }

        if (enemy == null) {
            reset()
            return@tickHandler
        }

        val extendedRange = ModuleKillAura.range.interactionRange + extendRange
        val isInExtendedRange = player.squaredBoxedDistanceTo(enemy) <= extendedRange.sq()
        isPossible = lagging && considerVelocityHit && isInExtendedRange

        if ((player.onGround() && isPossible) || (player.fallDistance > 0.3 && isPossible)) {
            onGroundTicks++
        }

        if (onGroundTicks > 5) {
            reset()
        }
    }

    fun reset() {
        isPossible = false
        considerVelocityHit = false
        damageReceived = false
        onGroundTicks = 0
    }

    private fun addRecentPacketTime() {
        lastPacketTime.add(timer.elapsed)
        timer.reset()

        if (lastPacketTime.size > SAMPLE_SIZE) {
            for (i in 0..<SAMPLE_SIZE) {
                lastPacketTime.removeAt(0)
            }
        }
    }

    private fun isLagging(): Boolean {
        if (lastPacketTime.size != SAMPLE_SIZE) {
            return false
        }

        var sumTime = 0L
        for (i in 0..<SAMPLE_SIZE) {
            sumTime += lastPacketTime[i]
        }

        return sumTime / SAMPLE_SIZE.toDouble() > 0.5
    }

}
