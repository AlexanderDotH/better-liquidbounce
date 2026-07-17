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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.EntityHealthUpdateEvent
import net.ccbluex.liquidbounce.event.events.GameRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.world.entity.player.Player

object FakeKillAura : ToggleableValueGroup(
    ModuleAmnesia,
    "FakeKillAura",
    false,
    aliases = listOf("Fake KillAura"),
) {

    internal val switchInterval by int("SwitchInterval", 400, 50..5000, "ms")
    internal val smoothDuration by int("SmoothDuration", 250, 50..2000, "ms")
    internal val returnSmoothDuration by int("ReturnSmoothDuration", 350, 50..2000, "ms")
    internal val combatSnapDuration by int("CombatSnapDuration", 120, 20..500, "ms")
    internal val range by float("Range", 10f, 1f..32f, "blocks")
    internal val randomWhenEmpty by boolean("RandomWhenEmpty", false)

    @Suppress("unused")
    private val renderHandler = handler<GameRenderEvent> {
        if (!running) {
            PlayerModelHysteriaState.reset()
            return@handler
        }

        val target = ModuleAmnesia.findTarget() ?: run {
            PlayerModelHysteriaState.reset()
            return@handler
        }

        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        PlayerModelHysteriaState.tick(
            target = target,
            partialTicks = partialTicks,
            switchInterval = switchInterval,
            hysteriaSmoothDuration = smoothDuration,
            returnSmoothDuration = returnSmoothDuration,
            combatSnapDuration = combatSnapDuration,
            range = range,
            randomWhenEmpty = randomWhenEmpty,
            delayRotationUpdateInterval = if (DelayPlayerModel.running) DelayPlayerModel.updateInterval else null,
            delayRotationSmoothDuration = if (DelayPlayerModel.running) DelayPlayerModel.smoothDuration else null,
        )
    }

    @Suppress("unused")
    private val damageHandler = handler<EntityHealthUpdateEvent> { event ->
        if (!running) {
            return@handler
        }

        if (event.new >= event.old) {
            return@handler
        }

        val amnesiaTarget = ModuleAmnesia.findTarget() ?: return@handler
        val entity = event.entity
        if (entity !is Player || entity.id == amnesiaTarget.id) {
            return@handler
        }

        if (ModuleAntiBot.isBot(entity)) {
            return@handler
        }

        if (amnesiaTarget.squaredBoxedDistanceTo(entity) > range.sq()) {
            return@handler
        }

        PlayerModelHysteriaState.triggerCombatSnapFromDamage(
            target = amnesiaTarget,
            entity = entity,
            partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true),
        )
    }
}
