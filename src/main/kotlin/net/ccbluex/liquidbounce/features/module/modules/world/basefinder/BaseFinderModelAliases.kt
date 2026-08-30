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

internal typealias BaseSignalFamily = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseSignalFamily
internal typealias SeedComparePhase = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.SeedComparePhase
internal typealias SeedMismatchKind = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.SeedMismatchKind
internal typealias SeedMismatchCell = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.SeedMismatchCell
internal typealias BaseFalsePositive = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFalsePositive
internal typealias ConfidenceTier = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.ConfidenceTier
internal typealias BaseCoordinate = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseCoordinate
internal typealias BaseFinderBounds = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderBounds
internal typealias ChunkCoordinate = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.ChunkCoordinate
internal typealias EvidenceAnchor = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.EvidenceAnchor

internal typealias StorageSignal = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.StorageSignal
internal typealias UtilitiesSignal = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.UtilitiesSignal
internal typealias AutomationSignal = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.AutomationSignal
internal typealias EntitiesSignal = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.EntitiesSignal
internal typealias StructuralSignal = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.StructuralSignal
internal typealias GeometrySignal = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.GeometrySignal
internal typealias ActivitySignal = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.ActivitySignal
internal typealias ChunkTrailsSignal = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.ChunkTrailsSignal
internal typealias SeedMismatchSignal = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.SeedMismatchSignal
internal typealias ChunkEvidenceSnapshot = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.ChunkEvidenceSnapshot

internal typealias BaseDetectionStrategy = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseDetectionStrategy
internal typealias FamilyEvidence = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.FamilyEvidence
internal typealias EvidenceSummary = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.EvidenceSummary
internal typealias BaseScoreBreakdown = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseScoreBreakdown
internal typealias ScoredBaseCandidate = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.ScoredBaseCandidate
internal typealias BaseFinding = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinding
internal typealias BaseFinderLabelContribution = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderLabelContribution
internal typealias BaseFinderLabelEvidence = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderLabelEvidence
internal typealias BaseFinderMarker = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderMarker
internal typealias BaseFinderRenderSnapshot = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderRenderSnapshot

internal typealias BaseFinderScoreGroup = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderScoreGroup
internal typealias BaseFinderScoreWeight = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderScoreWeight
internal typealias BaseFinderScoringWeights = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.BaseFinderScoringWeights
internal typealias ObservedChunkBlocks = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.ObservedChunkBlocks
internal typealias ExpectedTerrainFidelity = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.ExpectedTerrainFidelity
internal typealias ExpectedChunkBlocks = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.ExpectedChunkBlocks
internal typealias ScoreContribution = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.ScoreContribution
internal typealias SeedMismatchScoreAssessment = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.SeedMismatchScoreAssessment
internal typealias SeedMismatchClusterProfile = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model.SeedMismatchClusterProfile
internal typealias SeedMismatchSelection = net.ccbluex.liquidbounce.features.module.modules.world.basefinder.seedmismatch.SeedMismatchSelection
