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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackScope
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.NetherBedrockChunkObservation

/** A small, deterministic solver input: two source chunks and a later independent validation chunk. */
internal data class NetherBedrockSolvePlan(
    val sourceObservations: List<NetherBedrockChunkObservation>,
    val heldOutObservation: NetherBedrockChunkObservation?,
    val fingerprint: String,
) {
    val allObservations: List<NetherBedrockChunkObservation>
        get() = sourceObservations + listOfNotNull(heldOutObservation)

    val isReady: Boolean
        get() = sourceObservations.isNotEmpty() && heldOutObservation != null
}

/** Prevents replayed or travelled chunks from growing and restarting the active Nether solve. */
internal object NetherBedrockSolvePlanner {

    const val MAX_RETAINED_CHUNKS = 3

    fun retain(
        scope: CrackScope,
        observations: Collection<NetherBedrockChunkObservation>,
    ): List<NetherBedrockChunkObservation> = observations.asSequence()
        .filter { it.scope == scope && it.isAccepted }
        .groupBy { it.chunk }
        .values
        .map { duplicates -> duplicates.maxWith(compareBy(NetherBedrockChunkObservation::revision)) }
        .sortedWith(
            compareBy<NetherBedrockChunkObservation>(NetherBedrockChunkObservation::capturedOrder)
                .thenBy { it.chunk.x }
                .thenBy { it.chunk.z }
                .thenBy(NetherBedrockChunkObservation::deduplicationKey),
        )
        .take(MAX_RETAINED_CHUNKS)
        .toList()

    fun plan(
        scope: CrackScope,
        observations: Collection<NetherBedrockChunkObservation>,
    ): NetherBedrockSolvePlan {
        val retained = retain(scope, observations)
        val heldOut = retained.lastOrNull()?.takeIf { retained.size >= MINIMUM_PLAN_CHUNKS }
        val source = if (heldOut == null) emptyList() else retained.dropLast(1)
        return NetherBedrockSolvePlan(
            sourceObservations = source,
            heldOutObservation = heldOut,
            fingerprint = retained.joinToString(separator = "|") { observation ->
                "${observation.deduplicationKey}:${observation.revision}:" +
                    "${observation.floor.hashCode()}:${observation.roof.hashCode()}"
            },
        )
    }

    private const val MINIMUM_PLAN_CHUNKS = 2
}
