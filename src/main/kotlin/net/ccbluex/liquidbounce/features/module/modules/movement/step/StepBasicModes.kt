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

import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.AllowAutoJumpEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PlayerStepEvent
import net.ccbluex.liquidbounce.event.events.PlayerStepSuccessEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.minecraft.stats.Stats

internal object StepLegit : Mode("Legit") {

    @Suppress("unused")
    private val autoJumpHandler = handler<AllowAutoJumpEvent> { event ->
        event.isAllowed = true
    }
}

internal object StepInstant : Mode("Instant") {
    private val jumpOrder = doubleArrayOf(
        0.0,
        0.41999998688698,
        0.7531999805212,
        1.00133597911215,
        1.166109260938214,
        1.24918707874468,
        1.25220334025373,
        1.17675927506424,
        1.024424088213685,
    )
    private val height by float("Height", 1.0F, 0.6F..5.0F)
    private val trim by boolean("Trim", false)
    private val simulateJumpOrder by intRange("SimulateJumpOrder", 0..2, jumpOrder.indices)
    private val wait by intRange("Wait", 0..0, 0..60, "ticks")
    private val packetType by enumChoice(
        "PacketType",
        MovePacketType.FULL,
        enumSetOf(MovePacketType.FULL, MovePacketType.POSITION_AND_ON_GROUND),
    )
    private var ticksWait = 0

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (ticksWait > 0) ticksWait--
    }

    @Suppress("unused")
    private val stepHandler = handler<PlayerStepEvent> {
        if (ticksWait > 0) return@handler
        it.height = height
    }

    @Suppress("unused")
    private val stepSuccessEvent = handler<PlayerStepSuccessEvent> { event ->
        val stepHeight = event.adjustedVec.y
        ModuleDebug.debugParameter(ModuleStep, "StepHeight", stepHeight)
        if (stepHeight <= 0.5 || simulateJumpOrder == 0..0) return@handler

        player.awardStat(Stats.JUMP)
        val trimHeight = player.y + stepHeight
        jumpOrder.sliceArray(simulateJumpOrder)
            .filter { it != 0.0 }
            .map { additionalY ->
                packetType.generatePacket().apply {
                    x = player.x
                    y = (player.y + additionalY).let { if (trim) it.coerceAtMost(trimHeight) else it }
                    z = player.z
                }
            }
            .forEach(network::send)
        ticksWait = wait.random()
    }
}
