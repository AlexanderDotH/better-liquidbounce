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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
@file:Suppress(
    "BracesOnIfStatements",
    "ClassNaming",
    "CognitiveComplexMethod",
    "LongMethod",
    "MaxLineLength",
    "ReturnCount",
)

package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackScope
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.NetherBedrockChunkObservation
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln

/**
 * The two useful Nether bedrock planes. The pattern seeds for floor and roof are deliberately kept separate:
 * vanilla derives each of them through a different dimension salt before placing bedrock.
 */
internal enum class NetherBedrockLayer(val blockY: Int) {
    FLOOR(4),
    ROOF(123),
}

/** A primitive, immutable bedrock predicate. [isBedrock] is a constraint, not merely a positive observation. */
internal data class NetherBedrockCellConstraint(
    val x: Int,
    val y: Int,
    val z: Int,
    val isBedrock: Boolean,
) {
    init {
        require(y == NetherBedrockLayer.FLOOR.blockY || y == NetherBedrockLayer.ROOF.blockY) {
            "Only the informative Nether bedrock layers Y=4 and Y=123 are supported"
        }
    }

    val layer: NetherBedrockLayer
        get() = if (y == NetherBedrockLayer.FLOOR.blockY) NetherBedrockLayer.FLOOR else NetherBedrockLayer.ROOF
}

/**
 * A solver-owned copy of a fully observed chunk. It intentionally contains no Minecraft state and defensively
 * copies both bit planes, so a scanner worker cannot mutate a running solver snapshot.
 */
internal class NetherBedrockSolverChunk private constructor(
    val chunkX: Int,
    val chunkZ: Int,
    private val floorWords: LongArray,
    private val roofWords: LongArray,
) {

    fun isBedrock(layer: NetherBedrockLayer, localX: Int, localZ: Int): Boolean {
        require(localX in 0 until SIDE_LENGTH) { "Local x must be in 0..15" }
        require(localZ in 0 until SIDE_LENGTH) { "Local z must be in 0..15" }
        val index = localZ * SIDE_LENGTH + localX
        val words = if (layer == NetherBedrockLayer.FLOOR) floorWords else roofWords
        return words[index ushr WORD_SHIFT] and (1L shl (index and WORD_MASK)) != 0L
    }

    fun constraints(layer: NetherBedrockLayer): List<NetherBedrockCellConstraint> = buildList(CELL_COUNT) {
        repeat(SIDE_LENGTH) { localZ ->
            repeat(SIDE_LENGTH) { localX ->
                add(
                    NetherBedrockCellConstraint(
                        x = chunkX * SIDE_LENGTH + localX,
                        y = layer.blockY,
                        z = chunkZ * SIDE_LENGTH + localZ,
                        isBedrock = isBedrock(layer, localX, localZ),
                    ),
                )
            }
        }
    }

    companion object {
        private const val SIDE_LENGTH = 16
        private const val CELL_COUNT = SIDE_LENGTH * SIDE_LENGTH
        private const val WORD_SHIFT = 6
        private const val WORD_MASK = Long.SIZE_BITS - 1
        private const val WORD_COUNT = CELL_COUNT / Long.SIZE_BITS

        fun fromWords(
            chunkX: Int,
            chunkZ: Int,
            floor: LongArray,
            roof: LongArray,
        ): NetherBedrockSolverChunk {
            require(floor.size == WORD_COUNT) { "Floor must contain $WORD_COUNT words" }
            require(roof.size == WORD_COUNT) { "Roof must contain $WORD_COUNT words" }
            return NetherBedrockSolverChunk(chunkX, chunkZ, floor.copyOf(), roof.copyOf())
        }

        fun fromPredicate(
            chunkX: Int,
            chunkZ: Int,
            floor: (localX: Int, localZ: Int) -> Boolean,
            roof: (localX: Int, localZ: Int) -> Boolean,
        ): NetherBedrockSolverChunk {
            val floorWords = LongArray(WORD_COUNT)
            val roofWords = LongArray(WORD_COUNT)
            repeat(SIDE_LENGTH) { z ->
                repeat(SIDE_LENGTH) { x ->
                    val index = z * SIDE_LENGTH + x
                    if (floor(x, z)) {
                        floorWords[index ushr WORD_SHIFT] = floorWords[index ushr WORD_SHIFT] or
                            (1L shl (index and WORD_MASK))
                    }
                    if (roof(x, z)) {
                        roofWords[index ushr WORD_SHIFT] = roofWords[index ushr WORD_SHIFT] or
                            (1L shl (index and WORD_MASK))
                    }
                }
            }
            return NetherBedrockSolverChunk(chunkX, chunkZ, floorWords, roofWords)
        }
    }
}

/** A resumable slice of the 48-bit bedrock-seed search space, split on its upper 36 bits. */
internal data class NetherBedrockPrefixRange(
    val startInclusive: Long,
    val endExclusive: Long,
) {
    init {
        require(startInclusive in 0..TOTAL_PREFIXES) { "Start prefix is outside the 36-bit search space" }
        require(endExclusive in startInclusive..TOTAL_PREFIXES) { "End prefix is outside the 36-bit search space" }
    }

    val prefixCount: Long
        get() = endExclusive - startInclusive

    fun next(windowSize: Long): NetherBedrockPrefixRange? {
        require(windowSize > 0L) { "Window size must be positive" }
        if (endExclusive == TOTAL_PREFIXES) return null
        return NetherBedrockPrefixRange(endExclusive, minOf(TOTAL_PREFIXES, endExclusive + windowSize))
    }

    companion object {
        const val LOWER_BITS = 12
        const val PREFIX_BITS = 48 - LOWER_BITS
        const val TOTAL_PREFIXES = 1L shl PREFIX_BITS

        fun initial(windowSize: Long = DEFAULT_PREFIX_WINDOW): NetherBedrockPrefixRange =
            NetherBedrockPrefixRange(0L, minOf(TOTAL_PREFIXES, windowSize))

        const val DEFAULT_PREFIX_WINDOW = 1L shl 20
    }
}

/** Input to a bounded single-layer search. Held-out cells are never used to generate candidates. */
internal data class NetherBedrockLayerSearchRequest(
    val layer: NetherBedrockLayer,
    val constraints: List<NetherBedrockCellConstraint>,
    val heldOutConstraints: List<NetherBedrockCellConstraint> = emptyList(),
    val range: NetherBedrockPrefixRange = NetherBedrockPrefixRange.initial(),
    val candidateLimit: Int = DEFAULT_CANDIDATE_LIMIT,
) {
    init {
        require(candidateLimit > 0) { "Candidate limit must be positive" }
        require(constraints.all { it.layer == layer }) { "All search constraints must use the requested layer" }
        require(heldOutConstraints.all { it.layer == layer }) { "All held-out constraints must use the requested layer" }
    }

    companion object {
        const val DEFAULT_CANDIDATE_LIMIT = 1_024
    }
}

internal enum class NetherBedrockNeed {
    NEED_SECOND_COMPLETE_CHUNK,
    CANDIDATE_BUDGET_TOO_HIGH,
}

/** A gate evaluated before expensive work is scheduled. */
internal sealed interface NetherBedrockStartGate {
    data class NeedsMoreInformation(
        val reason: NetherBedrockNeed,
        val distinctChunkCount: Int,
        val floorEstimatedCandidates: Long,
        val roofEstimatedCandidates: Long,
    ) : NetherBedrockStartGate

    data class Ready(
        val floorEstimatedCandidates: Long,
        val roofEstimatedCandidates: Long,
    ) : NetherBedrockStartGate
}

internal enum class NetherBedrockVerification {
    UNVERIFIED,
    HELD_OUT_VALIDATED,
}

/** A 48-bit pattern seed, not a claimed 64-bit world seed. */
internal data class NetherBedrockPatternSeedCandidate(
    val layer: NetherBedrockLayer,
    val patternSeed: Long,
    val verification: NetherBedrockVerification,
) {
    init {
        require(patternSeed in 0..JAVA_MASK) { "Pattern seed must be a 48-bit value" }
    }
}

/**
 * The bedrock placement seed is only 48 bits. Reconstructing all 64-bit world-seed candidates from its truncated
 * Java `nextLong` output is a separate inverse problem, so this seam makes that dependency explicit rather than
 * pretending a pattern seed is a world seed.
 */
internal fun interface NetherBedrockWorldSeedInverter {
    fun invert(
        patternSeed: Long,
        layer: NetherBedrockLayer,
        isCancelled: () -> Boolean,
    ): NetherBedrockWorldSeedInversion
}

internal sealed interface NetherBedrockWorldSeedInversion {
    /** No profile-pinned inverse implementation is bound; callers must not emit a world-seed result. */
    data object Unavailable : NetherBedrockWorldSeedInversion

    data object Cancelled : NetherBedrockWorldSeedInversion

    /** [complete] is true only when the implementation exhausted its mathematically complete inverse domain. */
    data class Candidates(
        val seeds: List<Long>,
        val complete: Boolean,
    ) : NetherBedrockWorldSeedInversion
}

internal object UnavailableNetherBedrockWorldSeedInverter : NetherBedrockWorldSeedInverter {
    override fun invert(
        patternSeed: Long,
        layer: NetherBedrockLayer,
        isCancelled: () -> Boolean,
    ): NetherBedrockWorldSeedInversion = if (isCancelled()) {
        NetherBedrockWorldSeedInversion.Cancelled
    } else {
        NetherBedrockWorldSeedInversion.Unavailable
    }
}

/** Results from a bounded range; callers continue with [nextRange] instead of blocking the client. */
internal sealed interface NetherBedrockLayerSearchOutcome {
    data class Progress(
        val layer: NetherBedrockLayer,
        val candidates: List<NetherBedrockPatternSeedCandidate>,
        val nextRange: NetherBedrockPrefixRange?,
        val checkedPrefixes: Long,
    ) : NetherBedrockLayerSearchOutcome

    data class CandidateBudgetExceeded(
        val layer: NetherBedrockLayer,
        val candidateLimit: Int,
        val nextRange: NetherBedrockPrefixRange?,
        val checkedPrefixes: Long,
    ) : NetherBedrockLayerSearchOutcome

    data class Contradicted(
        val layer: NetherBedrockLayer,
        val checkedPrefixes: Long,
    ) : NetherBedrockLayerSearchOutcome

    data class Cancelled(
        val layer: NetherBedrockLayer,
        val nextRange: NetherBedrockPrefixRange,
        val checkedPrefixes: Long,
    ) : NetherBedrockLayerSearchOutcome
}

/**
 * A bounded two-plane search input. [sourceChunks] produce candidates; [heldOutChunks] are only used afterwards to
 * validate them, which keeps the independently observed verification contract explicit.
 */
internal data class NetherBedrockWorldSeedSearchRequest(
    val sourceChunks: List<NetherBedrockSolverChunk>,
    val heldOutChunks: List<NetherBedrockSolverChunk> = emptyList(),
    val range: NetherBedrockPrefixRange = NetherBedrockPrefixRange.initial(),
    val candidateLimit: Int = NetherBedrockLayerSearchRequest.DEFAULT_CANDIDATE_LIMIT,
) {
    init {
        require(sourceChunks.isNotEmpty()) { "A Nether bedrock search needs at least one source chunk" }
        require(candidateLimit > 0) { "Candidate limit must be positive" }
    }
}

/** A signed 64-bit world seed recovered from both Java-Nether bedrock planes. */
internal data class NetherBedrockWorldSeedCandidate(
    val seed: Long,
    val primaryLayer: NetherBedrockLayer,
    val primaryPatternSeed: Long,
    val verification: NetherBedrockVerification,
)

/** Results from a bounded full-world search; callers schedule [nextRange] in a later background slice. */
internal sealed interface NetherBedrockWorldSeedSearchOutcome {
    data class Progress(
        val candidates: List<NetherBedrockWorldSeedCandidate>,
        val nextRange: NetherBedrockPrefixRange?,
        val checkedPrefixes: Long,
    ) : NetherBedrockWorldSeedSearchOutcome

    data class CandidateBudgetExceeded(
        val candidateLimit: Int,
        val nextRange: NetherBedrockPrefixRange?,
        val checkedPrefixes: Long,
    ) : NetherBedrockWorldSeedSearchOutcome

    data class Contradicted(
        val checkedPrefixes: Long,
    ) : NetherBedrockWorldSeedSearchOutcome

    data class Cancelled(
        val nextRange: NetherBedrockPrefixRange,
        val checkedPrefixes: Long,
    ) : NetherBedrockWorldSeedSearchOutcome
}

/**
 * Isolated adapter for the seed-dependent Nether bedrock rule. The default implementation is deliberately pure:
 * it does not touch a world, a packet, or native code. Future profiles must provide their own adapter and golden
 * corpus rather than silently reusing this one.
 */
internal interface NetherBedrockRuleAdapter {
    val profileName: String

    fun matches(patternSeed: Long, constraint: NetherBedrockCellConstraint): Boolean

    fun estimatedSuccessProbability(constraint: NetherBedrockCellConstraint): Double
}

/**
 * Java 26.2-compatible reference rule for the 1.18+ positional Java-LCG Nether bedrock path.
 *
 * The implementation is intentionally factored behind [NetherBedrockRuleAdapter]. It must remain profile-gated by
 * the caller; network protocol versions are not evidence of a server's generation profile.
 */
internal object NetherBedrockJava26_2Rule : NetherBedrockRuleAdapter {

    override val profileName: String = "java_26_2"

    override fun matches(patternSeed: Long, constraint: NetherBedrockCellConstraint): Boolean {
        require(patternSeed in 0..JAVA_MASK) { "Pattern seed must be a 48-bit value" }
        val bounds = bounds(constraint)
        val random = ((patternSeed xor positionHashWithJavaSeed(constraint.x, constraint.y, constraint.z)) *
            JAVA_MULTIPLIER) and JAVA_MASK
        return random in bounds.lowerInclusive until bounds.upperExclusive
    }

    override fun estimatedSuccessProbability(constraint: NetherBedrockCellConstraint): Double {
        val bounds = bounds(constraint)
        return (bounds.upperExclusive - bounds.lowerInclusive).toDouble() / JAVA_MASK.toDouble()
    }

    /**
     * Exact Java `Random.nextLong` path used by vanilla Nether bedrock in the 26.2 profile.
     *
     * The intermediate value is deliberately a signed Java long. Its low 48 bits are all that influence the next
     * Java-Random seed, but preserving the signed result is required when reconstructing a full world seed.
     */
    fun patternSeedFromWorldSeed(worldSeed: Long, layer: NetherBedrockLayer): Long {
        val commonBedrockSeed = nextLong(worldSeed)
        val salt = layerSalt(layer)
        return nextLong(commonBedrockSeed xor salt) and JAVA_MASK
    }

    internal fun layerSalt(layer: NetherBedrockLayer): Long =
        if (layer == NetherBedrockLayer.FLOOR) FLOOR_SALT else ROOF_SALT

    internal fun interval(constraint: NetherBedrockCellConstraint): LongRange {
        val bounds = bounds(constraint)
        return bounds.lowerInclusive..(bounds.upperExclusive - 1L)
    }

    internal fun bounds(constraint: NetherBedrockCellConstraint): NetherBedrockBounds {
        val threshold = when (constraint.layer) {
            NetherBedrockLayer.FLOOR -> ((5 - constraint.y).toDouble() / 5.0 * JAVA_MASK).toLong()
            NetherBedrockLayer.ROOF -> ((127 - constraint.y).toDouble() / 5.0 * JAVA_MASK).toLong()
        }
        require(threshold in 1 until JAVA_MASK) { "Only informative bedrock layers may be used" }

        return when {
            constraint.layer == NetherBedrockLayer.FLOOR && constraint.isBedrock -> NetherBedrockBounds(0L, threshold)
            constraint.layer == NetherBedrockLayer.FLOOR -> NetherBedrockBounds(threshold, JAVA_MASK)
            constraint.isBedrock -> NetherBedrockBounds(threshold, JAVA_MASK)
            else -> NetherBedrockBounds(0L, threshold)
        }
    }

    internal fun positionHashWithJavaSeed(x: Int, y: Int, z: Int): Long =
        (positionHash(x, y, z) xor JAVA_MULTIPLIER) and JAVA_MASK

    private fun positionHash(x: Int, y: Int, z: Int): Long {
        val xTerm = (x * 3_129_871).toLong()
        val zTerm = z.toLong() * 116_129_781L
        val base = xTerm xor zTerm xor y.toLong()
        return ((base * base * 42_317_861L) + (base * 11L)) ushr 16
    }

    internal fun nextLong(seed: Long): Long {
        val initialState = (seed xor JAVA_MULTIPLIER) and JAVA_MASK
        val firstState = ((initialState * JAVA_MULTIPLIER) + JAVA_ADDEND) and JAVA_MASK
        val secondState = ((firstState * JAVA_MULTIPLIER) + JAVA_ADDEND) and JAVA_MASK
        return ((firstState ushr 16) shl 32) + (secondState ushr 16).toInt().toLong()
    }

    private const val FLOOR_SALT = 2_042_456_806L
    private const val ROOF_SALT = 343_340_730L
}

/** The exclusive Java-Random interval belonging to one complete positive or negative bedrock cell constraint. */
internal data class NetherBedrockBounds(
    val lowerInclusive: Long,
    val upperExclusive: Long,
) {
    init {
        require(lowerInclusive in 0 until JAVA_MASK) { "Bedrock bound must fit the Java 48-bit domain" }
        require(upperExclusive in 1..JAVA_MASK) { "Bedrock bound must fit the Java 48-bit domain" }
        require(lowerInclusive < upperExclusive) { "Bedrock interval must not be empty" }
    }
}

/**
 * Pure Kotlin, bounded 48-bit constraint search. Search ranges contain 36-bit prefixes; a filter tree explores the
 * remaining 12 bits only after cheap interval checks. This mirrors the Java-LCG constraint shape without embedding
 * or launching a native cracker.
 */
internal object NetherBedrockConstraintSolver {

    fun fromAcceptedObservations(
        scope: CrackScope,
        observations: List<NetherBedrockChunkObservation>,
    ): List<NetherBedrockSolverChunk> {
        require(scope.isNether) { "Nether bedrock constraints require a Nether scope" }
        require(observations.all { it.scope == scope }) { "Bedrock observations from another scope must not be merged" }
        return observations.asSequence().filter(NetherBedrockChunkObservation::isAccepted).map { observation ->
            NetherBedrockSolverChunk.fromPredicate(
                chunkX = observation.chunk.x,
                chunkZ = observation.chunk.z,
                floor = observation.floor::isBedrock,
                roof = observation.roof::isBedrock,
            )
        }.toList()
    }

    /**
     * Enforces the UX start gate before the tracker schedules a range. Both planes must originate from two distinct
     * complete chunks; this prevents a placed/edited single chunk from triggering a huge search.
     */
    fun startGate(
        chunks: List<NetherBedrockSolverChunk>,
        candidateLimit: Int = NetherBedrockLayerSearchRequest.DEFAULT_CANDIDATE_LIMIT,
        rule: NetherBedrockRuleAdapter = NetherBedrockJava26_2Rule,
    ): NetherBedrockStartGate {
        require(candidateLimit > 0) { "Candidate limit must be positive" }
        val unique = chunks.distinctBy { it.chunkX to it.chunkZ }
        val floorEstimate = estimateCandidates(unique.flatMap { it.constraints(NetherBedrockLayer.FLOOR) }, rule)
        val roofEstimate = estimateCandidates(unique.flatMap { it.constraints(NetherBedrockLayer.ROOF) }, rule)
        if (unique.size < MINIMUM_COMPLETE_CHUNKS) {
            return NetherBedrockStartGate.NeedsMoreInformation(
                NetherBedrockNeed.NEED_SECOND_COMPLETE_CHUNK,
                unique.size,
                floorEstimate,
                roofEstimate,
            )
        }
        if (floorEstimate > candidateLimit && roofEstimate > candidateLimit) {
            return NetherBedrockStartGate.NeedsMoreInformation(
                NetherBedrockNeed.CANDIDATE_BUDGET_TOO_HIGH,
                unique.size,
                floorEstimate,
                roofEstimate,
            )
        }
        return NetherBedrockStartGate.Ready(floorEstimate, roofEstimate)
    }

    /**
     * Searches exactly [request.range] and returns a continuation instead of continuing indefinitely. [isCancelled]
     * is polled for every filter-tree branch, allowing the tracker to abort promptly on a new world epoch/revision.
     */
    fun search(
        request: NetherBedrockLayerSearchRequest,
        rule: NetherBedrockRuleAdapter = NetherBedrockJava26_2Rule,
        isCancelled: () -> Boolean = { false },
    ): NetherBedrockLayerSearchOutcome {
        if (isCancelled()) {
            return NetherBedrockLayerSearchOutcome.Cancelled(request.layer, request.range, checkedPrefixes = 0L)
        }

        val blocks = request.constraints.map(::FilterBlock)
        if (blocks.isEmpty()) {
            return NetherBedrockLayerSearchOutcome.Progress(
                request.layer,
                candidates = emptyList(),
                nextRange = request.range.next(request.range.prefixCount.coerceAtLeast(1L)),
                checkedPrefixes = 0L,
            )
        }

        val tree = FilterTree.create(blocks)
        val accumulator = CandidateAccumulator(request.candidateLimit, request.layer, request.heldOutConstraints, rule, isCancelled)
        var prefix = request.range.startInclusive
        while (prefix < request.range.endExclusive) {
            if (isCancelled()) {
                return NetherBedrockLayerSearchOutcome.Cancelled(
                    request.layer,
                    NetherBedrockPrefixRange(prefix, request.range.endExclusive),
                    checkedPrefixes = prefix - request.range.startInclusive,
                )
            }
            if (!tree.visit(prefix shl NetherBedrockPrefixRange.LOWER_BITS, accumulator::accept)) break
            prefix++
        }

        val checked = prefix - request.range.startInclusive
        val nextRange = when {
            accumulator.cancelled -> NetherBedrockPrefixRange(prefix, request.range.endExclusive)
            prefix < request.range.endExclusive -> NetherBedrockPrefixRange(prefix, request.range.endExclusive)
            else -> request.range.next(request.range.prefixCount.coerceAtLeast(1L))
        }
        if (accumulator.cancelled) {
            return NetherBedrockLayerSearchOutcome.Cancelled(
                request.layer,
                checkNotNull(nextRange),
                checked,
            )
        }
        if (accumulator.candidateLimitExceeded) {
            return NetherBedrockLayerSearchOutcome.CandidateBudgetExceeded(
                request.layer,
                request.candidateLimit,
                nextRange,
                checked,
            )
        }
        if (nextRange == null && accumulator.candidates.isEmpty()) {
            return NetherBedrockLayerSearchOutcome.Contradicted(request.layer, checked)
        }
        return NetherBedrockLayerSearchOutcome.Progress(
            request.layer,
            accumulator.candidates.toList(),
            nextRange,
            checked,
        )
    }

    fun validate(
        patternSeed: Long,
        heldOutConstraints: List<NetherBedrockCellConstraint>,
        rule: NetherBedrockRuleAdapter = NetherBedrockJava26_2Rule,
    ): Boolean = heldOutConstraints.all { rule.matches(patternSeed, it) }

    /**
     * Searches both Nether bedrock planes and emits only complete signed world-seed candidates.
     *
     * This is a Kotlin port of the positional Java-LCG filter-tree shape: one plane prunes 48-bit pattern seeds,
     * the other plane cross-checks the recovered common seed, and two inverse `nextLong` steps reconstruct the
     * signed world seed. The range is intentionally bounded to retain responsive cancellation and revision safety.
     */
    fun searchWorldSeeds(
        request: NetherBedrockWorldSeedSearchRequest,
        isCancelled: () -> Boolean = { false },
    ): NetherBedrockWorldSeedSearchOutcome {
        if (isCancelled()) {
            return NetherBedrockWorldSeedSearchOutcome.Cancelled(request.range, checkedPrefixes = 0L)
        }

        val sourceFloor = request.sourceChunks.flatMap { it.constraints(NetherBedrockLayer.FLOOR) }
        val sourceRoof = request.sourceChunks.flatMap { it.constraints(NetherBedrockLayer.ROOF) }
        val heldOutFloor = request.heldOutChunks.flatMap { it.constraints(NetherBedrockLayer.FLOOR) }
        val heldOutRoof = request.heldOutChunks.flatMap { it.constraints(NetherBedrockLayer.ROOF) }
        val floorEstimate = estimateCandidates(sourceFloor, NetherBedrockJava26_2Rule)
        val roofEstimate = estimateCandidates(sourceRoof, NetherBedrockJava26_2Rule)
        val primaryLayer = if (floorEstimate < roofEstimate) NetherBedrockLayer.FLOOR else NetherBedrockLayer.ROOF
        val primaryConstraints = if (primaryLayer == NetherBedrockLayer.FLOOR) sourceFloor else sourceRoof
        val secondaryConstraints = if (primaryLayer == NetherBedrockLayer.FLOOR) sourceRoof else sourceFloor
        val primaryHash = NetherBedrockJava26_2Rule.layerSalt(primaryLayer)
        val secondaryHash = NetherBedrockJava26_2Rule.layerSalt(
            if (primaryLayer == NetherBedrockLayer.FLOOR) NetherBedrockLayer.ROOF else NetherBedrockLayer.FLOOR,
        )
        val heldOutConstraints = heldOutFloor + heldOutRoof

        if (primaryConstraints.isEmpty()) {
            return NetherBedrockWorldSeedSearchOutcome.Progress(
                candidates = emptyList(),
                nextRange = request.range.next(request.range.prefixCount.coerceAtLeast(1L)),
                checkedPrefixes = 0L,
            )
        }

        val accumulator = WorldSeedAccumulator(
            candidateLimit = request.candidateLimit,
            primaryLayer = primaryLayer,
            primaryHash = primaryHash,
            secondaryHash = secondaryHash,
            secondaryConstraints = secondaryConstraints,
            sourceFloor = sourceFloor,
            sourceRoof = sourceRoof,
            heldOutConstraints = heldOutConstraints,
            isCancelled = isCancelled,
        )
        val tree = FilterTree.create(primaryConstraints.map(::FilterBlock))
        var prefix = request.range.startInclusive
        while (prefix < request.range.endExclusive) {
            if (isCancelled()) {
                return NetherBedrockWorldSeedSearchOutcome.Cancelled(
                    nextRange = NetherBedrockPrefixRange(prefix, request.range.endExclusive),
                    checkedPrefixes = prefix - request.range.startInclusive,
                )
            }
            if (!tree.visit(prefix shl NetherBedrockPrefixRange.LOWER_BITS, accumulator::acceptPrimaryPatternSeed)) {
                break
            }
            prefix++
        }

        val checked = prefix - request.range.startInclusive
        val nextRange = when {
            accumulator.cancelled -> NetherBedrockPrefixRange(prefix, request.range.endExclusive)
            prefix < request.range.endExclusive -> NetherBedrockPrefixRange(prefix, request.range.endExclusive)
            else -> request.range.next(request.range.prefixCount.coerceAtLeast(1L))
        }
        if (accumulator.cancelled) {
            return NetherBedrockWorldSeedSearchOutcome.Cancelled(checkNotNull(nextRange), checked)
        }
        if (accumulator.candidateLimitExceeded) {
            return NetherBedrockWorldSeedSearchOutcome.CandidateBudgetExceeded(
                request.candidateLimit,
                nextRange,
                checked,
            )
        }
        if (nextRange == null && accumulator.candidates.isEmpty()) {
            return NetherBedrockWorldSeedSearchOutcome.Contradicted(checked)
        }
        return NetherBedrockWorldSeedSearchOutcome.Progress(accumulator.candidates.toList(), nextRange, checked)
    }

    /**
     * Invokes a profile-pinned inverse stage and validates every returned world seed with the forward Java rule.
     * A broken or partial inverter can therefore never promote an arbitrary number into a verified full-world seed.
     */
    fun worldSeedCandidatesFromPatternSeed(
        patternSeed: Long,
        layer: NetherBedrockLayer,
        inverter: NetherBedrockWorldSeedInverter = UnavailableNetherBedrockWorldSeedInverter,
        rule: NetherBedrockJava26_2Rule = NetherBedrockJava26_2Rule,
        isCancelled: () -> Boolean = { false },
    ): NetherBedrockWorldSeedInversion {
        if (isCancelled()) return NetherBedrockWorldSeedInversion.Cancelled
        return when (val inversion = inverter.invert(patternSeed, layer, isCancelled)) {
            NetherBedrockWorldSeedInversion.Unavailable,
            NetherBedrockWorldSeedInversion.Cancelled -> inversion

            is NetherBedrockWorldSeedInversion.Candidates -> {
                if (isCancelled()) return NetherBedrockWorldSeedInversion.Cancelled
                val accepted = inversion.seeds.asSequence()
                    .takeWhile { !isCancelled() }
                    .filter { candidate -> rule.patternSeedFromWorldSeed(candidate, layer) == patternSeed }
                    .distinct()
                    .sorted()
                    .toList()
                if (isCancelled()) {
                    NetherBedrockWorldSeedInversion.Cancelled
                } else {
                    NetherBedrockWorldSeedInversion.Candidates(
                        seeds = accepted,
                        complete = inversion.complete && accepted.size == inversion.seeds.distinct().size,
                    )
                }
            }
        }
    }

    private fun estimateCandidates(
        constraints: List<NetherBedrockCellConstraint>,
        rule: NetherBedrockRuleAdapter,
    ): Long {
        if (constraints.isEmpty()) return Long.MAX_VALUE
        val logarithm = ln((JAVA_MASK + 1L).toDouble()) + constraints.sumOf { constraint ->
            ln(rule.estimatedSuccessProbability(constraint).coerceIn(MIN_PROBABILITY, 1.0))
        }
        if (logarithm >= ln(Long.MAX_VALUE.toDouble())) return Long.MAX_VALUE
        return ceil(exp(logarithm)).toLong().coerceAtLeast(1L)
    }

    private class WorldSeedAccumulator(
        private val candidateLimit: Int,
        private val primaryLayer: NetherBedrockLayer,
        private val primaryHash: Long,
        private val secondaryHash: Long,
        private val secondaryConstraints: List<NetherBedrockCellConstraint>,
        private val sourceFloor: List<NetherBedrockCellConstraint>,
        private val sourceRoof: List<NetherBedrockCellConstraint>,
        private val heldOutConstraints: List<NetherBedrockCellConstraint>,
        private val isCancelled: () -> Boolean,
    ) {
        val candidates = ArrayList<NetherBedrockWorldSeedCandidate>()
        private val seenSeeds = HashSet<Long>()
        var candidateLimitExceeded = false
            private set
        var cancelled = false
            private set

        fun acceptPrimaryPatternSeed(primaryPatternSeed: Long): Boolean {
            if (isCancelled()) return cancel()

            for (primaryInput in reverseNextLong(primaryPatternSeed, isCancelled)) {
                if (isCancelled()) return cancel()
                val commonBedrockSeed = (primaryInput xor primaryHash) and JAVA_MASK
                val secondaryPatternSeed = NetherBedrockJava26_2Rule.nextLong(commonBedrockSeed xor secondaryHash) and JAVA_MASK
                if (!secondaryConstraints.all { NetherBedrockJava26_2Rule.matches(secondaryPatternSeed, it) }) continue

                for (structureSeed in reverseNextLong(commonBedrockSeed, isCancelled)) {
                    if (isCancelled()) return cancel()
                    for (previousSeed in reverseNextLong(structureSeed, isCancelled)) {
                        if (isCancelled()) return cancel()
                        val worldSeed = NetherBedrockJava26_2Rule.nextLong(previousSeed)
                        if (!matchesWorldSeed(worldSeed, sourceFloor, sourceRoof)) continue
                        if (!matchesWorldSeed(worldSeed, heldOutConstraints)) continue
                        if (!seenSeeds.add(worldSeed)) continue
                        if (candidates.size >= candidateLimit) {
                            candidateLimitExceeded = true
                            return false
                        }
                        candidates += NetherBedrockWorldSeedCandidate(
                            seed = worldSeed,
                            primaryLayer = primaryLayer,
                            primaryPatternSeed = primaryPatternSeed,
                            verification = if (heldOutConstraints.isEmpty()) {
                                NetherBedrockVerification.UNVERIFIED
                            } else {
                                NetherBedrockVerification.HELD_OUT_VALIDATED
                            },
                        )
                    }
                }
            }
            return true
        }

        private fun cancel(): Boolean {
            cancelled = true
            return false
        }
    }

    private class CandidateAccumulator(
        private val candidateLimit: Int,
        private val layer: NetherBedrockLayer,
        private val heldOut: List<NetherBedrockCellConstraint>,
        private val rule: NetherBedrockRuleAdapter,
        private val isCancelled: () -> Boolean,
    ) {
        val candidates = ArrayList<NetherBedrockPatternSeedCandidate>()
        var candidateLimitExceeded = false
            private set
        var cancelled = false
            private set

        fun accept(patternSeed: Long): Boolean {
            if (isCancelled()) {
                cancelled = true
                return false
            }
            if (!validate(patternSeed, heldOut, rule)) return true
            if (candidates.size == candidateLimit) {
                candidateLimitExceeded = true
                return false
            }
            candidates += NetherBedrockPatternSeedCandidate(
                layer,
                patternSeed,
                if (heldOut.isEmpty()) NetherBedrockVerification.UNVERIFIED else NetherBedrockVerification.HELD_OUT_VALIDATED,
            )
            return true
        }
    }

    private fun matchesWorldSeed(
        worldSeed: Long,
        floorConstraints: List<NetherBedrockCellConstraint>,
        roofConstraints: List<NetherBedrockCellConstraint>,
    ): Boolean {
        val floorPatternSeed = NetherBedrockJava26_2Rule.patternSeedFromWorldSeed(worldSeed, NetherBedrockLayer.FLOOR)
        if (!floorConstraints.all { NetherBedrockJava26_2Rule.matches(floorPatternSeed, it) }) return false
        val roofPatternSeed = NetherBedrockJava26_2Rule.patternSeedFromWorldSeed(worldSeed, NetherBedrockLayer.ROOF)
        return roofConstraints.all { NetherBedrockJava26_2Rule.matches(roofPatternSeed, it) }
    }

    private fun matchesWorldSeed(
        worldSeed: Long,
        constraints: List<NetherBedrockCellConstraint>,
    ): Boolean {
        if (constraints.isEmpty()) return true
        val floor = constraints.filter { it.layer == NetherBedrockLayer.FLOOR }
        val roof = constraints.filter { it.layer == NetherBedrockLayer.ROOF }
        return matchesWorldSeed(worldSeed, floor, roof)
    }

    /**
     * Reverses the low 48 bits of Java `Random.nextLong`. The output omits sixteen bits, so the loop reconstructs
     * the missing low state bits and validates the remaining observed high-state bits before yielding external
     * Java-Random seeds. It is finite and polls cancellation every 1,024 states.
     */
    private fun reverseNextLong(output: Long, isCancelled: () -> Boolean): List<Long> {
        val observed = output and JAVA_MASK
        val lower32 = observed and 0xffff_ffffL
        val signedLower32 = lower32.toInt().toLong()
        val observedUpper16 = ((observed - signedLower32) and JAVA_MASK) ushr 32
        val seeds = ArrayList<Long>(2)

        for (missingBits in 0 until (1 shl 16)) {
            if (missingBits and REVERSAL_CANCELLATION_MASK == 0 && isCancelled()) return emptyList()
            val secondState = (lower32 shl 16) or missingBits.toLong()
            val firstState = ((secondState - JAVA_ADDEND) * JAVA_MULTIPLIER_INVERSE) and JAVA_MASK
            if (((firstState ushr 16) and 0xffffL) != observedUpper16) continue
            val initialState = ((firstState - JAVA_ADDEND) * JAVA_MULTIPLIER_INVERSE) and JAVA_MASK
            seeds += initialState xor JAVA_MULTIPLIER
        }
        return seeds
    }

    private class FilterTree private constructor(
        private val level: Int,
        private val checks: List<IntervalCheck>,
        private val next: FilterTree?,
    ) {
        fun visit(seedPrefix: Long, accept: (Long) -> Boolean): Boolean {
            if (checks.any { it.discards(seedPrefix) }) return true
            val child = next ?: return accept(seedPrefix)
            val split = 1L shl (level - 1)
            return child.visit(seedPrefix, accept) && child.visit(seedPrefix + split, accept)
        }

        companion object {
            fun create(blocks: List<FilterBlock>): FilterTree {
                val layers = ArrayList<Pair<Int, List<IntervalCheck>>>()
                for (lowerBits in NetherBedrockPrefixRange.LOWER_BITS downTo 0) {
                    val checks = blocks.mapNotNull { block ->
                        val discarded = block.discardedSeeds(lowerBits)
                        if (discarded > 0.0) block.createCheck(lowerBits, discarded) else null
                    }.sortedBy(IntervalCheck::sortKey)
                    layers += lowerBits to checks
                }

                var next: FilterTree? = null
                for ((lowerBits, checks) in layers.asReversed()) {
                    next = FilterTree(lowerBits, checks, next)
                }
                return checkNotNull(next)
            }
        }
    }

    private class FilterBlock(private val constraint: NetherBedrockCellConstraint) {
        private val lowerBound: Long
        private val upperBound: Long
        private var possibleRange: Long = JAVA_MASK
        private val positionHash: Long

        init {
            val interval = NetherBedrockJava26_2Rule.interval(constraint)
            lowerBound = interval.first
            upperBound = interval.last + 1L
            positionHash = NetherBedrockJava26_2Rule.positionHashWithJavaSeed(constraint.x, constraint.y, constraint.z)
        }

        fun discardedSeeds(lowerBits: Int): Double {
            val lowerMask = lowBitsMask(lowerBits)
            val bound = upperBound - lowerBound
            val successRange = bound + lowerMask * JAVA_MULTIPLIER
            val failChance = 1.0 - successRange.toDouble() / possibleRange.toDouble()
            return if (failChance <= 0.0) 0.0 else failChance * (1L shl lowerBits).toDouble()
        }

        fun createCheck(lowerBits: Int, sortKey: Double): IntervalCheck {
            val lowerMask = lowBitsMask(lowerBits)
            val range = (upperBound - lowerBound) + lowerMask * JAVA_MULTIPLIER
            require(range < possibleRange) { "Nether bedrock filter range did not narrow for $lowerBits bits" }
            possibleRange = range
            val offset = JAVA_MASK - upperBound
            val condition = lowerBound + offset - lowerMask * JAVA_MULTIPLIER
            return IntervalCheck(
                positionHash = positionHash and (JAVA_MASK xor lowerMask),
                condition = condition,
                offset = offset,
                sortKey = sortKey,
            )
        }
    }

    private data class IntervalCheck(
        val positionHash: Long,
        val condition: Long,
        val offset: Long,
        val sortKey: Double,
    ) {
        fun discards(seedPrefix: Long): Boolean {
            val value = (((seedPrefix xor positionHash) * JAVA_MULTIPLIER) + offset) and JAVA_MASK
            return java.lang.Long.compareUnsigned(value, condition) < 0
        }
    }

    private fun lowBitsMask(bits: Int): Long {
        require(bits in 0..NetherBedrockPrefixRange.LOWER_BITS)
        return if (bits == 0) 0L else (1L shl bits) - 1L
    }
}

private const val JAVA_MULTIPLIER = 0x5DEECE66DL
private const val JAVA_ADDEND = 0xBL
private const val JAVA_MASK = (1L shl 48) - 1L
private const val JAVA_MULTIPLIER_INVERSE = 0xDFE05BCB1365L
private const val MINIMUM_COMPLETE_CHUNKS = 2
private const val MIN_PROBABILITY = 1.0 / (JAVA_MASK + 1L).toDouble()
private const val REVERSAL_CANCELLATION_MASK = 0x3ff
