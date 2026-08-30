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

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeCriticalsState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelActionState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelVisualTransform

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.EntityHealthUpdateEvent
import net.ccbluex.liquidbounce.event.events.GameRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.CriticalsMode
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

object FakeCriticals : ToggleableValueGroup(
    null,
    "FakeCriticals",
    false,
    aliases = listOf("Fake Criticals"),
) {

    internal val mode by enumChoice("Mode", CriticalsMode.BOTH)
    internal val range by float("Range", 4.5f, 1f..8f, "blocks")
    internal val triggerWindow by int("TriggerWindow", 250, 50..1000, "ms")
    internal val cooldown by int("Cooldown", 350, 50..2000, "ms")
    internal val criticalParticles by int("CriticalParticles", 6, 0..20)
    internal val magicParticles by int("MagicParticles", 0, 0..20)
    internal val microHopHeight by float("MicroHopHeight", 0.12f, 0.02f..0.42f, "blocks")
    internal val packetJitter by float("PacketJitter", 0.06f, 0f..0.25f, "blocks")
    internal val requireLineOfSight by boolean("RequireLineOfSight", false)
    internal val rotateToVictim by boolean("RotateToVictim", true)
    internal val swing by boolean("Swing", true)

    @Suppress("unused")
    private val renderHandler = handler<GameRenderEvent>(priority = 2) {
        if (!running) {
            PlayerModelFakeCriticalsState.reset()
            return@handler
        }

        val target = AmnesiaRuntimeBridge.findTarget() ?: run {
            PlayerModelFakeCriticalsState.reset()
            return@handler
        }

        PlayerModelFakeCriticalsState.tick(target)
    }

    @Suppress("unused")
    private val damageHandler = handler<EntityHealthUpdateEvent> { event ->
        if (!running || event.new >= event.old) {
            return@handler
        }

        val target = AmnesiaRuntimeBridge.findTarget() ?: return@handler
        val victim = event.entity
        if (!isValidVictim(target, victim)) {
            return@handler
        }

        PlayerModelFakeCriticalsState.trigger(
            target = target,
            victim = victim,
            partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true),
            mode = mode,
            triggerWindow = triggerWindow,
            cooldown = cooldown,
            criticalParticles = criticalParticles,
            magicParticles = magicParticles,
            microHopHeight = microHopHeight,
            packetJitter = packetJitter,
            rotateToVictim = rotateToVictim,
            swing = swing,
        )
    }

    fun getTransform(
        entity: LivingEntity,
        partialTicks: Float,
        basePosition: net.minecraft.world.phys.Vec3,
        velocityPositionActive: Boolean,
    ): PlayerModelVisualTransform? {
        if (!running) {
            return null
        }

        return PlayerModelFakeCriticalsState.getTransform(entity, partialTicks, basePosition, velocityPositionActive)
    }

    fun hasRotation(entity: LivingEntity): Boolean {
        if (!running) {
            return false
        }

        return PlayerModelFakeCriticalsState.hasRotation(entity)
    }

    fun getActionState(entity: LivingEntity): PlayerModelActionState? {
        if (!running) {
            return null
        }

        return PlayerModelFakeCriticalsState.getActionState(entity)
    }

    override fun onDisabled() {
        PlayerModelFakeCriticalsState.reset()
    }

    private fun isValidVictim(target: LivingEntity, victim: LivingEntity): Boolean {
        if (victim.id == target.id || victim.isRemoved) {
            return false
        }

        if (victim is Player && ModuleAntiBot.isBot(victim)) {
            return false
        }

        if (target.squaredBoxedDistanceTo(victim) > range.sq()) {
            return false
        }

        return !requireLineOfSight || target.hasLineOfSight(victim)
    }
}
