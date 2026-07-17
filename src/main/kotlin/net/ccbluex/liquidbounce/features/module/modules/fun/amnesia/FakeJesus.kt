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
import net.minecraft.world.phys.Vec3

object FakeJesus : ToggleableValueGroup(
    ModuleAmnesia,
    "FakeJesus",
    false,
    aliases = listOf("Fake Jesus"),
) {

    internal val surfaceOffset by float("SurfaceOffset", 0.08f, 0f..0.5f, "blocks")
    internal val bobAmount by float("BobAmount", 0.02f, 0f..0.12f, "blocks")
    internal val activationRange by float("ActivationRange", 0.65f, 0.1f..1.5f, "blocks")
    internal val smoothDuration by int("SmoothDuration", 120, 0..1000, "ms")
    internal val spoofStandingPose by boolean("SpoofStandingPose", true)

    @Suppress("unused")
    private val renderHandler = handler<GameRenderEvent>(priority = 2) {
        if (!running) {
            PlayerModelFakeJesusState.reset()
            return@handler
        }

        val target = ModuleAmnesia.findTarget() ?: run {
            PlayerModelFakeJesusState.reset()
            return@handler
        }

        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val visualPos = ModuleAmnesia.getAuxiliaryVisualPosition(target, partialTicks) ?: target.position()
        PlayerModelFakeJesusState.tick(
            target = target,
            visualPos = visualPos,
            surfaceOffset = surfaceOffset,
            bobAmount = bobAmount,
            activationRange = activationRange,
            smoothDuration = smoothDuration,
            spoofStandingPose = spoofStandingPose,
        )
    }

    fun getTransform(entity: LivingEntity, partialTicks: Float, basePosition: Vec3): PlayerModelVisualTransform? {
        if (!running) {
            return null
        }

        return PlayerModelFakeJesusState.getTransform(entity, partialTicks, basePosition)
    }

    fun getActionState(entity: LivingEntity): PlayerModelActionState? {
        if (!running) {
            return null
        }

        return PlayerModelFakeJesusState.getActionState(entity)
    }

    override fun onDisabled() {
        PlayerModelFakeJesusState.reset()
    }
}
