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

@file:Suppress("MatchingDeclarationName")

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target

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

import java.util.IdentityHashMap

internal data class MaceKillCombatTargetCandidate<T : Any>(
    val target: T,
    val distance: Double,
    val crosshairAngle: Float,
)

/**
 * Chooses the nearest candidate that has a usable mace attack position.
 *
 * A small distance hysteresis keeps the preview from jumping between moving entities every tick.
 * Crosshair angle only breaks distance ties; Combat mode must not prefer a perfectly aligned entity
 * hundreds of blocks away over a nearby target.
 */
internal fun <T : Any> selectMaceKillCombatTarget(
    candidates: Iterable<MaceKillCombatTargetCandidate<T>>,
    retainedTarget: T?,
    hasAttackEndpoint: (T) -> Boolean,
): T? {
    val ordered = candidates.asSequence()
        .filter { candidate ->
            candidate.distance.isFinite() && candidate.distance >= 0.0 &&
                candidate.crosshairAngle.isFinite() && candidate.crosshairAngle >= 0f
        }
        .sortedWith(
            compareBy<MaceKillCombatTargetCandidate<T>> { it.distance }
                .thenBy { it.crosshairAngle },
        )
        .toList()
    if (ordered.isEmpty()) return null

    val endpointAvailability = IdentityHashMap<T, Boolean>()
    fun hasEndpoint(candidate: MaceKillCombatTargetCandidate<T>): Boolean =
        endpointAvailability.getOrPut(candidate.target) { hasAttackEndpoint(candidate.target) }

    val best = ordered.firstOrNull(::hasEndpoint) ?: return null
    val retained = retainedTarget?.let { target -> ordered.firstOrNull { it.target === target } }
    return retained
        ?.takeIf { it.distance <= best.distance + MACE_KILL_TARGET_DISTANCE_HYSTERESIS && hasEndpoint(it) }
        ?.target
        ?: best.target
}

private const val MACE_KILL_TARGET_DISTANCE_HYSTERESIS = 2.0
