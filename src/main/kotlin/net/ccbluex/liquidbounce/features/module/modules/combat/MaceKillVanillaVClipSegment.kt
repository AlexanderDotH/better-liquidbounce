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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** A single vanilla position-packet clip, deliberately capped to five vertical blocks. */
internal data class MaceKillVanillaVClipSegment(
    val from: Vec3,
    val to: Vec3,
) {
    val movement: Vec3 = to.subtract(from)

    init {
        require(from.hasFiniteMaceKillVanillaVClipCoordinates()) { "Vanilla VClip source must be finite" }
        require(to.hasFiniteMaceKillVanillaVClipCoordinates()) { "Vanilla VClip destination must be finite" }
        require(abs(movement.x) <= MACE_KILL_VANILLA_VCLIP_EPSILON &&
            abs(movement.z) <= MACE_KILL_VANILLA_VCLIP_EPSILON
        ) { "Vanilla VClip must be vertical" }
        val verticalDistance = abs(movement.y)
        require(
            verticalDistance >= MACE_KILL_VANILLA_VCLIP_EPSILON &&
                verticalDistance <= MACE_KILL_MAX_VANILLA_VCLIP_DISTANCE + MACE_KILL_VANILLA_VCLIP_EPSILON,
        ) {
            "Vanilla VClip distance must stay within the 0..5 block limit"
        }
    }

    /** The exact inverse return is part of the same bounded VClip contract. */
    fun matches(candidateFrom: Vec3, candidateTo: Vec3): Boolean =
        matchesDirection(from, to, candidateFrom, candidateTo) ||
            matchesDirection(to, from, candidateFrom, candidateTo)
}

/** Tries the nearer legal height first, starting in the target's vertical direction. */
internal fun maceKillVanillaVClipCandidates(origin: Vec3, endpoint: Vec3): List<Vec3> {
    require(origin.hasFiniteMaceKillVanillaVClipCoordinates()) { "Vanilla VClip origin must be finite" }
    require(endpoint.hasFiniteMaceKillVanillaVClipCoordinates()) { "Vanilla VClip endpoint must be finite" }

    val preferredDirection = if (endpoint.y >= origin.y) 1.0 else -1.0
    return buildList(MACE_KILL_MAX_VANILLA_VCLIP_DISTANCE.toInt() * 2) {
        listOf(preferredDirection, -preferredDirection).forEach { direction ->
            for (distance in 1..MACE_KILL_MAX_VANILLA_VCLIP_DISTANCE.toInt()) {
                add(Vec3(0.0, direction * distance, 0.0))
            }
        }
    }
}

private fun matchesDirection(
    expectedFrom: Vec3,
    expectedTo: Vec3,
    actualFrom: Vec3,
    actualTo: Vec3,
): Boolean = expectedFrom.distanceToSqr(actualFrom) <= MACE_KILL_VANILLA_VCLIP_MATCH_EPSILON_SQUARED &&
    expectedTo.distanceToSqr(actualTo) <= MACE_KILL_VANILLA_VCLIP_MATCH_EPSILON_SQUARED

private fun Vec3.hasFiniteMaceKillVanillaVClipCoordinates(): Boolean =
    x.isFinite() && y.isFinite() && z.isFinite()

internal const val MACE_KILL_MAX_VANILLA_VCLIP_DISTANCE = 5.0

private const val MACE_KILL_VANILLA_VCLIP_EPSILON = 1.0E-9
private const val MACE_KILL_VANILLA_VCLIP_MATCH_EPSILON_SQUARED = 1.0E-12
