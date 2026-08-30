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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker

internal class NetherBedrockBitPlane private constructor(words: LongArray) {
    private val words = words.copyOf()

    val bedrockCount: Int
        get() = words.sumOf(java.lang.Long::bitCount)

    fun isBedrock(localX: Int, localZ: Int): Boolean {
        require(localX in 0 until SIZE && localZ in 0 until SIZE) { "Bedrock coordinates must be in a 16x16 chunk" }
        val index = localZ * SIZE + localX
        return (words[index ushr WORD_SHIFT] and (1L shl (index and WORD_MASK))) != 0L
    }

    fun toWords(): LongArray = words.copyOf()

    override fun equals(other: Any?): Boolean =
        other is NetherBedrockBitPlane && words.contentEquals(other.words)

    override fun hashCode(): Int = words.contentHashCode()

    override fun toString(): String = "NetherBedrockBitPlane(bedrockCount=$bedrockCount)"

    companion object {
        const val SIZE = 16
        const val CELL_COUNT = SIZE * SIZE
        const val WORD_COUNT = CELL_COUNT / Long.SIZE_BITS
        private const val WORD_SHIFT = 6
        private const val WORD_MASK = Long.SIZE_BITS - 1

        fun fromWords(words: LongArray): NetherBedrockBitPlane {
            require(words.size == WORD_COUNT) { "A bedrock plane must contain $WORD_COUNT words" }
            return NetherBedrockBitPlane(words)
        }

        fun fromPredicate(predicate: (localX: Int, localZ: Int) -> Boolean): NetherBedrockBitPlane {
            val words = LongArray(WORD_COUNT)
            repeat(CELL_COUNT) { index ->
                val x = index and (SIZE - 1)
                val z = index ushr 4
                if (predicate(x, z)) {
                    words[index ushr WORD_SHIFT] = words[index ushr WORD_SHIFT] or
                        (1L shl (index and WORD_MASK))
                }
            }
            return NetherBedrockBitPlane(words)
        }

        fun empty(): NetherBedrockBitPlane = NetherBedrockBitPlane(LongArray(WORD_COUNT))
    }
}

internal data class NetherBedrockChunkObservation(
    override val id: EvidenceId,
    override val scope: CrackScope,
    val chunk: ChunkCoordinate,
    override val revision: Long,
    val floor: NetherBedrockBitPlane,
    val roof: NetherBedrockBitPlane,
    val capturedOrder: Long = 0L,
    override val confidence: EvidenceConfidence = EvidenceConfidence.STRONG,
    override val status: EvidenceStatus = confidence.initialStatus,
) : CrackEvidence {
    init {
        require(scope.isNether) { "Nether bedrock observations require the Nether scope" }
        require(revision >= 0L) { "Observation revision must be non-negative" }
        require(capturedOrder >= 0L) { "Observation capture order must be non-negative" }
    }

    val deduplicationKey: String
        get() = EvidenceId.fromStableParts(
            scope.serverKey,
            scope.dimensionKey,
            scope.generationProfile.storageKey,
            chunk.x.toString(),
            chunk.z.toString(),
        ).value
}
