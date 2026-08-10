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

package net.ccbluex.liquidbounce.features.command.commands.module

import net.ccbluex.liquidbounce.features.command.Parameter
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseCoordinate
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseFinding
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseScoreBreakdown
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.BaseSignalFamily
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ConfidenceTier
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.EvidenceSummary
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ScoreContribution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandBaseFinderTest {

    @Test
    fun `basefinder exposes list report export and clear management commands`() {
        val command = CommandBaseFinder.createCommand()

        assertEquals("basefinder", command.name)
        assertFalse(command.executable)
        assertEquals(listOf("list", "report", "export", "clear"), command.subcommands.map { it.name })
    }

    @Test
    fun `list accepts one optional page`() {
        val list = CommandBaseFinder.createCommand().subcommands.single { it.name == "list" }

        assertTrue(list.executable)
        assertEquals(1, list.parameters.size)
        assertEquals("page", list.parameters.single().name)
        assertFalse(list.parameters.single().required)
        assertTrue(list.requiresIngame)
        val verifier = requireNotNull(list.parameters.single().verifier)
        assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, verifier.verifyAndParse("1"))
        assertInstanceOf(Parameter.Verificator.Result.Error::class.java, verifier.verifyAndParse("0"))
    }

    @Test
    fun `export format is an explicit subcommand`() {
        val export = CommandBaseFinder.createCommand().subcommands.single { it.name == "export" }

        assertFalse(export.executable)
        assertEquals(listOf("json", "csv"), export.subcommands.map { it.name })
        assertTrue(export.subcommands.all { it.executable && it.parameters.isEmpty() && it.requiresIngame })
    }

    @Test
    fun `report requires an identifier and offers current scope autocomplete`() {
        val report = CommandBaseFinder.createCommand().subcommands.single { it.name == "report" }
        val identifier = report.parameters.single()
        val verifier = requireNotNull(identifier.verifier)

        assertTrue(report.executable)
        assertTrue(report.requiresIngame)
        assertEquals("id", identifier.name)
        assertTrue(identifier.required)
        assertNotNull(identifier.autocompletionHandler)
        assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, verifier.verifyAndParse("018f0abc"))
    }

    @Test
    fun `finding identifier is displayed as exactly its first eight characters`() {
        assertEquals("018f0abc", baseFinderFindingPrefix("018f0abc-1234-5678"))
    }

    @Test
    fun `autocomplete uses prefixes unless a collision requires full identifiers`() {
        val suggestions = baseFinderFindingSuggestions(
            listOf(
                finding("bbbbbbbb-one"),
                finding("aaaaaaaa-one"),
                finding("aaaaaaaa-two"),
                finding("cccccccc-one"),
            )
        )

        assertEquals(
            listOf("aaaaaaaa-one", "aaaaaaaa-two", "bbbbbbbb", "cccccccc"),
            suggestions,
        )
    }

    @Test
    fun `lookup resolves an exact identifier before its ambiguous prefix`() {
        val exact = finding("aaaaaaaa-one")
        val other = finding("aaaaaaaa-two")

        val result = resolveBaseFinderFinding(listOf(other, exact), "AAAAAAAA-ONE")

        assertEquals(BaseFinderLookupResult.Found(exact), result)
    }

    @Test
    fun `lookup resolves a unique prefix and sorts ambiguous matches`() {
        val first = finding("aaaaaaaa-one")
        val second = finding("aaaaaaaa-two")
        val unique = finding("bbbbbbbb-one")

        assertEquals(
            BaseFinderLookupResult.Found(unique),
            resolveBaseFinderFinding(listOf(first, unique, second), "bbbb"),
        )
        assertEquals(
            BaseFinderLookupResult.Ambiguous(listOf(first, second)),
            resolveBaseFinderFinding(listOf(second, unique, first), "aaaaaaaa"),
        )
        assertEquals(
            BaseFinderLookupResult.NotFound,
            resolveBaseFinderFinding(listOf(first, second, unique), "missing"),
        )
        assertEquals(BaseFinderLookupResult.NotFound, resolveBaseFinderFinding(listOf(first), " "))
    }

    @Test
    fun `top list evidence is scored and deterministically ordered`() {
        val finding = finding(
            id = "aaaaaaaa-one",
            evidence = listOf(
                evidence(BaseSignalFamily.STORAGE, 12),
                evidence(BaseSignalFamily.SEED_MISMATCH, 85),
                evidence(BaseSignalFamily.AUTOMATION, 12),
            ),
        )

        assertEquals(
            listOf(
                evidence(BaseSignalFamily.SEED_MISMATCH, 85),
                evidence(BaseSignalFamily.AUTOMATION, 12),
            ),
            finding.topBaseFinderEvidence(2),
        )
    }

    @Test
    fun `report entries include every family contribution and score modifier`() {
        val seedContribution = ScoreContribution("seed_mismatch.unexpected_solid", 40, 143)
        val storageContribution = ScoreContribution("storage.chest", 12, 2)
        val seed = evidence(BaseSignalFamily.SEED_MISMATCH, 85, listOf(seedContribution))
        val storage = evidence(BaseSignalFamily.STORAGE, 12, listOf(storageContribution))
        val breakdown = BaseScoreBreakdown(
            evidenceSubtotal = 97,
            diversityBonus = 0,
            falsePositivePenalty = 5,
            rawScore = 92,
            confidenceCap = 100,
            finalConfidence = 92,
        )
        val finding = finding(
            id = "aaaaaaaa-one",
            confidence = 92,
            tier = ConfidenceTier.STRONG,
            evidence = listOf(storage, seed),
            scoreBreakdown = breakdown,
        )

        assertEquals(
            listOf(
                BaseFinderReportEntry.Family(seed),
                BaseFinderReportEntry.Contribution(BaseSignalFamily.SEED_MISMATCH, seedContribution),
                BaseFinderReportEntry.Family(storage),
                BaseFinderReportEntry.Contribution(BaseSignalFamily.STORAGE, storageContribution),
                BaseFinderReportEntry.Breakdown(breakdown),
            ),
            finding.baseFinderReportEntries(),
        )
    }

    @Test
    fun `legacy report keeps family subtotals and marks detailed scoring unavailable`() {
        val seed = evidence(BaseSignalFamily.SEED_MISMATCH, 24)
        val legacy = finding("legacy-finding", evidence = listOf(seed))

        assertEquals(
            listOf(
                BaseFinderReportEntry.Family(seed),
                BaseFinderReportEntry.DetailsUnavailable,
            ),
            legacy.baseFinderReportEntries(),
        )
    }

    @Test
    fun `clear current requires the exact confirm token`() {
        val clear = CommandBaseFinder.createCommand().subcommands.single { it.name == "clear" }
        val current = clear.subcommands.single { it.name == "current" }
        val confirm = current.parameters.single()
        val verifier = requireNotNull(confirm.verifier)

        assertFalse(clear.executable)
        assertTrue(current.executable)
        assertTrue(current.requiresIngame)
        assertTrue(confirm.required)
        assertEquals("confirm", confirm.name)
        assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, verifier.verifyAndParse("confirm"))
        assertInstanceOf(Parameter.Verificator.Result.Error::class.java, verifier.verifyAndParse("CONFIRM"))
        assertInstanceOf(Parameter.Verificator.Result.Error::class.java, verifier.verifyAndParse("yes"))
    }

    @Test
    fun `clear cache is an in game command without confirmation`() {
        val clear = CommandBaseFinder.createCommand().subcommands.single { it.name == "clear" }
        val cache = clear.subcommands.single { it.name == "cache" }

        assertEquals(listOf("current", "cache"), clear.subcommands.map { it.name })
        assertTrue(cache.executable)
        assertTrue(cache.requiresIngame)
        assertTrue(cache.parameters.isEmpty())
    }

    private fun finding(
        id: String,
        confidence: Int = 60,
        tier: ConfidenceTier = ConfidenceTier.POSSIBLE,
        evidence: List<EvidenceSummary> = emptyList(),
        scoreBreakdown: BaseScoreBreakdown? = null,
    ) = BaseFinding(
        id = id,
        serverKeyHash = "server",
        dimensionKey = "minecraft:overworld",
        anchor = BaseCoordinate(10, 64, 20),
        confidence = confidence,
        tier = tier,
        evidence = evidence,
        firstSeenAtMillis = 1L,
        lastSeenAtMillis = 2L,
        timesSeen = 1,
        scoreBreakdown = scoreBreakdown,
    )

    private fun evidence(
        family: BaseSignalFamily,
        score: Int,
        contributions: List<ScoreContribution>? = null,
    ) = EvidenceSummary(
        family = family,
        score = score,
        keys = listOf(family.name.lowercase()),
        contributions = contributions,
    )

}
