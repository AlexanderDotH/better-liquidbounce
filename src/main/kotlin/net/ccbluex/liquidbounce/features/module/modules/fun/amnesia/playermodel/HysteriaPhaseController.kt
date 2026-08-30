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
import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

internal class HysteriaPhaseController(private val rotation: HysteriaRotationState) {

    private var phase = HysteriaPhase.REAL
    private var currentGoalEntityId: Int? = null
    private var combatTargetEntityId: Int? = null
    private var activeSmoothDuration = 200
    private var auraSmoothDuration = 200
    private val switchTimer = Chronometer()
    private val phaseTimer = Chronometer()

    fun configure(hysteriaSmoothDuration: Int) {
        activeSmoothDuration = hysteriaSmoothDuration.coerceAtLeast(1)
        auraSmoothDuration = activeSmoothDuration
    }

    fun initialize(target: LivingEntity, partialTicks: Float, candidates: List<Player>) {
        switchTimer.reset()
        phaseTimer.reset()
        rotation.syncToReal(target, partialTicks)
        if (candidates.isNotEmpty()) {
            beginAura(target, partialTicks, candidates)
        } else {
            enterReal(target, partialTicks)
        }
    }

    fun advance(
        target: LivingEntity,
        partialTicks: Float,
        range: Float,
        candidates: List<Player>,
        frameDeltaMs: Int,
        switchInterval: Int,
        combatSnapDuration: Int,
    ) {
        when (phase) {
            HysteriaPhase.COMBAT -> tickCombat(
                target, partialTicks, range, candidates, frameDeltaMs, combatSnapDuration,
            )
            HysteriaPhase.REAL -> if (candidates.isNotEmpty()) beginAura(target, partialTicks, candidates)
            HysteriaPhase.AURA -> tickAura(target, partialTicks, range, candidates, frameDeltaMs, switchInterval)
        }
    }

    fun beginCombat(target: LivingEntity, entity: LivingEntity, partialTicks: Float, duration: Int) {
        combatTargetEntityId = entity.id
        currentGoalEntityId = entity.id
        phase = HysteriaPhase.COMBAT
        activeSmoothDuration = duration.coerceAtLeast(1)
        rotation.setGoal(target, entity, partialTicks, commitImmediately = true)
        phaseTimer.reset()
        switchTimer.reset()
    }

    fun transform(entity: Entity, targetEntityId: Int?): PlayerModelVisualTransform? =
        if (phase == HysteriaPhase.REAL) null else rotation.transform(entity, targetEntityId)

    fun reset() {
        currentGoalEntityId = null
        combatTargetEntityId = null
        phase = HysteriaPhase.REAL
        activeSmoothDuration = 200
        switchTimer.reset()
        phaseTimer.reset()
    }

    private fun beginAura(target: LivingEntity, partialTicks: Float, candidates: List<Player>) {
        val picked = HysteriaTargetResolver.pickRandom(candidates, currentGoalEntityId) ?: return
        currentGoalEntityId = picked.id
        combatTargetEntityId = null
        phase = HysteriaPhase.AURA
        activeSmoothDuration = auraSmoothDuration
        rotation.setGoal(target, picked, partialTicks, commitImmediately = true)
        switchTimer.reset()
    }

    private fun enterReal(target: LivingEntity, partialTicks: Float) {
        phase = HysteriaPhase.REAL
        currentGoalEntityId = null
        combatTargetEntityId = null
        rotation.syncToReal(target, partialTicks)
        switchTimer.reset()
        phaseTimer.reset()
    }

    private fun tickCombat(
        target: LivingEntity,
        partialTicks: Float,
        range: Float,
        candidates: List<Player>,
        frameDeltaMs: Int,
        combatSnapDuration: Int,
    ) {
        if (candidates.isEmpty()) {
            enterReal(target, partialTicks)
            return
        }
        val combatTarget = combatTargetEntityId?.let { world.getEntity(it) as? Player }
        if (combatTarget != null && HysteriaTargetResolver.isValid(combatTarget) &&
            HysteriaTargetResolver.isInRange(target, combatTarget, range)
        ) {
            rotation.setGoal(target, combatTarget, partialTicks)
        }
        rotation.step(frameDeltaMs, activeSmoothDuration)
        if (phaseTimer.hasElapsed(combatSnapDuration.toLong())) beginAura(target, partialTicks, candidates)
    }

    private fun tickAura(
        target: LivingEntity,
        partialTicks: Float,
        range: Float,
        candidates: List<Player>,
        frameDeltaMs: Int,
        switchInterval: Int,
    ) {
        if (candidates.isEmpty()) {
            enterReal(target, partialTicks)
            return
        }
        val current = currentGoalEntityId?.let { world.getEntity(it) as? Player }
        if (current != null && HysteriaTargetResolver.isValid(current) &&
            HysteriaTargetResolver.isInRange(target, current, range)
        ) {
            rotation.setGoal(target, current, partialTicks)
        } else {
            beginAura(target, partialTicks, candidates)
        }
        rotation.step(frameDeltaMs, activeSmoothDuration)
        if (switchTimer.hasElapsed(switchInterval.toLong())) beginAura(target, partialTicks, candidates)
    }

    private enum class HysteriaPhase {
        REAL,
        AURA,
        COMBAT,
    }
}
