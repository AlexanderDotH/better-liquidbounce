/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.minecraft.world.entity.Entity

internal abstract class VClipTargetMode(name: String) : Mode(name) {

    abstract fun resolve(
        entity: Entity,
        direction: VClipDirection,
        doNotClipAroundBedrock: Boolean,
    ): VClipPosition?
}

internal object VClipDistanceTarget : VClipTargetMode("Distance") {
    private val blocks by float("Blocks", 5.0F, 0.25F..20.0F, "blocks")

    override fun resolve(
        entity: Entity,
        direction: VClipDirection,
        doNotClipAroundBedrock: Boolean,
    ): VClipPosition? {
        val position = entity.position()
        val targetY = VClipTargetPlanner.distanceTargetY(position.y, direction, blocks.toDouble())
        if (VClipLandingPositionResolver.isBedrockPathBlocked(entity, targetY, doNotClipAroundBedrock)) {
            return null
        }

        return VClipPosition(position.x, targetY, position.z)
    }
}

internal object VClipSmartTarget : VClipTargetMode("Smart") {
    private object ScanDistance : ToggleableValueGroup(this, "ScanDistance", true) {
        val maxDistance by int("MaxDistance", 10, 1..20, "blocks")
    }

    init {
        tree(ScanDistance)
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacyVClipSmartScanDistance(jsonObject)
    }

    override fun resolve(
        entity: Entity,
        direction: VClipDirection,
        doNotClipAroundBedrock: Boolean,
    ) =
        VClipLandingPositionResolver.resolve(
            entity = entity,
            direction = direction,
            maxDistance = ScanDistance.maxDistance.takeIf { ScanDistance.enabled },
            doNotClipAroundBedrock = doNotClipAroundBedrock,
        )
}
