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
package net.ccbluex.liquidbounce.features.module.modules.movement.noweb.modes

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.noweb.runtime.NoWebModuleProvider
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.features.rotation.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.DIRECTIONS_EXCLUDING_DOWN
import net.ccbluex.liquidbounce.features.block.runtime.doPlacement
import net.ccbluex.liquidbounce.utils.block.immutable
import net.ccbluex.liquidbounce.utils.block.liquid.TimedPickupTracker
import net.ccbluex.liquidbounce.features.block.planner.planPlacementAtPos
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.findClosestSlot
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.WebBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object NoWebPlaceWater : NoWebMode("PlaceWater") {
    private object Pickup : ToggleableValueGroup(this@NoWebPlaceWater, "Pickup", true) {
        // Keep a hard lower bound so water has enough time to spread at least one block.
        val pickupSpan by floatRange("PickupSpan", 0.8F..3.0F, 0.5F..20.0F, "s")
    }

    private val rotations = tree(RotationsValueGroup(this))
    private val pickupTracker = TimedPickupTracker(PICKUP_TRACKER_CAPACITY)
    private val trackedWebs = ObjectLinkedOpenHashSet<BlockPos>()

    private var currentAction: NoWebUseAction? = null
    private var lastSuccessfulWeb: BlockPos? = null
    private var lastSuccessfulAt = 0L

    init {
        tree(Pickup)
    }

    override fun disable() {
        SilentHotbar.resetSlot(this)
        resetState()
    }

    override fun handleEntityCollision(pos: BlockPos): Boolean {
        trackedWebs.add(pos.immutable)
        while (trackedWebs.size > MAX_TRACKED_WEBS) {
            trackedWebs.removeFirst()
        }

        return false
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        resetState()
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        currentAction = null

        val waterEvaporates = world.environmentAttributes()
            .getDimensionValue(EnvironmentAttributes.WATER_EVAPORATES)
        if (!allowsNoWebWaterPlacement(waterEvaporates)) {
            return@handler
        }

        trackedWebs.removeIf { trackedPos -> trackedPos.state?.block !is WebBlock }

        val now = System.currentTimeMillis()
        val placeAction = Slots.OffhandWithHotbar.findClosestSlot(Items.WATER_BUCKET)?.let { waterSlot ->
            trackedWebs.firstNotNullOfOrNull { webPos ->
                if (lastSuccessfulWeb == webPos && now - lastSuccessfulAt <= SAME_WEB_RETRY_DELAY_MS) {
                    return@firstNotNullOfOrNull null
                }

                buildPlaceAction(webPos, waterSlot)
            }
        }

        currentAction = placeAction ?: buildPickupAction()

        val action = currentAction ?: return@handler
        RotationManager.setRotationTarget(
            action.rotation,
            valueGroup = rotations,
            priority = Priority.IMPORTANT_FOR_PLAYER_LIFE,
            provider = NoWebModuleProvider.module,
        )
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val action = currentAction ?: return@handler
        val rotation = RotationManager.currentRotation ?: player.rotation
        val resolvedHitResult = action.resolveHitResult(traceFromPlayer(rotation)) ?: return@handler

        SilentHotbar.selectSlotSilently(this, action.slot, 1)
        val onSuccess = {
            action.onSuccess(resolvedHitResult)
            true
        }

        doPlacement(
            resolvedHitResult,
            rotation,
            hand = action.slot.useHand,
            onItemUseSuccess = onSuccess,
            onPlacementSuccess = onSuccess,
        )

        currentAction = null
    }

    private fun buildPlaceAction(
        webPos: BlockPos,
        waterSlot: HotbarItemSlot,
    ): NoWebUseAction? {
        val webBox = AABB(webPos)
        val eyes = player.eyePosition

        return when {
            webBox.contains(eyes) -> buildDirectionalPlaceAction(webPos, waterSlot, DIRECTIONS_EXCLUDING_DOWN)
            eyes.y > webBox.maxY -> buildTopPlaceAction(webPos, waterSlot)
            else -> buildDirectionalPlaceAction(webPos, waterSlot, Direction.BY_2D_DATA)
        }
    }

    private fun buildTopPlaceAction(
        webPos: BlockPos,
        waterSlot: HotbarItemSlot,
    ): NoWebUseAction? {
        val plan = planPlacementAtPos(webPos.above(), waterSlot) ?: return null

        return NoWebUseAction(
            slot = plan.hotbarItemSlot,
            rotation = plan.placementTarget.rotation,
            resolveHitResult = { rayTraceResult ->
                if (plan.doesCorrespondTo(rayTraceResult)) rayTraceResult else null
            },
            onSuccess = {
                markWebPlacementSuccess(webPos)
                pickupTracker.record(plan.targetPos)
            },
        )
    }

    private fun buildDirectionalPlaceAction(
        webPos: BlockPos,
        waterSlot: HotbarItemSlot,
        directions: Array<Direction>,
    ): NoWebUseAction? {
        val side = pickBestNoWebSide(webPos, directions, player.lookAngle) ?: return null
        val faceCenter = noWebCenterOnSide(AABB(webPos), side)
        val fallbackHitResult = BlockHitResult(faceCenter, side, webPos, false)

        return NoWebUseAction(
            slot = waterSlot,
            rotation = Rotation.lookingAt(point = faceCenter, from = player.eyePosition),
            resolveHitResult = { rayTraceResult ->
                resolveNoWebDirectionalPlacementHitResult(rayTraceResult, webPos, side, fallbackHitResult)
            },
            onSuccess = { placementHitResult ->
                markWebPlacementSuccess(webPos)
                noWebDirectionalWaterCandidates(webPos, side, placementHitResult)
                    .forEach(pickupTracker::record)
            },
        )
    }

    private fun buildPickupAction(): NoWebUseAction? {
        if (!Pickup.enabled) {
            return null
        }

        val pickupSpanStartMs = (Pickup.pickupSpan.start * 1000.0F).toLong()
        val pickupSpanEndMs = (Pickup.pickupSpan.endInclusive * 1000.0F).toLong()

        pickupTracker.prune(pickupSpanEndMs, TimedPickupTracker.PickupFilter.WATER)

        val maxRangeSq = noWebSquaredRange(player.blockInteractionRange())
        val pickupPos = pickupTracker.firstEligible(pickupSpanStartMs) { pos ->
            AABB(pos).distanceToSqr(player.eyePosition) <= maxRangeSq
        } ?: return null

        val bucketSlot = Slots.OffhandWithHotbar.findClosestSlot(Items.BUCKET) ?: return null
        val pickupCenter = Vec3.atCenterOf(pickupPos)

        return NoWebUseAction(
            slot = bucketSlot,
            rotation = Rotation.lookingAt(point = pickupCenter, from = player.eyePosition),
            resolveHitResult = { rayTraceResult ->
                resolveNoWebPickupHitResult(
                    rayTraceResult,
                    traceFromPlayer(fluid = ClipContext.Fluid.SOURCE_ONLY),
                    pickupPos,
                    pickupCenter,
                )
            },
            onSuccess = {
                pickupTracker.prune(0L, TimedPickupTracker.PickupFilter.WATER)
            },
        )
    }

    private fun markWebPlacementSuccess(webPos: BlockPos) {
        lastSuccessfulWeb = webPos
        lastSuccessfulAt = System.currentTimeMillis()
        trackedWebs.remove(webPos)
    }

    private fun resetState() {
        currentAction = null
        trackedWebs.clear()
        pickupTracker.clear()
    }
}
