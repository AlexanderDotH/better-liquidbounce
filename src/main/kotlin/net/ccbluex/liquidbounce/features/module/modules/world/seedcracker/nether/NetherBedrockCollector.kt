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
 */
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.ChunkCoordinate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackScope
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceId
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.NetherBedrockBitPlane
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.NetherBedrockChunkObservation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** A primitive snapshot supplied by the tracker after it has read a complete loaded chunk on its own worker. */
internal data class NetherBedrockChunkSnapshot(
    val scope: CrackScope,
    val chunk: ChunkCoordinate,
    val revision: Long,
    val floor: NetherBedrockBitPlane,
    val roof: NetherBedrockBitPlane,
) {
    init {
        require(revision >= 0L) { "Chunk revision must be non-negative" }
    }
}

/** The collection result tells the tracker whether solver input changed without exposing mutable storage. */
internal data class NetherBedrockCollectionChange(
    val observation: NetherBedrockChunkObservation,
    val changed: Boolean,
)

/**
 * Thread-safe, pure evidence storage for full Nether bedrock layers.
 *
 * The tracker owns Minecraft access and creates [NetherBedrockChunkSnapshot] values. This collector only retains
 * immutable primitive snapshots, rejects stale or same-revision contradictory updates, and never retains chunks,
 * block states, or block positions.
 */
internal class NetherBedrockCollector {

    private val byScopeAndChunk = ConcurrentHashMap<ScopeChunkKey, NetherBedrockChunkObservation>()
    private val captureOrder = AtomicLong()

    fun record(snapshot: NetherBedrockChunkSnapshot): NetherBedrockCollectionChange {
        var result: NetherBedrockCollectionChange? = null
        val key = ScopeChunkKey(snapshot.scope, snapshot.chunk)

        byScopeAndChunk.compute(key) { _, current ->
            val next = decide(current, snapshot)
            result = next
            next.observation
        }

        return checkNotNull(result)
    }

    fun observation(scope: CrackScope, chunk: ChunkCoordinate): NetherBedrockChunkObservation? =
        byScopeAndChunk[ScopeChunkKey(scope, chunk)]

    fun observations(scope: CrackScope): List<NetherBedrockChunkObservation> = byScopeAndChunk
        .asSequence()
        .filter { it.key.scope == scope }
        .map { it.value }
        .sortedWith(
            compareBy<NetherBedrockChunkObservation> { it.chunk.x }.thenBy { it.chunk.z },
        )
        .toList()

    /** Restores immutable persisted evidence without rewriting its capture ordering. */
    fun restore(observations: Collection<NetherBedrockChunkObservation>) {
        observations.forEach { observation ->
            val key = ScopeChunkKey(observation.scope, observation.chunk)
            byScopeAndChunk.compute(key) { _, current ->
                when {
                    current == null || current.revision <= observation.revision -> observation
                    else -> current
                }
            }
            captureOrder.updateAndGet { current -> maxOf(current, observation.capturedOrder) }
        }
    }

    fun remove(scope: CrackScope, chunk: ChunkCoordinate): Boolean =
        byScopeAndChunk.remove(ScopeChunkKey(scope, chunk)) != null

    fun clear() {
        byScopeAndChunk.clear()
        captureOrder.set(0L)
    }

    private fun decide(
        current: NetherBedrockChunkObservation?,
        snapshot: NetherBedrockChunkSnapshot,
    ): NetherBedrockCollectionChange {
        if (current == null) {
            return NetherBedrockCollectionChange(snapshot.toObservation(nextCaptureOrder()), changed = true)
        }
        if (snapshot.revision < current.revision) return NetherBedrockCollectionChange(current, changed = false)
        if (snapshot.revision == current.revision) return NetherBedrockCollectionChange(current, changed = false)
        if (snapshot.floor == current.floor && snapshot.roof == current.roof) {
            return NetherBedrockCollectionChange(snapshot.toObservation(current.capturedOrder), changed = false)
        }
        return NetherBedrockCollectionChange(snapshot.toObservation(nextCaptureOrder()), changed = true)
    }

    private fun nextCaptureOrder(): Long = captureOrder.incrementAndGet()

    private fun NetherBedrockChunkSnapshot.toObservation(capturedOrder: Long) = NetherBedrockChunkObservation(
        id = EvidenceId(
            "nether-bedrock:${scope.dimensionKey}:${scope.generationProfile.name}:${chunk.x}:${chunk.z}:$revision",
        ),
        scope = scope,
        chunk = chunk,
        revision = revision,
        floor = floor,
        roof = roof,
        capturedOrder = capturedOrder,
    )

    private data class ScopeChunkKey(
        val scope: CrackScope,
        val chunk: ChunkCoordinate,
    )
}
