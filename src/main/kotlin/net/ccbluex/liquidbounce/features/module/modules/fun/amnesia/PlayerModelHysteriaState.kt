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

import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.interpolateBodyYaw
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentRotation
import net.ccbluex.liquidbounce.utils.entity.interpolateHeadYaw
import net.ccbluex.liquidbounce.utils.entity.interpolatePitch
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

@Suppress("TooManyFunctions")
object PlayerModelHysteriaState {

    private const val COMBAT_LOOK_TOLERANCE = 45f
    private const val COMBAT_SNAP_COOLDOWN_MS = 150L

    private enum class Phase {
        REAL,
        AURA,
        COMBAT,
    }

    private var targetEntityId: Int? = null
    private var displayBodyYaw = 0f
    private var displayHeadYaw = 0f
    private var displayPitch = 0f
    private var goalBodyYaw = 0f
    private var goalHeadYaw = 0f
    private var goalPitch = 0f
    private var currentGoalEntityId: Int? = null
    private var combatTargetEntityId: Int? = null
    private var phase = Phase.REAL
    private var activeSmoothDuration = 200
    private var auraSmoothDuration = 200
    private var combatSmoothDuration = 120
    private var rotationLagEnabled = false
    private var rotationLagUpdateInterval = 500
    private var rotationLagSmoothDuration = 200
    private var idealBodyYaw = 0f
    private var idealHeadYaw = 0f
    private var idealPitch = 0f
    private var initialized = false
    private var wasSwinging = false
    private var lastPartialTicks = 0f
    private val switchTimer = Chronometer()
    private val phaseTimer = Chronometer()
    private val combatCooldown = Chronometer()
    private val rotationSnapTimer = Chronometer()
    private var lastFrameTime = System.currentTimeMillis()

    @Suppress("CognitiveComplexMethod", "LongMethod")
    fun tick(
        target: LivingEntity,
        partialTicks: Float,
        switchInterval: Int,
        hysteriaSmoothDuration: Int,
        @Suppress("UNUSED_PARAMETER") returnSmoothDuration: Int,
        combatSnapDuration: Int,
        range: Float,
        @Suppress("UNUSED_PARAMETER") randomWhenEmpty: Boolean,
        delayRotationUpdateInterval: Int? = null,
        delayRotationSmoothDuration: Int? = null,
    ) {
        if (initialized && targetEntityId != null && targetEntityId != target.id) {
            reset()
        }

        targetEntityId = target.id
        lastPartialTicks = partialTicks
        activeSmoothDuration = hysteriaSmoothDuration.coerceAtLeast(1)
        auraSmoothDuration = activeSmoothDuration
        combatSmoothDuration = combatSnapDuration.coerceAtLeast(1)
        rotationLagEnabled = delayRotationUpdateInterval != null && delayRotationSmoothDuration != null
        if (rotationLagEnabled) {
            rotationLagUpdateInterval = delayRotationUpdateInterval!!.coerceAtLeast(1)
            rotationLagSmoothDuration = delayRotationSmoothDuration!!.coerceAtLeast(1)
        }

        if (!initialized) {
            initialized = true
            switchTimer.reset()
            phaseTimer.reset()
            combatCooldown.reset()
            rotationSnapTimer.reset()
            wasSwinging = target.swinging
            lastFrameTime = System.currentTimeMillis()

            val candidates = collectPlayerCandidates(target, range)
            syncDisplayToReal(target, partialTicks)
            if (candidates.isNotEmpty()) {
                beginAuraSnap(target, partialTicks, candidates)
            } else {
                enterReal(target, partialTicks)
            }
            return
        }

        val frameDeltaMs = (System.currentTimeMillis() - lastFrameTime).coerceAtLeast(1L).toInt()
        lastFrameTime = System.currentTimeMillis()

        val candidates = collectPlayerCandidates(target, range)
        val hasCandidates = candidates.isNotEmpty()

        if (target.swinging && !wasSwinging) {
            findCombatPlayer(target, partialTicks, range)?.let { player ->
                beginCombatSnap(target, player, partialTicks, combatSnapDuration)
            }
        }
        wasSwinging = target.swinging

        when (phase) {
            Phase.COMBAT -> {
                if (!hasCandidates) {
                    enterReal(target, partialTicks)
                    return
                }

                updateCombatGoal(target, partialTicks, range)
                applyRotationStep(frameDeltaMs)
                if (phaseTimer.hasElapsed(combatSnapDuration.toLong())) {
                    beginAuraSnap(target, partialTicks, candidates)
                }
            }

            Phase.REAL -> {
                if (hasCandidates) {
                    beginAuraSnap(target, partialTicks, candidates)
                }
            }

            Phase.AURA -> {
                if (!hasCandidates) {
                    enterReal(target, partialTicks)
                    return
                }

                trackAuraGoal(target, partialTicks, range, candidates)
                applyRotationStep(frameDeltaMs)

                if (switchTimer.hasElapsed(switchInterval.toLong())) {
                    beginAuraSnap(target, partialTicks, candidates)
                }
            }
        }
    }

    fun triggerCombatSnapFromDamage(
        target: LivingEntity,
        entity: LivingEntity,
        partialTicks: Float,
    ) {
        if (!initialized || entity.id == targetEntityId || entity !is Player) {
            return
        }

        if (!isValidPlayer(entity)) {
            return
        }

        if (!combatCooldown.hasElapsed(COMBAT_SNAP_COOLDOWN_MS)) {
            return
        }

        if (isDisplayLookingAt(target, entity, partialTicks)) {
            return
        }

        beginCombatSnap(target, entity, partialTicks, combatSmoothDuration)
        combatCooldown.reset()
    }

    fun getTransform(entity: Entity): PlayerModelVisualTransform? {
        if (entity.id != targetEntityId || !initialized || phase == Phase.REAL) {
            return null
        }

        return PlayerModelVisualTransform(
            position = null,
            bodyYaw = displayBodyYaw,
            headYaw = displayHeadYaw,
            pitch = displayPitch,
        )
    }

    fun reset() {
        targetEntityId = null
        currentGoalEntityId = null
        combatTargetEntityId = null
        phase = Phase.REAL
        activeSmoothDuration = 200
        rotationLagEnabled = false
        initialized = false
        wasSwinging = false
        lastPartialTicks = 0f
        switchTimer.reset()
        phaseTimer.reset()
        combatCooldown.reset()
        rotationSnapTimer.reset()
        lastFrameTime = System.currentTimeMillis()
    }

    private fun beginCombatSnap(
        target: LivingEntity,
        entity: LivingEntity,
        partialTicks: Float,
        combatSnapDuration: Int,
    ) {
        combatTargetEntityId = entity.id
        currentGoalEntityId = entity.id
        phase = Phase.COMBAT
        activeSmoothDuration = combatSnapDuration.coerceAtLeast(1)
        setGoalFromEntity(target, entity, partialTicks, commitImmediately = true)
        phaseTimer.reset()
        switchTimer.reset()
    }

    private fun beginAuraSnap(
        target: LivingEntity,
        partialTicks: Float,
        candidates: List<Player>,
    ) {
        val picked = pickRandomCandidate(candidates, currentGoalEntityId) ?: return

        currentGoalEntityId = picked.id
        combatTargetEntityId = null
        phase = Phase.AURA
        activeSmoothDuration = auraSmoothDuration
        setGoalFromEntity(target, picked, partialTicks, commitImmediately = true)
        switchTimer.reset()
    }

    private fun enterReal(target: LivingEntity, partialTicks: Float) {
        phase = Phase.REAL
        currentGoalEntityId = null
        combatTargetEntityId = null
        syncDisplayToReal(target, partialTicks)
        switchTimer.reset()
        phaseTimer.reset()
    }

    private fun trackAuraGoal(
        target: LivingEntity,
        partialTicks: Float,
        range: Float,
        candidates: List<Player>,
    ) {
        val entityId = currentGoalEntityId
        val current = entityId?.let { world.getEntity(it) as? Player }

        if (current != null && isValidPlayer(current) && isInRange(target, current, range)) {
            setGoalFromEntity(target, current, partialTicks)
            return
        }

        beginAuraSnap(target, partialTicks, candidates)
    }

    private fun updateCombatGoal(target: LivingEntity, partialTicks: Float, range: Float) {
        val entityId = combatTargetEntityId ?: return
        val entity = world.getEntity(entityId) as? Player ?: run {
            return
        }

        if (!isValidPlayer(entity) || !isInRange(target, entity, range)) {
            return
        }

        setGoalFromEntity(target, entity, partialTicks)
    }

    private fun syncDisplayToReal(target: LivingEntity, partialTicks: Float) {
        val rotation = realRotation(target, partialTicks)
        displayBodyYaw = rotation.bodyYaw
        displayHeadYaw = rotation.headYaw
        displayPitch = rotation.pitch
        goalBodyYaw = rotation.bodyYaw
        goalHeadYaw = rotation.headYaw
        goalPitch = rotation.pitch
    }

    private fun setGoalFromEntity(
        target: LivingEntity,
        entity: LivingEntity,
        partialTicks: Float,
        commitImmediately: Boolean = false,
    ) {
        val rotation = aimAtEntity(target, entity, partialTicks)
        if (rotationLagEnabled) {
            applyIdeal(rotation)
            if (commitImmediately) {
                commitGoalFromIdeal()
                rotationSnapTimer.reset()
            }
        } else {
            applyGoal(rotation)
        }
    }

    private fun applyIdeal(rotation: ModelRotation) {
        idealBodyYaw = rotation.bodyYaw
        idealHeadYaw = rotation.headYaw
        idealPitch = rotation.pitch
    }

    private fun commitGoalFromIdeal() {
        goalBodyYaw = idealBodyYaw
        goalHeadYaw = idealHeadYaw
        goalPitch = idealPitch
    }

    private fun applyGoal(rotation: ModelRotation) {
        goalBodyYaw = rotation.bodyYaw
        goalHeadYaw = rotation.headYaw
        goalPitch = rotation.pitch
    }

    private fun applyRotationStep(frameDeltaMs: Int) {
        if (rotationLagEnabled && rotationSnapTimer.hasElapsed(rotationLagUpdateInterval.toLong())) {
            commitGoalFromIdeal()
            rotationSnapTimer.reset()
        }

        applySmoothStep(frameDeltaMs)
    }

    private fun applySmoothStep(frameDeltaMs: Int) {
        val duration = if (rotationLagEnabled) {
            rotationLagSmoothDuration
        } else {
            activeSmoothDuration
        }
        val t = (frameDeltaMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        displayBodyYaw = Mth.rotLerp(t, displayBodyYaw, goalBodyYaw)
        displayHeadYaw = Mth.rotLerp(t, displayHeadYaw, goalHeadYaw)
        displayPitch = Mth.lerp(t, displayPitch, goalPitch)
    }

    private fun isDisplayLookingAt(
        target: LivingEntity,
        entity: LivingEntity,
        partialTicks: Float,
    ): Boolean {
        val eyes = target.getEyePosition(partialTicks)
        val aimRotation = Rotation.lookingAt(aimPoint(entity), eyes)
        val displayRotation = Rotation(displayHeadYaw, displayPitch)
        return displayRotation.angleTo(aimRotation) <= COMBAT_LOOK_TOLERANCE
    }

    private fun realRotation(target: LivingEntity, partialTicks: Float): ModelRotation {
        return ModelRotation(
            bodyYaw = target.interpolateBodyYaw(partialTicks),
            headYaw = target.interpolateHeadYaw(partialTicks),
            pitch = target.interpolatePitch(partialTicks),
        )
    }

    private fun findCombatPlayer(
        target: LivingEntity,
        partialTicks: Float,
        range: Float,
    ): Player? {
        val rotation = target.interpolateCurrentRotation(partialTicks)
        val rayHit = target.findEntityInCrosshair(range.toDouble(), rotation) { entity ->
            entity is Player && entity.id != target.id && isValidPlayer(entity)
        }?.entity as? Player

        if (rayHit != null) {
            return rayHit
        }

        val eyes = target.getEyePosition(partialTicks)
        val rangeSq = range.sq()

        var best: Player? = null
        var bestAngle = Float.MAX_VALUE

        for (entity in world.entitiesForRendering()) {
            if (entity !is Player || !isValidPlayer(entity) || entity.id == target.id) {
                continue
            }

            if (target.squaredBoxedDistanceTo(entity) > rangeSq) {
                continue
            }

            val rotationToEntity = Rotation.lookingAt(aimPoint(entity), eyes)
            val angle = rotation.angleTo(rotationToEntity)
            if (angle < bestAngle) {
                bestAngle = angle
                best = entity
            }
        }

        return best?.takeIf { bestAngle <= COMBAT_LOOK_TOLERANCE }
    }

    private fun collectPlayerCandidates(target: LivingEntity, range: Float): List<Player> {
        val rangeSq = range.sq()
        val candidates = ArrayList<Player>()

        for (entity in world.entitiesForRendering()) {
            if (entity !is Player || !isValidPlayer(entity) || entity.id == target.id) {
                continue
            }

            if (target.squaredBoxedDistanceTo(entity) <= rangeSq) {
                candidates.add(entity)
            }
        }

        val localPlayer = mc.player
        if (localPlayer != null
            && localPlayer.id != target.id
            && isValidPlayer(localPlayer)
            && target.squaredBoxedDistanceTo(localPlayer) <= rangeSq
            && candidates.none { it.id == localPlayer.id }
        ) {
            candidates.add(localPlayer)
        }

        return candidates
    }

    private fun isValidPlayer(player: Player): Boolean {
        return !player.isRemoved
            && player.isAlive
            && !player.isSpectator
            && !ModuleAntiBot.isBot(player)
    }

    private fun isInRange(target: LivingEntity, player: Player, range: Float): Boolean =
        target.squaredBoxedDistanceTo(player) <= range.sq()

    private fun pickRandomCandidate(
        candidates: List<Player>,
        currentEntityId: Int?,
    ): Player? {
        if (candidates.isEmpty()) {
            return null
        }

        if (candidates.size == 1) {
            return candidates[0]
        }

        if (currentEntityId != null) {
            val others = candidates.filter { it.id != currentEntityId }
            if (others.isNotEmpty()) {
                return others.random()
            }
        }

        return candidates.random()
    }

    private fun aimAtEntity(
        target: LivingEntity,
        entity: LivingEntity,
        partialTicks: Float,
    ): ModelRotation {
        val eyes = target.getEyePosition(partialTicks)
        val rotation = Rotation.lookingAt(aimPoint(entity), eyes)
        val yaw = rotation.yaw
        val pitch = rotation.pitch.coerceIn(-90f, 90f)
        return ModelRotation(yaw, yaw, pitch)
    }

    private fun aimPoint(entity: LivingEntity) = entity.eyePosition

    private data class ModelRotation(
        val bodyYaw: Float,
        val headYaw: Float,
        val pitch: Float,
    )
}
