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

package net.ccbluex.liquidbounce.utils.entity

import net.ccbluex.liquidbounce.common.EntityFluidInteractionAccess
import net.ccbluex.liquidbounce.common.EntityFluidTrackerAccess
import net.ccbluex.liquidbounce.utils.math.plus
import net.ccbluex.liquidbounce.utils.math.toBlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.tags.TagKey
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.entity.EntityFluidInteraction
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.phys.Vec3

internal fun EntityFluidInteraction.copyForSimulation(): EntityFluidInteraction {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val sourceTrackers = (this as EntityFluidInteractionAccess).trackerByFluid()
    val copy = EntityFluidInteraction(sourceTrackers.keys)
    @Suppress("CAST_NEVER_SUCCEEDS")
    val targetTrackers = (copy as EntityFluidInteractionAccess).trackerByFluid()

    for ((fluid, sourceTracker) in sourceTrackers) {
        val targetTracker = targetTrackers[fluid] ?: continue
        copyTracker(sourceTracker as EntityFluidTrackerAccess, targetTracker as EntityFluidTrackerAccess)
    }
    return copy
}

private fun copyTracker(source: EntityFluidTrackerAccess, target: EntityFluidTrackerAccess) {
    target.height(source.height())
    target.eyesInside(source.eyesInside())
    target.accumulatedCurrent(source.accumulatedCurrent())
    target.currentCount(source.currentCount())
}

internal fun SimulatedPlayer.refreshFluidState() {
    updateFluidInteraction()
    wasUnderwater = fluidInteraction.isEyeInFluid(FluidTags.WATER)
    updateSwimmingState()
}

internal fun SimulatedPlayer.updateFluidInteraction(): Boolean {
    fluidInteraction.update(player, !player.isAffectedByFluids)
    val inWater = fluidInteraction.isInFluid(FluidTags.WATER)
    val inLava = fluidInteraction.isInFluid(FluidTags.LAVA)
    if (inWater) {
        land()
    }

    wasTouchingWater = inWater
    if (player.isAffectedByFluids) {
        applyFluidCurrents(inWater, inLava)
    }
    return inWater || inLava
}

private fun SimulatedPlayer.applyFluidCurrents(inWater: Boolean, inLava: Boolean) {
    if (inWater) {
        fluidInteraction.applyCurrentTo(FluidTags.WATER, player, 0.014)
    }
    if (inLava) {
        val fastLava = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.FAST_LAVA)
        fluidInteraction.applyCurrentTo(FluidTags.LAVA, player, if (fastLava) 0.007 else 0.0023333333333333335)
    }
}

private fun SimulatedPlayer.updateSwimmingState() {
    isSwimming = if (isSwimming) {
        isSprinting && isInWater() && !player.isPassenger
    } else {
        isSprinting && isSubmergedInWater() && !player.isPassenger &&
            player.level().getFluidState(pos.toBlockPos()).`is`(FluidTags.WATER)
    }
}

internal fun SimulatedPlayer.isInWater(): Boolean = wasTouchingWater

internal fun SimulatedPlayer.isInLava(): Boolean = fluidInteraction.isInFluid(FluidTags.LAVA)

internal fun SimulatedPlayer.isSubmergedInWater(): Boolean = wasUnderwater && isInWater()

internal fun SimulatedPlayer.getFluidHeight(tags: TagKey<Fluid>): Double = fluidInteraction.getFluidHeight(tags)

internal fun SimulatedPlayer.getFluidJumpThreshold(): Double = if (player.eyeHeight.toDouble() < 0.4) 0.0 else 0.4

internal fun SimulatedPlayer.swimUpward(fluid: TagKey<Fluid>) {
    val verticalSpeed = if (fluid === FluidTags.WATER) 0.03999999910593033 else 0.005999999865889549
    deltaMovement += Vec3(0.0, verticalSpeed, 0.0)
}
