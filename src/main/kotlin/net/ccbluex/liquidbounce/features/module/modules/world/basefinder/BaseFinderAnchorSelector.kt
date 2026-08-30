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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import kotlin.math.max

internal object BaseFinderAnchorSelector {

    fun select(
        evidence: List<FamilyEvidence>,
        fallbackChunk: ChunkCoordinate,
    ): BaseCoordinate {
        val anchors = evidence.asSequence()
            .filter { it.family.seedCapable }
            .flatMap { it.anchors.asSequence() }
            .toList()
        if (anchors.isEmpty()) {
            return BaseCoordinate(fallbackChunk.x * 16 + 8, DEFAULT_ANCHOR_Y, fallbackChunk.z * 16 + 8)
        }

        val maximumWeight = anchors.maxOf(EvidenceAnchor::weight)
        val tied = anchors.filter { it.weight == maximumWeight }
        if (tied.size == 1) return tied.single().position

        val totalWeight = anchors.sumOf { max(1, it.weight) }.toDouble()
        val centroidX = anchors.sumOf { it.position.x * max(1, it.weight).toDouble() } / totalWeight
        val centroidY = anchors.sumOf { it.position.y * max(1, it.weight).toDouble() } / totalWeight
        val centroidZ = anchors.sumOf { it.position.z * max(1, it.weight).toDouble() } / totalWeight
        return tied.minWith(
            compareBy<EvidenceAnchor> {
                val position = it.position
                square(position.x - centroidX) + square(position.y - centroidY) + square(position.z - centroidZ)
            }.thenBy { it.position.x }.thenBy { it.position.y }.thenBy { it.position.z },
        ).position
    }

    private fun square(value: Double) = value * value

    private const val DEFAULT_ANCHOR_Y = 64
}
