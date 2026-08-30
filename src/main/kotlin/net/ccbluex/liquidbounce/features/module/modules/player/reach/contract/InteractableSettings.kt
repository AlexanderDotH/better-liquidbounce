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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.contract

import net.ccbluex.liquidbounce.utils.collection.Filter
import net.minecraft.world.level.block.Block

internal data class InteractableBlockFilter(val mode: Filter, val blocks: Set<Block>) {
    operator fun contains(block: Block): Boolean = mode(block, blocks)
}

internal data class InteractableSettingsSnapshot(
    val maxRange: Double,
    val interactionRange: Double,
    val filter: InteractableBlockFilter,
    val containerVehicles: Boolean,
    val routing: InteractableRoutingSettings,
    val surfaceFallback: InteractableSurfaceFallbackSettings,
    val openRetries: Int,
    val openTimeoutTicks: Int,
    val routeTimeoutTicks: Int,
    val holdTimeoutTicks: Int,
)

internal data class InteractableRoutingSettings(
    val maxCost: Int,
    val diagonal: Boolean,
    val lineOfSightShortcuts: Boolean,
    val stepDistance: Double,
    val stepDelayTicks: Int,
    val nodesPerTick: Int,
    val renderPath: Boolean,
)

internal data class InteractableSurfaceFallbackSettings(
    val enabled: Boolean,
    val maxRise: Int,
    val horizontalSearch: Int,
    val maxClipDistance: Int,
    val doNotClipAroundBedrock: Boolean,
    val transport: InteractableVClipSettings,
)

internal sealed interface InteractableVClipSettings {
    data class Vanilla(val paperBypass: Boolean, val fullPacket: Boolean) : InteractableVClipSettings
    data class Folia(val movementPackets: Int, val fullPacket: Boolean) : InteractableVClipSettings
}
