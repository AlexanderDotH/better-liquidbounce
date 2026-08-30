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

import net.ccbluex.liquidbounce.render.playermodel.PlayerModelActionState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelVisualTransform
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.ScaffoldStyle
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.ScaffoldYawMode

import net.minecraft.client.model.HumanoidModel
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

object PlayerModelFakeScaffoldState {

    private var targetEntityId: Int? = null
    private var lastPlacementTick = Int.MIN_VALUE
    private var lastVisualPos: Vec3? = null
    private var actionStartedAt = 0L
    private var actionUntil = 0L
    private var actionCrouching = false
    private var actionSwing = false
    private var actionBodyYaw = 0f
    private var actionHeadYaw = 0f
    private var actionPitch = 78f
    private var spoofRotations = false
    private val blockStore = ScaffoldBlockStore()

    fun tick(
        target: LivingEntity,
        partialTicks: Float,
        visualPos: Vec3,
        style: ScaffoldStyle,
        placeInterval: Int,
        lifetime: Int,
        maxBlocks: Int,
        spoofSwing: Boolean,
        spoofSneak: Boolean,
        spoofDownRotations: Boolean,
        pitch: Float,
        yawMode: ScaffoldYawMode,
    ) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
        }

        targetEntityId = target.id
        blockStore.expire(lifetime)

        val previousVisualPos = lastVisualPos
        val movement = previousVisualPos?.let(visualPos::subtract) ?: target.deltaMovement
        lastVisualPos = visualPos

        val interval = placeInterval.coerceAtLeast(1)
        if (target.tickCount - lastPlacementTick < interval) {
            return
        }

        val candidates = ScaffoldCandidatePlanner.collect(target, visualPos, movement, style)

        if (candidates.isEmpty()) {
            return
        }

        if (!blockStore.add(candidates, System.currentTimeMillis())) {
            return
        }

        lastPlacementTick = target.tickCount
        blockStore.trim(maxBlocks)
        beginAction(target, partialTicks, movement, spoofSwing, spoofSneak, spoofDownRotations, pitch, yawMode)
    }

    fun activeBlockPositions(): Set<BlockPos> = blockStore.positions()

    fun getTransform(entity: LivingEntity): PlayerModelVisualTransform? {
        if (!isActiveFor(entity) || !spoofRotations) {
            return null
        }

        return PlayerModelVisualTransform(
            position = null,
            bodyYaw = actionBodyYaw,
            headYaw = actionHeadYaw,
            pitch = actionPitch,
        )
    }

    fun getActionState(entity: LivingEntity): PlayerModelActionState? {
        if (!isActiveFor(entity)) {
            return null
        }

        val elapsed = (System.currentTimeMillis() - actionStartedAt).coerceAtLeast(0L)
        val swing = if (actionSwing) ScaffoldActionMath.swingProgress(elapsed) else null

        if (!actionCrouching && swing == null) {
            return null
        }

        return PlayerModelActionState(
            crouching = actionCrouching,
            swingProgress = swing,
            armPose = if (swing != null) HumanoidModel.ArmPose.ITEM else null,
        )
    }

    fun reset() {
        targetEntityId = null
        lastPlacementTick = Int.MIN_VALUE
        lastVisualPos = null
        actionStartedAt = 0L
        actionUntil = 0L
        actionCrouching = false
        actionSwing = false
        actionBodyYaw = 0f
        actionHeadYaw = 0f
        actionPitch = 78f
        spoofRotations = false
        blockStore.clear()
    }

    private fun beginAction(
        target: LivingEntity,
        partialTicks: Float,
        movement: Vec3,
        spoofSwing: Boolean,
        spoofSneak: Boolean,
        spoofDownRotations: Boolean,
        pitch: Float,
        yawMode: ScaffoldYawMode,
    ) {
        val now = System.currentTimeMillis()
        actionStartedAt = now
        actionUntil = now + ScaffoldActionMath.ACTION_WINDOW_MS
        actionCrouching = spoofSneak
        actionSwing = spoofSwing
        spoofRotations = spoofDownRotations
        actionPitch = pitch.coerceIn(45f, 90f)
        actionBodyYaw = ScaffoldActionMath.yaw(movement, target.getViewYRot(partialTicks), yawMode)
        actionHeadYaw = actionBodyYaw
    }

    private fun isActiveFor(entity: LivingEntity): Boolean {
        if (entity.id != targetEntityId) {
            return false
        }

        return System.currentTimeMillis() <= actionUntil
    }
}
