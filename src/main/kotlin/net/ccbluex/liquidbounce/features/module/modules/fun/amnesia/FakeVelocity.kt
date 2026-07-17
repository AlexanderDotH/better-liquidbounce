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

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.EntityHealthUpdateEvent
import net.ccbluex.liquidbounce.event.events.GameRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.utils.client.mc

object FakeVelocity : ToggleableValueGroup(
    ModuleAmnesia,
    "FakeVelocity",
    false,
    aliases = listOf("Fake Velocity"),
) {

    internal val mode by enumChoice("Mode", VelocityMode.NO_VELOCITY)
    internal val resumeDistance by float("ResumeDistance", 1.5f, 0.5f..6f, "blocks")
    internal val teleportDistance by float("TeleportDistance", 8f, 2f..32f, "blocks")
    internal val minFreezeDuration by int("MinFreezeDuration", 150, 0..1000, "ms")
    internal val retainedMotion by float("RetainedMotion", 0.85f, 0f..1.25f)
    internal val recoveryDuration by int("RecoveryDuration", 250, 50..1000, "ms")
    internal val maxDesync by float("MaxDesync", 3f, 0.5f..8f, "blocks")
    internal val tinyRecoil by float("TinyRecoil", 0.05f, 0f..0.5f, "blocks")

    enum class VelocityMode(override val tag: String) : Tagged {
        FREEZE("Freeze"),
        NO_VELOCITY("NoVelocity"),
    }

    @Suppress("unused")
    private val renderHandler = handler<GameRenderEvent>(priority = 1) {
        if (!running) {
            PlayerModelFakeVelocityState.reset()
            return@handler
        }

        val target = ModuleAmnesia.findTarget() ?: run {
            PlayerModelFakeVelocityState.reset()
            return@handler
        }

        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        PlayerModelFakeVelocityState.tick(
            target = target,
            partialTicks = partialTicks,
            mode = mode,
            resumeDistance = resumeDistance,
            teleportDistance = teleportDistance,
            minFreezeDuration = minFreezeDuration,
            retainedMotion = retainedMotion,
            recoveryDuration = recoveryDuration,
            maxDesync = maxDesync,
            tinyRecoil = tinyRecoil,
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

        val target = ModuleAmnesia.findTarget() ?: return@handler
        if (event.entity.id != target.id) {
            return@handler
        }

        PlayerModelFakeVelocityState.queueFreezeFromDamage(target)
    }
}
