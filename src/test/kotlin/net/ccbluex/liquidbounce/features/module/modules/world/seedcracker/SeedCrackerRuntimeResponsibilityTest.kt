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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText

class SeedCrackerRuntimeResponsibilityTest {

    @Test
    fun `runtime responsibilities stay split into bounded source files`() {
        val boundedFiles = listOf(
            "SeedCrackerRuntime.kt",
            "RuntimeContracts.kt",
            "RuntimeState.kt",
            "LifecycleCoordinator.kt",
            "ChunkObservation.kt",
            "ChunkSnapshots.kt",
            "SnapshotPipeline.kt",
            "SolverCoordinator.kt",
            "NetherCompletion.kt",
            "ResultPublisher.kt",
            "EvidencePersistence.kt",
            "EvidenceCommands.kt",
            "EvidenceDecisions.kt",
            "RuntimeControls.kt",
            "StatusPresentation.kt",
        ).map(RUNTIME_ROOT::resolve) + listOf(MODULE_SOURCE, HUD_SOURCE, HUD_STATUS_LINES_SOURCE)

        boundedFiles.forEach { source ->
            assertTrue(Files.isRegularFile(source), "Missing responsibility source: $source")
            assertTrue(source.readLines().size <= MAX_SOURCE_LINES, "$source exceeds $MAX_SOURCE_LINES lines")
        }

        val forbiddenSuppressions = listOf(
            "CognitiveComplexMethod",
            "LargeClass",
            "LongMethod",
            "TooManyFunctions",
        )
        val boundedSource = boundedFiles.joinToString(separator = "\n") { it.readText() }
        forbiddenSuppressions.forEach { suppression ->
            assertFalse(suppression in boundedSource, "Runtime boundary must not suppress $suppression")
        }
    }

    @Test
    fun `runtime workflows retain their observable ordering`() {
        assertOrdered("onEnabled", "updateSettings(", "warnAboutParallelSeedCrackerX()", "activateCurrentScope()", "subscribe()")
        assertOrdered("onTick", "refreshSolverResult()", "rescanDirtyChunks()", "refreshStatusProjection()", "publishGuidanceIfChanged()")
        assertOrdered("scanChunk", "collectNetherObservation(", "collectStructureObservations(", "invalidateCandidate()", "persist(scope)", "offerCurrentSnapshot(scope)", "refreshStatusProjection(scope)")
        assertOrdered("offerCurrentSnapshot", "refreshStructureFingerprint(", "refreshNetherFingerprint(", "hasEnoughInformation(", "tracker.offer(scope, snapshot)")
        assertOrdered("solveSnapshot", "solveStructureSnapshot(", "solveNetherSnapshot(")
        assertOrdered("solveNetherSnapshot", "NetherBedrockSolvePlanner.plan(", "NetherBedrockConstraintSolver.startGate(", "heldOut", "sourceChunks", "searchNetherBatches(")
        assertOrdered("completedNetherResult", "distinctBy", "multipleNetherCandidates(", "contradictedNetherResult(", "netherCandidateResult(")
        assertOrdered("refreshSolverResult", "tracker.snapshot()", "applyCandidate(", "persistAppliedResult(", "publishSolveResult(", "continueStructureSearch(")
        assertOrdered("statusPresentation", "statusHeader(", "statusScopeDetails(", "statusNextAction(")
    }

    private fun assertOrdered(functionName: String, vararg markers: String) {
        val body = runtimeSources()
            .firstNotNullOfOrNull { source -> functionBody(source, functionName) }
            ?: error("Missing function $functionName")
        var previous = -1
        markers.forEach { marker ->
            val next = body.indexOf(marker, previous + 1)
            assertTrue(next > previous, "$marker must follow the previous step in $functionName")
            previous = next
        }
    }

    private fun runtimeSources(): Sequence<String> = Files.list(RUNTIME_ROOT).use { paths ->
        paths.filter { it.fileName.toString().endsWith(".kt") }
            .map(Path::readText)
            .toList()
            .asSequence()
    }

    private fun functionBody(source: String, functionName: String): String? {
        val signature = Regex("""fun\s+(?:\w+\.)?${Regex.escape(functionName)}\(""")
            .find(source)
            ?.range
            ?.first
            ?: return null
        val openingBrace = source.indexOf('{', signature)
        if (openingBrace < 0) return null
        var depth = 0
        source.forEachIndexed { index, character ->
            if (index < openingBrace) return@forEachIndexed
            when (character) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(openingBrace, index + 1)
            }
        }
        return null
    }

    private companion object {
        val RUNTIME_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/seedcracker",
        )
        val MODULE_SOURCE: Path = RUNTIME_ROOT.resolveSibling("ModuleSeedCracker.kt")
        val HUD_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/component/components/seedcracker/SeedCrackerHudComponent.kt",
        )
        val HUD_STATUS_LINES_SOURCE: Path = HUD_SOURCE.resolveSibling("StatusLines.kt")
        const val MAX_SOURCE_LINES = 200
    }
}
