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

internal object BaseFinderDynamicBounds {

    fun fromEvidence(
        evidence: Collection<FamilyEvidence>,
        seedMismatchBounds: BaseFinderBounds?,
    ): BaseFinderBounds? {
        val anchorBounds = BaseFinderBounds.enclosing(
            evidence.asSequence()
                .filter { it.family in STATIC_FAMILIES }
                .flatMap { it.anchors.asSequence() }
                .filter { it.key !in MOVING_STORAGE_KEYS }
                .map(EvidenceAnchor::position)
                .toList(),
        )
        return when {
            anchorBounds == null -> seedMismatchBounds
            seedMismatchBounds == null -> anchorBounds
            else -> anchorBounds.merge(seedMismatchBounds)
        }
    }

    private val STATIC_FAMILIES = setOf(
        BaseSignalFamily.STORAGE,
        BaseSignalFamily.UTILITIES,
        BaseSignalFamily.AUTOMATION,
        BaseSignalFamily.STRUCTURAL,
        BaseSignalFamily.SEED_MISMATCH,
    )
    private val MOVING_STORAGE_KEYS = setOf(
        "storage.container_vehicle",
        "storage.minecart_container",
        "storage.minecart_furnace",
    )
}
