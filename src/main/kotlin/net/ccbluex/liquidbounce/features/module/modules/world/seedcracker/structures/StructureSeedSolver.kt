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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures

import net.ccbluex.liquidbounce.seedcracker.seedfinding.mccore.rand.ChunkRand
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mccore.version.MCVersion
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mcfeature.structure.DesertPyramid
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mcfeature.structure.Igloo
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mcfeature.structure.JunglePyramid
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mcfeature.structure.Monument
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mcfeature.structure.PillagerOutpost
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mcfeature.structure.RegionStructure
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mcfeature.structure.Shipwreck
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mcfeature.structure.SwampHut
import net.ccbluex.liquidbounce.seedcracker.seedfinding.mcfeature.structure.UniformStructure
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceStatus
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.GenerationProfile
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.StructureObservation
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.StructureType
import kotlin.math.ceil
import kotlin.math.ln

/**
 * The structures supported by the Java 26.2 structure-seed profile.
 *
 * This deliberately is a solver-local projection. The tracker converts accepted
 * [net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.StructureObservation]
 * values to [StructureSeedEvidence] before work reaches a background solver.
 */
internal enum class StructureSeedStructure {
    DESERT_PYRAMID,
    JUNGLE_TEMPLE,
    SWAMP_HUT,
    IGLOO,
    SHIPWRECK,
    PILLAGER_OUTPOST,
    OCEAN_MONUMENT,
}

/** Immutable, client-visible structure evidence with no Minecraft-world references. */
internal data class StructureSeedEvidence(
    val id: String,
    val structure: StructureSeedStructure,
    val startChunkX: Int,
    val startChunkZ: Int,
    val fingerprint: Long,
) {
    init {
        require(id.isNotBlank()) { "Structure evidence id must not be blank" }
    }
}

/**
 * Immutable continuation for a bounded 48-bit structure-seed lift.
 *
 * The expensive lower-bit reduction is retained as primitive values only; every resumed worker still receives a
 * frozen evidence list and rechecks all complete constraints before adding a candidate.
 */
internal data class StructureSeedSearchCursor(
    val evidenceFingerprint: String,
    val compatibleLowerBits: List<Long>,
    val lowerBitIndex: Int,
    val nextUpperBits: Long,
    val discoveredCandidates: List<Long> = emptyList(),
) {
    init {
        require(evidenceFingerprint.isNotBlank()) { "Structure search cursor needs an evidence fingerprint" }
        require(compatibleLowerBits.all { it in 0L until (1L shl 19) }) {
            "Lower-bit candidates must be 19-bit values"
        }
        require(lowerBitIndex in 0..compatibleLowerBits.size) { "Lower-bit index is outside the candidate list" }
        require(nextUpperBits in 0L until (1L shl 29)) { "Upper-bit cursor is outside the 29-bit lift domain" }
        require(discoveredCandidates.all { it in 0L..STRUCTURE_SEED_MASK }) {
            "Discovered candidates must be 48-bit structure seeds"
        }
    }

    private companion object {
        const val STRUCTURE_SEED_MASK = (1L shl 48) - 1L
    }
}

/**
 * Converts exactly one accepted, Java 26.2 Overworld observation to a pure solver value.
 *
 * Pending player confirmation, rejected evidence, and any non-Overworld scope deliberately
 * return null, so callers cannot accidentally hand speculative or cross-dimension data to a
 * background seed search.
 */
internal fun StructureObservation.toStructureSeedEvidenceOrNull(): StructureSeedEvidence? {
    if (!isAccepted || !scope.isOverworld || scope.generationProfile != GenerationProfile.JAVA_26_2) {
        return null
    }

    return StructureSeedEvidence(
        id = id.value,
        structure = type.toStructureSeedStructure(),
        startChunkX = anchorChunk.x,
        startChunkZ = anchorChunk.z,
        fingerprint = snapshotHash,
    )
}

private fun StructureType.toStructureSeedStructure(): StructureSeedStructure = when (this) {
    StructureType.IGLOO -> StructureSeedStructure.IGLOO
    StructureType.DESERT_PYRAMID -> StructureSeedStructure.DESERT_PYRAMID
    StructureType.JUNGLE_TEMPLE -> StructureSeedStructure.JUNGLE_TEMPLE
    StructureType.SWAMP_HUT -> StructureSeedStructure.SWAMP_HUT
    StructureType.SHIPWRECK -> StructureSeedStructure.SHIPWRECK
    StructureType.PILLAGER_OUTPOST -> StructureSeedStructure.PILLAGER_OUTPOST
    StructureType.OCEAN_MONUMENT -> StructureSeedStructure.OCEAN_MONUMENT
}

/** A narrow bridge that keeps the solving core independent of tracker model changes. */
internal fun interface StructureObservationBridge<in T> {
    /** Return null for unaccepted, rejected, cross-scope, or otherwise unusable observations. */
    fun acceptedEvidence(observation: T): StructureSeedEvidence?
}

/** Cooperative cancellation is checked before and after every adapter call. */
internal fun interface StructureSeedCancellationProbe {
    fun isCancelled(): Boolean

    companion object {
        val Never = StructureSeedCancellationProbe { false }
    }
}

/**
 * The only extension point that may call a version-pinned SeedFinding implementation.
 *
 * The default implementation intentionally does not guess a seed until those libraries
 * have been bound and version-validated by the owning module.
 */
internal fun interface StructureSeedConstraintAdapter {
    fun solve(
        evidence: List<StructureSeedEvidence>,
        cancellationProbe: StructureSeedCancellationProbe,
        cursor: StructureSeedSearchCursor?,
    ): StructureSeedAdapterResult
}

internal sealed interface StructureSeedAdapterResult {
    data class NeedMoreEvidence(
        val preferredNext: StructureSeedStructure? = null,
    ) : StructureSeedAdapterResult

    data class StructureSeeds(
        val candidates: List<Long>,
        val preferredNext: StructureSeedStructure? = null,
    ) : StructureSeedAdapterResult

    data class FullSeeds(
        val candidates: List<Long>,
        val verified: Boolean = false,
        val preferredNext: StructureSeedStructure? = null,
    ) : StructureSeedAdapterResult

    data class ContradictedEvidence(
        val detail: String,
        val conflictingEvidenceIds: List<String> = emptyList(),
    ) : StructureSeedAdapterResult

    data class TooManyStructureSeeds(
        val candidateLimit: Int,
        val preferredNext: StructureSeedStructure? = null,
    ) : StructureSeedAdapterResult

    /** The current bounded lift slice finished; schedule [continuation] after the normal tracker debounce. */
    data class Searching(
        val continuation: StructureSeedSearchCursor,
    ) : StructureSeedAdapterResult

    data object Unavailable : StructureSeedAdapterResult
}

internal enum class StructureSeedEvidenceGap {
    MINIMUM_INDEPENDENT_OBSERVATIONS,
    ADAPTER_REQUIRES_MORE_EVIDENCE,
    MULTIPLE_WORLD_SEEDS,
    NO_CANDIDATES,
    TOO_MANY_STRUCTURE_SEEDS,
}

internal data class StructureSeedRecommendation(
    val structure: StructureSeedStructure,
    val requiresIndependentInstance: Boolean,
)

/**
 * The information-bearing part of the current pinned lower-bit lift.
 *
 * The 26.2 reference exposes Shipwreck as the only supported [UniformStructure] in this module's
 * structure set. Its `spacing - separation` offset is 20, yielding just under 8.65 bits per
 * independent placement. Five independently observed shipwreck starts are therefore needed before
 * the bounded 40-bit lift is worth scheduling. Keep this policy separate from the generic solver:
 * other accepted structure types can still validate a lifted candidate afterwards.
 */
internal data class StructureSeedCollectionProgress(
    val acceptedIndependentEvidence: Int,
    val requiredIndependentEvidence: Int,
) {
    init {
        require(acceptedIndependentEvidence >= 0) { "Accepted independent evidence must not be negative" }
        require(requiredIndependentEvidence > 0) { "Required independent evidence must be positive" }
    }

    val isReady: Boolean
        get() = acceptedIndependentEvidence >= requiredIndependentEvidence
}

internal object StructureSeedCollectionPlan {

    fun progress(observations: Collection<StructureObservation>): StructureSeedCollectionProgress {
        val independentShipwrecks = observations.asSequence()
            .filter(StructureObservation::isAccepted)
            .filter { it.type == StructureType.SHIPWRECK }
            .map(StructureObservation::anchorChunk)
            .distinct()
            .count()

        return StructureSeedCollectionProgress(
            acceptedIndependentEvidence = independentShipwrecks,
            requiredIndependentEvidence = REQUIRED_SHIPWRECKS,
        )
    }

    /** Returns the next useful visible structure without pretending that old-structure inputs lift the seed. */
    fun requestedStructure(observations: Collection<StructureObservation>): StructureType {
        if (!progress(observations).isReady) {
            return StructureType.SHIPWRECK
        }

        val acceptedTypes = observations.asSequence()
            .filter(StructureObservation::isAccepted)
            .mapTo(linkedSetOf(), StructureObservation::type)
        return FOLLOW_UP_STRUCTURES.firstOrNull { it !in acceptedTypes } ?: StructureType.SHIPWRECK
    }

    /** Selects without guessing only when exactly one pending observation matches the current information need. */
    fun guidedPendingEvidence(observations: Collection<StructureObservation>): StructureObservation? =
        guidedPendingEvidenceCandidates(observations).singleOrNull()

    fun guidedPendingEvidenceCandidates(observations: Collection<StructureObservation>): List<StructureObservation> {
        val requested = requestedStructure(observations)
        return observations.asSequence()
            .filter { it.status == EvidenceStatus.PENDING_CONFIRMATION && it.type == requested }
            .sortedBy { it.id.value }
            .toList()
    }

    private val REQUIRED_SHIPWRECKS = ceil(
        MINIMUM_LIFTING_BITS / (ln(SHIPWRECK_OFFSET * SHIPWRECK_OFFSET) / ln(2.0)),
    ).toInt()

    private val FOLLOW_UP_STRUCTURES = listOf(
        StructureType.DESERT_PYRAMID,
        StructureType.JUNGLE_TEMPLE,
        StructureType.SWAMP_HUT,
        StructureType.IGLOO,
        StructureType.PILLAGER_OUTPOST,
        StructureType.OCEAN_MONUMENT,
    )

    private const val SHIPWRECK_OFFSET = 20.0
    private const val MINIMUM_LIFTING_BITS = 40.0
}

internal sealed interface StructureSeedSolveResult {
    data class NeedMoreEvidence(
        val gap: StructureSeedEvidenceGap,
        val next: StructureSeedRecommendation,
        val acceptedEvidenceCount: Int,
        val minimumIndependentObservations: Int,
    ) : StructureSeedSolveResult

    data class StructureSeeds(
        val candidates: List<Long>,
        val next: StructureSeedRecommendation,
        val acceptedEvidenceCount: Int,
    ) : StructureSeedSolveResult

    data class FullSeed(
        val seed: Long,
        val verified: Boolean,
        val acceptedEvidenceCount: Int,
    ) : StructureSeedSolveResult

    data class ContradictedEvidence(
        val detail: String,
        val acceptedEvidenceCount: Int,
        val conflictingEvidenceIds: List<String>,
    ) : StructureSeedSolveResult

    data class Searching(
        val continuation: StructureSeedSearchCursor,
        val acceptedEvidenceCount: Int,
    ) : StructureSeedSolveResult

    data object Cancelled : StructureSeedSolveResult

    /** No version-pinned engine is available, so no seed-like result is emitted. */
    data object Unavailable : StructureSeedSolveResult
}

/**
 * Pure structure solving coordinator.
 *
 * It never accesses Minecraft state, starts threads, or retains mutable tracker objects.
 * Call it from the tracker-owned cancellable background job with a frozen accepted snapshot.
 */
internal class StructureSeedSolver(
    private val adapter: StructureSeedConstraintAdapter = UnavailableStructureSeedConstraintAdapter,
) {

    fun solve(
        acceptedEvidence: Collection<StructureSeedEvidence>,
        cancellationProbe: StructureSeedCancellationProbe = StructureSeedCancellationProbe.Never,
        cursor: StructureSeedSearchCursor? = null,
    ): StructureSeedSolveResult {
        if (cancellationProbe.isCancelled()) {
            return StructureSeedSolveResult.Cancelled
        }

        val evidence = acceptedEvidence.stableDistinct()
        if (evidence.isEmpty()) {
            return needMore(
                evidence = evidence,
                gap = StructureSeedEvidenceGap.MINIMUM_INDEPENDENT_OBSERVATIONS,
            )
        }

        val adapterResult = adapter.solve(evidence, cancellationProbe, cursor)
        if (cancellationProbe.isCancelled()) {
            return StructureSeedSolveResult.Cancelled
        }

        return adapterResult.toSolveResult(evidence)
    }

    fun <T> solveAccepted(
        observations: Collection<T>,
        bridge: StructureObservationBridge<T>,
        cancellationProbe: StructureSeedCancellationProbe = StructureSeedCancellationProbe.Never,
        cursor: StructureSeedSearchCursor? = null,
    ): StructureSeedSolveResult = solve(
        acceptedEvidence = observations.mapNotNull(bridge::acceptedEvidence),
        cancellationProbe = cancellationProbe,
        cursor = cursor,
    )

    private fun StructureSeedAdapterResult.toSolveResult(
        evidence: List<StructureSeedEvidence>,
    ): StructureSeedSolveResult {
        return when (this) {
            is StructureSeedAdapterResult.NeedMoreEvidence -> needMore(
                evidence = evidence,
                gap = StructureSeedEvidenceGap.ADAPTER_REQUIRES_MORE_EVIDENCE,
                preferredNext = preferredNext,
            )

            is StructureSeedAdapterResult.StructureSeeds -> {
                val candidates = candidates.validStructureSeeds()
                if (candidates.isEmpty()) {
                    needMore(evidence, StructureSeedEvidenceGap.NO_CANDIDATES, preferredNext)
                } else {
                    StructureSeedSolveResult.StructureSeeds(
                        candidates = candidates,
                        next = recommendation(evidence, preferredNext),
                        acceptedEvidenceCount = evidence.size,
                    )
                }
            }

            is StructureSeedAdapterResult.FullSeeds -> {
                val candidates = candidates.distinct().sorted()
                if (candidates.size != 1) {
                    needMore(
                        evidence = evidence,
                        gap = if (candidates.isEmpty()) {
                            StructureSeedEvidenceGap.NO_CANDIDATES
                        } else {
                            StructureSeedEvidenceGap.MULTIPLE_WORLD_SEEDS
                        },
                        preferredNext = preferredNext,
                    )
                } else {
                    StructureSeedSolveResult.FullSeed(
                        seed = candidates.single(),
                        verified = verified,
                        acceptedEvidenceCount = evidence.size,
                    )
                }
            }

            is StructureSeedAdapterResult.ContradictedEvidence -> StructureSeedSolveResult.ContradictedEvidence(
                detail = detail,
                acceptedEvidenceCount = evidence.size,
                conflictingEvidenceIds = conflictingIdsWithin(evidence),
            )

            is StructureSeedAdapterResult.TooManyStructureSeeds -> needMore(
                evidence = evidence,
                gap = StructureSeedEvidenceGap.TOO_MANY_STRUCTURE_SEEDS,
                preferredNext = preferredNext,
            )

            is StructureSeedAdapterResult.Searching -> StructureSeedSolveResult.Searching(
                continuation = continuation,
                acceptedEvidenceCount = evidence.size,
            )

            StructureSeedAdapterResult.Unavailable -> StructureSeedSolveResult.Unavailable
        }
    }

    private fun StructureSeedAdapterResult.ContradictedEvidence.conflictingIdsWithin(
        evidence: List<StructureSeedEvidence>,
    ): List<String> = conflictingEvidenceIds
        .takeIf(List<String>::isNotEmpty)
        ?.filterTo(linkedSetOf()) { id -> evidence.any { it.id == id } }
        ?.toList()
        .orEmpty()
        .ifEmpty { evidence.map(StructureSeedEvidence::id) }

    private fun needMore(
        evidence: List<StructureSeedEvidence>,
        gap: StructureSeedEvidenceGap,
        preferredNext: StructureSeedStructure? = null,
    ) = StructureSeedSolveResult.NeedMoreEvidence(
        gap = gap,
        next = recommendation(evidence, preferredNext),
        acceptedEvidenceCount = evidence.size,
        minimumIndependentObservations = maxOf(MINIMUM_INDEPENDENT_OBSERVATIONS, evidence.size + 1),
    )

    private fun recommendation(
        evidence: List<StructureSeedEvidence>,
        preferredNext: StructureSeedStructure?,
    ): StructureSeedRecommendation {
        val observedStructures = evidence.mapTo(linkedSetOf(), StructureSeedEvidence::structure)
        val structure = preferredNext ?: STRUCTURE_PRIORITY.firstOrNull { it !in observedStructures }
            ?: STRUCTURE_PRIORITY.first()
        return StructureSeedRecommendation(structure, requiresIndependentInstance = true)
    }

    private fun Collection<StructureSeedEvidence>.stableDistinct(): List<StructureSeedEvidence> = asSequence()
        .sortedWith(
            compareBy<StructureSeedEvidence>(StructureSeedEvidence::structure)
                .thenBy(StructureSeedEvidence::startChunkX)
                .thenBy(StructureSeedEvidence::startChunkZ)
                .thenBy(StructureSeedEvidence::id)
                .thenBy(StructureSeedEvidence::fingerprint),
        )
        .distinctBy { evidence ->
            StructureSeedLocationKey(evidence.structure, evidence.startChunkX, evidence.startChunkZ)
        }
        .toList()

    private fun List<Long>.validStructureSeeds(): List<Long> = asSequence()
        .filter { it in 0L..STRUCTURE_SEED_MASK }
        .distinct()
        .sorted()
        .toList()

    private data class StructureSeedLocationKey(
        val structure: StructureSeedStructure,
        val chunkX: Int,
        val chunkZ: Int,
    )

    private data object UnavailableStructureSeedConstraintAdapter : StructureSeedConstraintAdapter {
        override fun solve(
            evidence: List<StructureSeedEvidence>,
            cancellationProbe: StructureSeedCancellationProbe,
            cursor: StructureSeedSearchCursor?,
        ) = StructureSeedAdapterResult.Unavailable
    }

    private companion object {
        const val MINIMUM_INDEPENDENT_OBSERVATIONS = 2
        const val STRUCTURE_SEED_MASK = (1L shl 48) - 1

        val STRUCTURE_PRIORITY = listOf(
            StructureSeedStructure.DESERT_PYRAMID,
            StructureSeedStructure.JUNGLE_TEMPLE,
            StructureSeedStructure.SWAMP_HUT,
            StructureSeedStructure.IGLOO,
            StructureSeedStructure.SHIPWRECK,
            StructureSeedStructure.PILLAGER_OUTPOST,
            StructureSeedStructure.OCEAN_MONUMENT,
        )
    }
}

/**
 * Version-pinned adapter for the SeedFinding Java 26.2 artifact set.
 *
 * The implementation deliberately only turns fully accepted observations into
 * [RegionStructure] constraints. Its search is bounded, cancellable, and runs
 * only after enough liftable information has been collected; it never touches
 * Minecraft client objects or makes a network request.
 */
internal class SeedFindingStructureConstraintAdapter(
    // SeedFinding's pinned 26.2 reference artifacts expose legacy structure placement through this final enum.
    // Do not use latest(): a future artifact upgrade must add a separately tested GenerationProfile instead.
    private val version: MCVersion = SEED_FINDING_JAVA_26_2_REFERENCE_VERSION,
    private val search: SeedFindingStructureSeedSearch = SeedFindingLiftingStructureSearch(version),
) : StructureSeedConstraintAdapter {

    fun solve(
        evidence: List<StructureSeedEvidence>,
        cancellationProbe: StructureSeedCancellationProbe,
    ): StructureSeedAdapterResult = solve(evidence, cancellationProbe, cursor = null)

    override fun solve(
        evidence: List<StructureSeedEvidence>,
        cancellationProbe: StructureSeedCancellationProbe,
        cursor: StructureSeedSearchCursor?,
    ): StructureSeedAdapterResult {
        if (cancellationProbe.isCancelled()) {
            return StructureSeedAdapterResult.NeedMoreEvidence()
        }

        return try {
            search.findCandidates(evidence.map(::constraintFor), cancellationProbe, cursor)
        } catch (_: LinkageError) {
            // JIJ resolution must not turn a missing third-party class into a guessed seed.
            StructureSeedAdapterResult.Unavailable
        }
    }

    private fun constraintFor(evidence: StructureSeedEvidence) = SeedFindingStructureConstraint(
        evidence = evidence,
        feature = when (evidence.structure) {
            StructureSeedStructure.DESERT_PYRAMID -> DesertPyramid(version)
            StructureSeedStructure.JUNGLE_TEMPLE -> JunglePyramid(version)
            StructureSeedStructure.SWAMP_HUT -> SwampHut(version)
            StructureSeedStructure.IGLOO -> Igloo(version)
            StructureSeedStructure.SHIPWRECK -> Shipwreck(version)
            StructureSeedStructure.PILLAGER_OUTPOST -> PillagerOutpost(version)
            StructureSeedStructure.OCEAN_MONUMENT -> Monument(version)
        },
    )
}

private val SEED_FINDING_JAVA_26_2_REFERENCE_VERSION: MCVersion = checkNotNull(MCVersion.fromString("1.21.3")) {
    "The pinned Java 26.2 SeedFinding reference must expose Minecraft 1.21.3 placement rules"
}

internal fun interface SeedFindingStructureSeedSearch {
    fun findCandidates(
        constraints: List<SeedFindingStructureConstraint>,
        cancellationProbe: StructureSeedCancellationProbe,
        cursor: StructureSeedSearchCursor?,
    ): StructureSeedAdapterResult
}

/**
 * Immutable adapter input. Its only references are SeedFinding feature objects,
 * which are constructed in the solver worker after the tracker froze primitives.
 */
internal data class SeedFindingStructureConstraint(
    val evidence: StructureSeedEvidence,
    val feature: RegionStructure<*, *>,
) {
    val structure: StructureSeedStructure
        get() = evidence.structure
}

/**
 * Performs the 48-bit lifting procedure used by the pinned SeedFinding profile.
 *
 * The inexpensive 19-bit pass checks the low two bits of two uniform-structure
 * offsets. Only then does it inspect compatible full 48-bit candidates. The
 * operation is intentionally cooperative: the caller owns the worker pool and
 * may invalidate the frozen observation revision at any time.
 */
internal class SeedFindingLiftingStructureSearch(
    private val version: MCVersion,
    private val candidateLimit: Int = DEFAULT_STRUCTURE_CANDIDATE_LIMIT,
) : SeedFindingStructureSeedSearch {

    init {
        require(candidateLimit > 0) { "Structure candidate limit must be positive" }
    }

    override fun findCandidates(
        constraints: List<SeedFindingStructureConstraint>,
        cancellationProbe: StructureSeedCancellationProbe,
        cursor: StructureSeedSearchCursor?,
    ): StructureSeedAdapterResult {
        val liftable = constraints.filter { it.isLiftableForLifting() }
        if (liftable.informationBits() < MINIMUM_LIFTING_BITS) {
            return StructureSeedAdapterResult.NeedMoreEvidence()
        }

        val fingerprint = constraints.evidenceFingerprint()
        val resumedCursor = cursor?.takeIf { it.evidenceFingerprint == fingerprint }
        val lowerBits = resumedCursor?.compatibleLowerBits ?: findCompatibleLowerBits(liftable, cancellationProbe)
        if (cancellationProbe.isCancelled()) {
            return StructureSeedAdapterResult.NeedMoreEvidence()
        }
        if (lowerBits.isEmpty()) {
            return StructureSeedAdapterResult.ContradictedEvidence(
                "No Java 26.2 structure seed matches the accepted observations",
                conflictingEvidenceIds = liftable.map { it.evidence.id },
            )
        }

        return liftCandidates(lowerBits, constraints, cancellationProbe, fingerprint, resumedCursor)
    }

    private fun findCompatibleLowerBits(
        liftable: List<SeedFindingStructureConstraint>,
        cancellationProbe: StructureSeedCancellationProbe,
    ): List<Long> {
        val candidates = ArrayList<Long>()
        val random = ChunkRand()
        for (lowerBits in 0L until LOWER_BIT_SEARCH_SPACE) {
            if (lowerBits and CANCELLATION_CHECK_MASK == 0L && cancellationProbe.isCancelled()) {
                return emptyList()
            }
            if (liftable.all { it.matchesLowerBits(lowerBits, random, version) }) {
                candidates += lowerBits
            }
        }
        return candidates
    }

    private fun liftCandidates(
        lowerBits: List<Long>,
        constraints: List<SeedFindingStructureConstraint>,
        cancellationProbe: StructureSeedCancellationProbe,
        fingerprint: String,
        cursor: StructureSeedSearchCursor?,
    ): StructureSeedAdapterResult {
        val candidates = cursor?.discoveredCandidates?.toCollection(linkedSetOf()) ?: linkedSetOf()
        val random = ChunkRand()
        var lowerIndex = cursor?.lowerBitIndex ?: 0
        var upper = cursor?.nextUpperBits ?: 0L
        var checks = 0L

        while (lowerIndex < lowerBits.size && checks < MAX_LIFTED_SEED_CHECKS_PER_SLICE) {
            if (checks and CANCELLATION_CHECK_MASK == 0L && cancellationProbe.isCancelled()) {
                return StructureSeedAdapterResult.NeedMoreEvidence()
            }

            val seed = (upper shl LOWER_BITS) or lowerBits[lowerIndex]
            if (constraints.all { it.matches(seed, random) }) {
                if (candidates.size >= candidateLimit) {
                    return StructureSeedAdapterResult.TooManyStructureSeeds(candidateLimit)
                }
                candidates += seed
            }

            checks++
            upper++
            if (upper == UPPER_BIT_SEARCH_SPACE) {
                lowerIndex++
                upper = 0L
            }
        }

        if (lowerIndex == lowerBits.size) {
            return StructureSeedAdapterResult.StructureSeeds(candidates.toList())
        }

        return StructureSeedAdapterResult.Searching(
            StructureSeedSearchCursor(
                evidenceFingerprint = fingerprint,
                compatibleLowerBits = lowerBits.toList(),
                lowerBitIndex = lowerIndex,
                nextUpperBits = upper,
                discoveredCandidates = candidates.toList(),
            ),
        )
    }

    private fun SeedFindingStructureConstraint.isLiftableForLifting() =
        feature is UniformStructure<*> && structure != StructureSeedStructure.PILLAGER_OUTPOST

    private fun List<SeedFindingStructureConstraint>.informationBits(): Double = sumOf { constraint ->
        val uniform = constraint.feature as? UniformStructure<*> ?: return@sumOf 0.0
        val offset = uniform.offset.toDouble()
        ln(offset * offset) / ln(2.0)
    }

    private fun SeedFindingStructureConstraint.matchesLowerBits(
        lowerBits: Long,
        random: ChunkRand,
        version: MCVersion,
    ): Boolean {
        val uniform = feature as? UniformStructure<*> ?: return false
        val data = uniform.at(evidence.startChunkX, evidence.startChunkZ)
        random.setRegionSeed(lowerBits, data.regionX, data.regionZ, uniform.salt, version)
        return random.nextInt(uniform.offset) % OFFSET_MODULUS == data.offsetX % OFFSET_MODULUS &&
            random.nextInt(uniform.offset) % OFFSET_MODULUS == data.offsetZ % OFFSET_MODULUS
    }

    private fun SeedFindingStructureConstraint.matches(seed: Long, random: ChunkRand): Boolean =
        feature.at(evidence.startChunkX, evidence.startChunkZ).testStart(seed, random)

    private fun List<SeedFindingStructureConstraint>.evidenceFingerprint(): String = asSequence()
        .map(SeedFindingStructureConstraint::evidence)
        .sortedWith(
            compareBy<StructureSeedEvidence>(StructureSeedEvidence::id)
                .thenBy(StructureSeedEvidence::structure)
                .thenBy(StructureSeedEvidence::startChunkX)
                .thenBy(StructureSeedEvidence::startChunkZ)
                .thenBy(StructureSeedEvidence::fingerprint),
        )
        .joinToString(separator = "|") { evidence ->
            listOf(
                evidence.id,
                evidence.structure,
                evidence.startChunkX,
                evidence.startChunkZ,
                evidence.fingerprint,
            ).joinToString(separator = ":")
        }

    private companion object {
        const val LOWER_BITS = 19
        const val LOWER_BIT_SEARCH_SPACE = 1L shl LOWER_BITS
        const val UPPER_BIT_SEARCH_SPACE = 1L shl (48 - LOWER_BITS)
        const val CANCELLATION_CHECK_MASK = 0x3fffL
        const val OFFSET_MODULUS = 4
        const val MINIMUM_LIFTING_BITS = 40.0
        const val DEFAULT_STRUCTURE_CANDIDATE_LIMIT = 1_024
        const val MAX_LIFTED_SEED_CHECKS_PER_SLICE = 1L shl 20
    }
}
