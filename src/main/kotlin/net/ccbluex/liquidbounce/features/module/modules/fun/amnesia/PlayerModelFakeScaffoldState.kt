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

import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.model.HumanoidModel
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin

@Suppress("TooManyFunctions")
object PlayerModelFakeScaffoldState {

    private const val ACTION_WINDOW_MS = 220L
    private const val MIN_HORIZONTAL_MOVEMENT_SQ = 1.0E-4

    private data class FakeBlock(val pos: BlockPos, val createdAt: Long)

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
    private val activeBlocks = LinkedHashMap<BlockPos, FakeBlock>()

    fun tick(
        target: LivingEntity,
        partialTicks: Float,
        visualPos: Vec3,
        style: FakeScaffold.ScaffoldStyle,
        placeInterval: Int,
        lifetime: Int,
        maxBlocks: Int,
        spoofSwing: Boolean,
        spoofSneak: Boolean,
        spoofDownRotations: Boolean,
        pitch: Float,
        yawMode: FakeScaffold.ScaffoldYawMode,
    ) {
        if (targetEntityId != null && targetEntityId != target.id) {
            reset()
        }

        targetEntityId = target.id
        expireBlocks(lifetime)

        val previousVisualPos = lastVisualPos
        val movement = previousVisualPos?.let(visualPos::subtract) ?: target.deltaMovement
        lastVisualPos = visualPos

        val interval = placeInterval.coerceAtLeast(1)
        if (target.tickCount - lastPlacementTick < interval) {
            return
        }

        val candidates = collectCandidates(target, visualPos, movement, style)
            .map(BlockPos::immutable)
            .filter(::canRenderFakeBlock)
            .distinct()

        if (candidates.isEmpty()) {
            return
        }

        val now = System.currentTimeMillis()
        var placed = false
        for (candidate in candidates) {
            if (activeBlocks.containsKey(candidate)) {
                continue
            }

            activeBlocks[candidate] = FakeBlock(candidate, now)
            placed = true
        }

        if (!placed) {
            return
        }

        lastPlacementTick = target.tickCount
        trimBlocks(maxBlocks)
        beginAction(target, partialTicks, movement, spoofSwing, spoofSneak, spoofDownRotations, pitch, yawMode)
    }

    fun activeBlockPositions(): Set<BlockPos> = activeBlocks.keys.toSet()

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
        val progress = (elapsed.toFloat() / ACTION_WINDOW_MS.toFloat()).coerceIn(0f, 1f)
        val swing = if (actionSwing) sin(progress * PI).toFloat().coerceIn(0f, 1f) else null

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
        activeBlocks.clear()
    }

    private fun collectCandidates(
        target: LivingEntity,
        visualPos: Vec3,
        movement: Vec3,
        style: FakeScaffold.ScaffoldStyle,
    ): List<BlockPos> {
        val base = blockBelow(visualPos)
        val horizontal = Vec3(movement.x, 0.0, movement.z)

        return when (style) {
            FakeScaffold.ScaffoldStyle.NORMAL -> normalCandidates(base, visualPos, horizontal)
            FakeScaffold.ScaffoldStyle.TELLY -> tellyCandidates(target, base, visualPos, horizontal)
            FakeScaffold.ScaffoldStyle.TOWER -> towerCandidates(target, base, horizontal)
        }
    }

    private fun normalCandidates(base: BlockPos, visualPos: Vec3, horizontal: Vec3): List<BlockPos> {
        if (horizontal.lengthSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ) {
            return listOf(base)
        }

        val behind = visualPos.subtract(horizontal.normalize().scale(0.7))
        return listOf(base, blockBelow(behind))
    }

    private fun tellyCandidates(
        target: LivingEntity,
        base: BlockPos,
        visualPos: Vec3,
        horizontal: Vec3,
    ): List<BlockPos> {
        val airborne = !target.onGround() || target.deltaMovement.y < -0.03
        if (!airborne) {
            return emptyList()
        }

        if (horizontal.lengthSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ) {
            return listOf(base)
        }

        val forward = visualPos.add(horizontal.normalize().scale(0.8))
        return listOf(base, blockBelow(forward))
    }

    private fun towerCandidates(target: LivingEntity, base: BlockPos, horizontal: Vec3): List<BlockPos> {
        val movingUp = target.deltaMovement.y > 0.03
        val mostlyStationary = horizontal.lengthSqr() < 0.018
        if (!movingUp || !mostlyStationary) {
            return emptyList()
        }

        return listOf(base, base.below())
    }

    private fun blockBelow(pos: Vec3): BlockPos = BlockPos.containing(pos.x, pos.y - 0.05, pos.z).below()

    private fun canRenderFakeBlock(pos: BlockPos): Boolean {
        val level = mc.level ?: return false
        if (!level.worldBorder.isWithinBounds(pos)) {
            return false
        }

        val state = pos.stateOrEmpty
        return state.isAir || state.canBeReplaced()
    }

    private fun beginAction(
        target: LivingEntity,
        partialTicks: Float,
        movement: Vec3,
        spoofSwing: Boolean,
        spoofSneak: Boolean,
        spoofDownRotations: Boolean,
        pitch: Float,
        yawMode: FakeScaffold.ScaffoldYawMode,
    ) {
        val now = System.currentTimeMillis()
        actionStartedAt = now
        actionUntil = now + ACTION_WINDOW_MS
        actionCrouching = spoofSneak
        actionSwing = spoofSwing
        spoofRotations = spoofDownRotations
        actionPitch = pitch.coerceIn(45f, 90f)
        actionBodyYaw = scaffoldYaw(target, partialTicks, movement, yawMode)
        actionHeadYaw = actionBodyYaw
    }

    private fun scaffoldYaw(
        target: LivingEntity,
        partialTicks: Float,
        movement: Vec3,
        yawMode: FakeScaffold.ScaffoldYawMode,
    ): Float {
        val horizontal = Vec3(movement.x, 0.0, movement.z)
        if (horizontal.lengthSqr() <= MIN_HORIZONTAL_MOVEMENT_SQ) {
            return target.getViewYRot(partialTicks)
        }

        var yaw = Math.toDegrees(atan2(horizontal.z, horizontal.x)).toFloat() - 90f
        if (yawMode == FakeScaffold.ScaffoldYawMode.REVERSE) {
            yaw += 180f
        }

        if (yawMode == FakeScaffold.ScaffoldYawMode.SNAP_45) {
            yaw = (yaw / 45f).roundToInt() * 45f
        }

        return Mth.wrapDegrees(yaw)
    }

    private fun expireBlocks(lifetime: Int) {
        val now = System.currentTimeMillis()
        val maxAge = lifetime.coerceAtLeast(1).toLong()
        activeBlocks.entries.removeIf { now - it.value.createdAt > maxAge }
    }

    private fun trimBlocks(maxBlocks: Int) {
        val limit = maxBlocks.coerceAtLeast(1)
        while (activeBlocks.size > limit) {
            val oldest = activeBlocks.keys.firstOrNull() ?: return
            activeBlocks.remove(oldest)
        }
    }

    private fun isActiveFor(entity: LivingEntity): Boolean {
        if (entity.id != targetEntityId) {
            return false
        }

        return System.currentTimeMillis() <= actionUntil
    }
}
