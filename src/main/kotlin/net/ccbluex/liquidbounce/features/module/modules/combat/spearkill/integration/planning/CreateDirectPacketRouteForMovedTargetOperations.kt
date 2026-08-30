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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.DirectPacketRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.calculateSpearKillTravel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Rebuilds and round-trip validates direct Packet movement from the current confirmed position.
 * The validator stays anchored to the original session box so virtual movement never inherits a
 * displaced client-side collision shape.
 */
internal fun SpearKillModuleState.createDirectPacketRouteForMovedTarget(
    target: LivingEntity,
    routeOrigin: Vec3,
    sessionOrigin: Vec3,
): DirectPacketRoutePlan? {
    val rawDistance = routeOrigin.distanceTo(target.position())
    if (rawDistance !in 3.0..maxTargetDistance.toDouble()) return null

    val settings = packetSessionSettings ?: return null
    return calculateDirectPacketRoute(
        target = target,
        routeOrigin = routeOrigin,
        travel = calculateSpearKillTravel(rawDistance),
        settings = settings,
        sessionOrigin = sessionOrigin,
    )
}
