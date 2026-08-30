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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.minecraft.world.phys.Vec3

internal fun requiredMaceKillLocalRestore(
    packetRouteOwned: Boolean,
    preservePhysicalMovement: Boolean,
    origin: Vec3?,
    currentPosition: Vec3,
): Vec3? = origin?.takeIf {
    packetRouteOwned && !preservePhysicalMovement &&
        currentPosition.distanceToSqr(it) >= MACE_KILL_LOCAL_POSITION_EPSILON_SQUARED
}

/** Keeps route displacement server-side while ordinary local movement remains under player control. */
internal fun maceKillPhysicalMovementVirtualOffset(
    routeOwned: Boolean,
    packetMovement: Boolean,
    researchActive: Boolean,
    committedOffset: Vec3,
): Vec3? = committedOffset.takeIf { routeOwned && packetMovement && !researchActive }

private const val MACE_KILL_LOCAL_POSITION_EPSILON_SQUARED = 1.0E-8
