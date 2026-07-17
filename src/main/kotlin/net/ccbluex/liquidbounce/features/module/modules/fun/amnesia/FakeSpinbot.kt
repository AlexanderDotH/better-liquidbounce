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
import net.ccbluex.liquidbounce.event.events.GameRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.world.entity.LivingEntity

object FakeSpinbot : ToggleableValueGroup(
    ModuleAmnesia,
    "FakeSpinbot",
    false,
    aliases = listOf("Fake Spinbot"),
) {

    internal val spinSpeed by float("SpinSpeed", 720f, 30f..3600f, "deg/s")
    internal val pitch by float("Pitch", 90f, 45f..90f)
    internal val smoothDuration by int("SmoothDuration", 120, 0..1000, "ms")
    internal val attackSnapDuration by int("AttackSnapDuration", 180, 20..1000, "ms")
    internal val attackRange by float("AttackRange", 6f, 1f..12f, "blocks")

    @Suppress("unused")
    private val renderHandler = handler<GameRenderEvent>(priority = 2) {
        if (!running) {
            PlayerModelFakeSpinbotState.reset()
            return@handler
        }

        val target = ModuleAmnesia.findTarget() ?: run {
            PlayerModelFakeSpinbotState.reset()
            return@handler
        }

        PlayerModelFakeSpinbotState.tick(
            target = target,
            partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true),
            spinSpeed = spinSpeed,
            pitch = pitch,
            smoothDuration = smoothDuration,
            attackSnapDuration = attackSnapDuration,
            attackRange = attackRange,
        )
    }

    fun getTransform(entity: LivingEntity): PlayerModelVisualTransform? {
        if (!running) {
            return null
        }

        return PlayerModelFakeSpinbotState.getTransform(entity)
    }

    override fun onDisabled() {
        PlayerModelFakeSpinbotState.reset()
    }
}
