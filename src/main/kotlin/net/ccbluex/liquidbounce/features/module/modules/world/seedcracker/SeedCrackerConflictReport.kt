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

/** Runtime-only explanation of an inconsistent accepted evidence set. */
internal data class SeedCrackerConflictReport(
    val detail: String,
    val evidence: List<Evidence>,
) {
    init {
        require(detail.isNotBlank()) { "A conflict report needs a detail" }
        require(evidence.isNotEmpty()) { "A conflict report needs involved evidence" }
    }

    val fingerprint: String = evidence.joinToString(separator = "|") { it.id.value }

    internal sealed interface Evidence {
        val id: EvidenceId
        val displayLabel: String
    }

    internal data class StructureEvidence(
        override val id: EvidenceId,
        val type: StructureType,
        val chunkX: Int,
        val chunkZ: Int,
    ) : Evidence {
        override val displayLabel: String
            get() = "${type.id} @ $chunkX, $chunkZ"
    }

    internal data class NetherEvidence(
        override val id: EvidenceId,
        val chunkX: Int,
        val chunkZ: Int,
    ) : Evidence {
        override val displayLabel: String
            get() = "nether_bedrock @ $chunkX, $chunkZ"
    }

    companion object {
        fun inconsistentStructures(
            detail: String,
            evidence: Collection<StructureEvidence>,
        ) = SeedCrackerConflictReport(detail, evidence.stableEvidence())

        fun inconsistentNether(
            detail: String,
            evidence: Collection<NetherEvidence>,
        ) = SeedCrackerConflictReport(detail, evidence.stableEvidence())

        private fun <T : Evidence> Collection<T>.stableEvidence(): List<T> = asSequence()
            .distinctBy(Evidence::id)
            .sortedBy { it.id.value }
            .toList()
    }
}
