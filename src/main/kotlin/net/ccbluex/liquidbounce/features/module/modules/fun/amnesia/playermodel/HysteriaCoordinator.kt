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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel

import net.ccbluex.liquidbounce.render.playermodel.PlayerModelVisualTransform
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

internal class HysteriaCoordinator {

    private var targetEntityId: Int? = null
    private var initialized = false
    private var wasSwinging = false
    private var combatSmoothDuration = 120
    private var lastFrameTime = System.currentTimeMillis()
    private val combatCooldown = Chronometer()
    private val rotation = HysteriaRotationState()
    private val phase = HysteriaPhaseController(rotation)

    fun tick(target: LivingEntity, partialTicks: Float, settings: HysteriaTickSettings) {
        if (initialized && targetEntityId != null && targetEntityId != target.id) {
            reset()
        }
        targetEntityId = target.id
        combatSmoothDuration = settings.combatSnapDuration.coerceAtLeast(1)
        rotation.configureLag(settings.rotationLagUpdateInterval, settings.rotationLagSmoothDuration)
        phase.configure(settings.smoothDuration)
        if (!initialized) {
            initialize(target, partialTicks, settings.range)
            return
        }

        val frameDeltaMs = frameDeltaMs()
        val candidates = HysteriaTargetResolver.collectCandidates(target, settings.range)
        triggerSwingCombat(target, partialTicks, settings)
        wasSwinging = target.swinging
        phase.advance(
            target,
            partialTicks,
            settings.range,
            candidates,
            frameDeltaMs,
            settings.switchInterval,
            settings.combatSnapDuration,
        )
    }

    fun triggerCombatSnapFromDamage(target: LivingEntity, entity: LivingEntity, partialTicks: Float) {
        if (!initialized || entity.id == targetEntityId || entity !is Player) {
            return
        }
        if (!HysteriaTargetResolver.isValid(entity) || !combatCooldown.hasElapsed(COMBAT_SNAP_COOLDOWN_MS)) {
            return
        }
        if (rotation.isLookingAt(target, entity, partialTicks)) {
            return
        }
        phase.beginCombat(target, entity, partialTicks, combatSmoothDuration)
        combatCooldown.reset()
    }

    fun transform(entity: Entity): PlayerModelVisualTransform? = phase.transform(entity, targetEntityId)

    fun reset() {
        targetEntityId = null
        initialized = false
        wasSwinging = false
        combatSmoothDuration = 120
        combatCooldown.reset()
        rotation.reset()
        phase.reset()
        lastFrameTime = System.currentTimeMillis()
    }

    private fun initialize(target: LivingEntity, partialTicks: Float, range: Float) {
        initialized = true
        combatCooldown.reset()
        rotation.initializeTimer()
        wasSwinging = target.swinging
        lastFrameTime = System.currentTimeMillis()
        phase.initialize(target, partialTicks, HysteriaTargetResolver.collectCandidates(target, range))
    }

    private fun triggerSwingCombat(
        target: LivingEntity,
        partialTicks: Float,
        settings: HysteriaTickSettings,
    ) {
        if (!target.swinging || wasSwinging) {
            return
        }
        HysteriaTargetResolver.findCombatPlayer(target, partialTicks, settings.range)?.let { player ->
            phase.beginCombat(target, player, partialTicks, settings.combatSnapDuration)
        }
    }

    private fun frameDeltaMs(): Int {
        val elapsed = (System.currentTimeMillis() - lastFrameTime).coerceAtLeast(1L).toInt()
        lastFrameTime = System.currentTimeMillis()
        return elapsed
    }

    private companion object {
        const val COMBAT_SNAP_COOLDOWN_MS = 150L
    }
}

internal data class HysteriaTickSettings(
    val switchInterval: Int,
    val smoothDuration: Int,
    val combatSnapDuration: Int,
    val range: Float,
    val rotationLagUpdateInterval: Int?,
    val rotationLagSmoothDuration: Int?,
)
