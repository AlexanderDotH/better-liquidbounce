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

package net.ccbluex.liquidbounce.buildsrc.quality

import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetBaseline
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetEntry
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetMode
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetStatus
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceQualityGateTest {

    @Test
    fun `gate composes configured analyzers ratchet and fixed reports`() {
        val root = Files.createTempDirectory("source-quality-gate")
        val source = root.resolve("src/main/kotlin/net/example/Large.kt")
        source.parent.createDirectories()
        source.writeText("package net.example\n" + (1..200).joinToString("\n") { "val line$it = $it" })
        val hygiene = root.resolve("config/source-hygiene.json").also { it.parent.createDirectories(); it.writeText(HYGIENE) }
        val architecture = root.resolve("config/source-architecture.json").also { it.writeText(ARCHITECTURE) }
        val ratchet = root.resolve("config/source-ratchet.json")
        val reports = root.resolve("build/reports/source-hygiene")
        writeEmptyRatchet(ratchet)

        val result = SourceQualityGate.run(
            SourceQualityRequest(root, hygiene, architecture, ratchet, reports, emptySet()),
        )

        assertTrue(result.hasBlockingFindings)
        assertEquals(listOf("LB-HYG-001"), result.assessments.map { it.finding.ruleId })
        assertTrue(Files.exists(reports.resolve("source-quality.md")))
        assertTrue("LB-HYG-001" in Files.readString(reports.resolve("source-quality.sarif")))
    }

    @Test
    fun `clean compare succeeds after ratchet file is deleted`() {
        val fixture = fixture("package net.example\nclass Small")

        val result = SourceQualityGate.run(fixture.request())

        assertTrue(!result.hasBlockingFindings)
        assertTrue(!Files.exists(fixture.ratchet))
    }

    @Test
    fun `resolved forbidden suppression is reported as removable ratchet debt`() {
        val fixture = fixture("package net.example\nclass Small")
        val fingerprint = "LB-HYG-002|src/main/kotlin/net/example/Sample.kt|TooManyFunctions"
        val baseline = RatchetBaseline(
            schemaVersion = 1,
            capturedRevision = "legacy",
            entries = listOf(
                RatchetEntry(
                    fingerprint = fingerprint,
                    ruleId = "LB-HYG-002",
                    path = "src/main/kotlin/net/example/Sample.kt",
                    subject = "TooManyFunctions",
                    maximum = 1,
                ),
            ),
        )
        net.ccbluex.liquidbounce.buildsrc.quality.config.RatchetJson.write(fixture.ratchet, baseline)

        val result = SourceQualityGate.run(fixture.request())

        val resolved = result.assessments.single()
        assertEquals(RatchetStatus.REDUCED, resolved.status)
        assertEquals(0, resolved.finding.measuredValue)
        assertTrue("remove" in resolved.finding.recommendation.lowercase())
        assertTrue(fingerprint in Files.readString(fixture.root.resolve("build/reports/source-hygiene/source-quality.json")))
        val markdown = Files.readString(fixture.root.resolve("build/reports/source-hygiene/source-quality.md"))
        assertTrue("TooManyFunctions" in markdown)
        assertTrue("Remove this resolved entry" in markdown)
        assertTrue(fingerprint in Files.readString(fixture.root.resolve("build/reports/source-hygiene/source-quality.sarif")))
    }

    @Test
    fun `write initializes a deterministic ratchet and compare reuses it`() {
        val fixture = fixture("package net.example\n" + (1..200).joinToString("\n") { "val line$it = $it" })

        val written = SourceQualityGate.run(
            fixture.request(ratchetMode = RatchetMode.WRITE, capturedRevision = "abc123"),
        )
        val firstBaseline = Files.readString(fixture.ratchet)
        val compared = SourceQualityGate.run(fixture.request())

        assertTrue(!written.hasBlockingFindings)
        assertTrue(!compared.hasBlockingFindings)
        assertTrue("abc123" in firstBaseline)
        assertEquals(firstBaseline, Files.readString(fixture.ratchet))
    }

    @Test
    fun `write rejects an increase to an existing ratchet`() {
        val fixture = fixture("package net.example\n" + (1..200).joinToString("\n") { "val line$it = $it" })
        SourceQualityGate.run(fixture.request(ratchetMode = RatchetMode.WRITE, capturedRevision = "first"))
        fixture.source.writeText(
            "package net.example\n" + (1..201).joinToString("\n") { "val line$it = $it" },
        )

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            SourceQualityGate.run(fixture.request(ratchetMode = RatchetMode.WRITE, capturedRevision = "second"))
        }
    }

    @Test
    fun `prune monotonically removes resolved hygiene debt while blockers remain`() {
        val fixture = fixture(oversizedSource("Sample", 200))
        val legacy = fixture.source.resolveSibling("Legacy.kt").also { it.writeText(oversizedSource("Legacy", 200)) }
        val tolerated = fixture.source.resolveSibling("Tolerated.kt").also {
            it.writeText(oversizedSource("Tolerated", 200))
        }
        fixture.source.resolveSibling("New.kt").writeText(oversizedSource("New", 200))
        val reducedEntry = effectiveLineEntry(fixture.root, fixture.source, maximum = 250)
        val blockingEntry = effectiveLineEntry(fixture.root, legacy, maximum = 200)
        val toleratedEntry = effectiveLineEntry(fixture.root, tolerated, maximum = 202)
        val resolvedSuppression = RatchetEntry(
            fingerprint = "LB-HYG-002|src/main/kotlin/net/example/Sample.kt|TooManyFunctions",
            ruleId = "LB-HYG-002",
            path = "src/main/kotlin/net/example/Sample.kt",
            subject = "TooManyFunctions",
            maximum = 1,
        )
        val retainedArchitecture = RatchetEntry(
            fingerprint = "LB-ARCH-002|net.example.legacy",
            ruleId = "LB-ARCH-002",
            path = "src/main/kotlin/net/example/Legacy.kt",
            subject = "cycle:net.example.legacy",
            maximum = 2,
            targets = setOf("net.example.first", "net.example.second"),
        )
        net.ccbluex.liquidbounce.buildsrc.quality.config.RatchetJson.write(
            fixture.ratchet,
            RatchetBaseline(
                schemaVersion = 1,
                capturedRevision = "legacy",
                entries = listOf(
                    reducedEntry,
                    blockingEntry,
                    toleratedEntry,
                    resolvedSuppression,
                    retainedArchitecture,
                ),
            ),
        )

        val result = SourceQualityGate.run(fixture.request(ratchetMode = RatchetMode.parse("prune")))
        val firstJson = Files.readString(fixture.ratchet)
        val pruned = checkNotNull(
            net.ccbluex.liquidbounce.buildsrc.quality.config.RatchetJson.readOrNull(fixture.ratchet),
        )

        assertTrue(result.hasBlockingFindings)
        assertEquals(202, pruned.byFingerprint.getValue(reducedEntry.fingerprint).maximum)
        assertEquals(200, pruned.byFingerprint.getValue(blockingEntry.fingerprint).maximum)
        assertEquals(202, pruned.byFingerprint.getValue(toleratedEntry.fingerprint).maximum)
        assertEquals(retainedArchitecture, pruned.byFingerprint.getValue(retainedArchitecture.fingerprint))
        assertTrue(resolvedSuppression.fingerprint !in pruned.byFingerprint)
        assertTrue(pruned.entries.none { "New.kt" in it.path })

        SourceQualityGate.run(fixture.request(ratchetMode = RatchetMode.parse("prune")))
        assertEquals(firstJson, Files.readString(fixture.ratchet))
    }

    @Test
    fun `compare credits a lower configured hygiene ceiling proven by the reference baseline`() {
        val fixture = fixture(oversizedSource("Sample", 200))
        val path = fixture.root.relativize(fixture.source).toString().replace('\\', '/')
        val referenceEntry = effectiveLineEntry(fixture.root, fixture.source, maximum = 250)
        val loweredEntry = referenceEntry.copy(maximum = 202)
        val reference = RatchetBaseline(1, "reference", listOf(referenceEntry))
        val lowered = RatchetBaseline(1, "working", listOf(loweredEntry))
        net.ccbluex.liquidbounce.buildsrc.quality.config.RatchetJson.write(fixture.ratchet, lowered)

        val reduced = SourceQualityGate.run(
            fixture.request(touchedPaths = setOf(path), referenceBaseline = reference),
        )

        assertTrue(!reduced.hasBlockingFindings)
        assertEquals(RatchetStatus.REDUCED, reduced.assessments.single().status)
        assertTrue("reference" in reduced.assessments.single().reason)

        val unchanged = SourceQualityGate.run(
            fixture.request(touchedPaths = setOf(path), referenceBaseline = lowered),
        )
        assertTrue(unchanged.hasBlockingFindings)
        assertEquals(RatchetStatus.BLOCKING, unchanged.assessments.single().status)

        val increased = lowered.copy(entries = listOf(loweredEntry.copy(maximum = 203)))
        net.ccbluex.liquidbounce.buildsrc.quality.config.RatchetJson.write(fixture.ratchet, increased)
        val rejectedIncrease = SourceQualityGate.run(
            fixture.request(touchedPaths = setOf(path), referenceBaseline = lowered),
        )
        assertTrue(rejectedIncrease.hasBlockingFindings)
        assertTrue(rejectedIncrease.baselineFindings.any { it.ruleId == "LB-RATCHET-002" })
    }

    private fun fixture(content: String): GateFixture {
        val root = Files.createTempDirectory("source-quality-gate")
        val source = root.resolve("src/main/kotlin/net/example/Sample.kt")
        source.parent.createDirectories()
        source.writeText(content)
        val hygiene = root.resolve("config/source-hygiene.json").also { it.parent.createDirectories(); it.writeText(HYGIENE) }
        val architecture = root.resolve("config/source-architecture.json").also { it.writeText(ARCHITECTURE) }
        return GateFixture(root, source, hygiene, architecture, root.resolve("config/source-ratchet.json"))
    }

    private data class GateFixture(
        val root: java.nio.file.Path,
        val source: java.nio.file.Path,
        val hygiene: java.nio.file.Path,
        val architecture: java.nio.file.Path,
        val ratchet: java.nio.file.Path,
    ) {
        fun request(
            ratchetMode: RatchetMode = RatchetMode.COMPARE,
            capturedRevision: String = "working-tree",
            touchedPaths: Set<String> = emptySet(),
            referenceBaseline: RatchetBaseline? = null,
        ) = SourceQualityRequest(
            repositoryRoot = root,
            hygieneConfiguration = hygiene,
            architectureConfiguration = architecture,
            ratchetConfiguration = ratchet,
            reportDirectory = root.resolve("build/reports/source-hygiene"),
            touchedPaths = touchedPaths,
            referenceBaseline = referenceBaseline,
            ratchetMode = ratchetMode,
            capturedRevision = capturedRevision,
        )
    }

    private fun writeEmptyRatchet(path: java.nio.file.Path) {
        net.ccbluex.liquidbounce.buildsrc.quality.config.RatchetJson.write(path, RatchetBaseline(1, "test", emptyList()))
    }

    private fun oversizedSource(name: String, valueLines: Int): String =
        "package net.example\nclass $name\n" + (1..valueLines).joinToString("\n") { "val line$it = $it" }

    private fun effectiveLineEntry(
        root: java.nio.file.Path,
        source: java.nio.file.Path,
        maximum: Int,
    ): RatchetEntry {
        val path = root.relativize(source).toString().replace('\\', '/')
        return RatchetEntry(
            fingerprint = "LB-HYG-001|$path|effective-lines",
            ruleId = "LB-HYG-001",
            path = path,
            subject = "effective-lines",
            maximum = maximum,
        )
    }

    private companion object {
        val CONFIG_ROOT: java.nio.file.Path = java.nio.file.Path.of("..", "config").toAbsolutePath().normalize()
        val HYGIENE: String = Files.readString(CONFIG_ROOT.resolve("source-hygiene.json"))
        val ARCHITECTURE: String = Files.readString(CONFIG_ROOT.resolve("source-architecture.json"))
    }
}
