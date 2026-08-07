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
@file:Suppress("ReturnCount", "MaxLineLength")

package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedCollectionPlan

internal enum class GuidanceKind {
    INFO,
    ACTION,
    WARNING,
    RESULT,
}

/** Localisable guidance without a dependency on the module, chat, or notification implementation. */
internal data class SeedCrackerGuidanceMessage(
    val key: String,
    val arguments: List<String> = emptyList(),
    val kind: GuidanceKind = GuidanceKind.INFO,
) {
    init {
        require(key.isNotBlank()) { "Guidance key must not be blank" }
    }

    val deduplicationKey: String
        get() = buildString {
            append(key)
            arguments.forEach { argument -> append('\u0000').append(argument) }
        }

    fun matchesPresentationKey(presentationKey: String?): Boolean =
        presentationKey != null && key.removePrefix("seedcracker.guidance.") == presentationKey
}

/** Derives the next player action from an immutable snapshot and intentionally ignores revision-only changes. */
internal object SeedCrackerGuidance {

    fun nextAction(snapshot: SeedCrackerSnapshot): SeedCrackerGuidanceMessage {
        if (snapshot.state == CrackerState.PAUSED) return message("seedcracker.guidance.paused", GuidanceKind.INFO)

        val candidate = snapshot.candidate
        if (snapshot.state == CrackerState.CONTRADICTED || candidate?.verification == CandidateVerification.CONTRADICTED) {
            return message("seedcracker.guidance.candidateContradicted", GuidanceKind.WARNING)
        }
        if (candidate?.isVerified == true) {
            return message(
                "seedcracker.guidance.candidateVerified",
                GuidanceKind.RESULT,
                candidate.seed.toString(),
                candidate.source.id,
            )
        }
        if (candidate?.kind == SeedCandidateKind.STRUCTURE_SEED_48) {
            return message(
                "seedcracker.guidance.structureSeedNeedsWorldProof",
                GuidanceKind.ACTION,
                candidate.seed.toString(),
            )
        }
        if (candidate != null && snapshot.state == CrackerState.CANDIDATE) {
            return message("seedcracker.guidance.verifyCandidate", GuidanceKind.ACTION, candidate.seed.toString())
        }

        pendingAction(snapshot)?.let { return it }
        if (snapshot.state == CrackerState.SOLVING) return message("seedcracker.guidance.solving", GuidanceKind.INFO)

        if (snapshot.scope.isNether && CrackingTechnique.NETHER_BEDROCK in snapshot.enabledTechniques) {
            if (snapshot.netherBedrock.count(CrackEvidence::isAccepted) < MINIMUM_NETHER_CHUNKS) {
                return message("seedcracker.guidance.collectNetherBedrock", GuidanceKind.ACTION)
            }
            return message("seedcracker.guidance.waitForSolver", GuidanceKind.INFO)
        }

        if (snapshot.scope.isOverworld && CrackingTechnique.STRUCTURES in snapshot.enabledTechniques) {
            val progress = StructureSeedCollectionPlan.progress(snapshot.structures)
            val requested = StructureSeedCollectionPlan.requestedStructure(snapshot.structures)
            return message(
                "seedcracker.guidance.findStructure",
                GuidanceKind.ACTION,
                requested.id,
                progress.acceptedIndependentEvidence.toString(),
                progress.requiredIndependentEvidence.toString(),
            )
        }

        return message("seedcracker.guidance.switchToSupportedDimension", GuidanceKind.ACTION)
    }

    fun shouldAnnounce(
        previous: SeedCrackerGuidanceMessage?,
        next: SeedCrackerGuidanceMessage,
    ): Boolean = previous?.deduplicationKey != next.deduplicationKey

    private fun pendingAction(snapshot: SeedCrackerSnapshot): SeedCrackerGuidanceMessage? {
        val pending = snapshot.allEvidence.filter { it.status == EvidenceStatus.PENDING_CONFIRMATION }
        if (pending.isEmpty()) return null
        val guided = StructureSeedCollectionPlan.guidedPendingEvidence(snapshot.structures)
        return if (guided != null) {
            message("seedcracker.guidance.confirmEvidence", GuidanceKind.ACTION, guided.id.value)
        } else {
            message("seedcracker.guidance.chooseEvidence", GuidanceKind.ACTION, pending.size.toString())
        }
    }

    private fun message(key: String, kind: GuidanceKind, vararg arguments: String) =
        SeedCrackerGuidanceMessage(key, arguments.toList(), kind)

    private const val MINIMUM_NETHER_CHUNKS = 2
}
