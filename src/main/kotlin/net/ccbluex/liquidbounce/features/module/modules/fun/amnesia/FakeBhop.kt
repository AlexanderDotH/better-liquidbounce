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

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeBhopState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelActionState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelVisualTransform

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.GameRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.BhopStyle
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

object FakeBhop : ToggleableValueGroup(
    null,
    "FakeBhop",
    false,
    aliases = listOf("Fake Bhop", "Fake BunnyHop"),
) {

    internal val style by enumChoice("Style", BhopStyle.NORMAL)
    internal val hopHeight by float("HopHeight", 0.42f, 0.05f..0.8f, "blocks")
    internal val hopInterval by int("HopInterval", 380, 120..900, "ms")
    internal val minMoveSpeed by float("MinMoveSpeed", 0.04f, 0.005f..0.25f, "blocks/tick")
    internal val strafeAmount by float("StrafeAmount", 0.05f, 0f..0.25f, "blocks")
    internal val rotateToMovement by boolean("RotateToMovement", true)
    internal val pitch by float("Pitch", 8f, -30f..45f)
    internal val spoofGroundPose by boolean("SpoofGroundPose", true)
    internal val smoothStopDuration by int("SmoothStopDuration", 140, 0..500, "ms")

    @Suppress("unused")
    private val renderHandler = handler<GameRenderEvent>(priority = 2) {
        if (!running) {
            PlayerModelFakeBhopState.reset()
            return@handler
        }

        val target = AmnesiaRuntimeBridge.findTarget() ?: run {
            PlayerModelFakeBhopState.reset()
            return@handler
        }

        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val visualPos = AmnesiaRuntimeBridge.auxiliaryVisualPosition(target, partialTicks) ?: target.position()
        PlayerModelFakeBhopState.tick(
            target = target,
            partialTicks = partialTicks,
            visualPos = visualPos,
            style = style,
            hopHeight = hopHeight,
            hopInterval = hopInterval,
            minMoveSpeed = minMoveSpeed,
            strafeAmount = strafeAmount,
            rotateToMovement = rotateToMovement,
            pitch = pitch,
            spoofGroundPose = spoofGroundPose,
            smoothStopDuration = smoothStopDuration,
        )
    }

    fun getTransform(
        entity: LivingEntity,
        partialTicks: Float,
        basePosition: Vec3,
        velocityPositionActive: Boolean,
    ): PlayerModelVisualTransform? {
        if (!running) {
            return null
        }

        return PlayerModelFakeBhopState.getTransform(entity, partialTicks, basePosition, velocityPositionActive)
    }

    fun hasRotation(entity: LivingEntity): Boolean = running && PlayerModelFakeBhopState.hasRotation(entity)

    fun getActionState(entity: LivingEntity): PlayerModelActionState? {
        if (!running) {
            return null
        }

        return PlayerModelFakeBhopState.getActionState(entity)
    }

    override fun onDisabled() {
        PlayerModelFakeBhopState.reset()
    }
}
