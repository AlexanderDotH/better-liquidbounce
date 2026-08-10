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

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BaseFinderLedgerTest {

    @Test
    fun `scope paths are hashed and never expose server or dimension names`() = withLedger { ledger, _ ->
        val path = ledger.scopePath("anarchy.example:25565", "minecraft:the_nether")

        assertFalse(path.toString().contains("anarchy.example"))
        assertFalse(path.toString().contains("minecraft"))
        assertEquals(64, path.parent.fileName.toString().length)
        assertEquals(69, path.fileName.toString().length)
    }

    @Test
    fun `versioned JSON round trip preserves a scoped finding and tolerates unknown fields`() = withLedger {
            ledger, _ ->
        val original = finding(
            id = "home",
            serverKeyHash = ledger.hashScopeKey(SERVER),
            confidence = 91,
            bounds = BaseFinderBounds(BaseCoordinate(8, 60, -40), BaseCoordinate(20, 72, -24)),
        )

        assertTrue(runBlocking { ledger.save(SERVER, DIMENSION, listOf(original)).await() }.isSuccess)
        val file = ledger.scopePath(SERVER, DIMENSION)
        val document = JsonParser.parseString(Files.readString(file)).asJsonObject
        document.addProperty("futureTopLevelField", true)
        document.getAsJsonArray("findings").first().asJsonObject.addProperty("futureFindingField", "ignored")
        Files.writeString(file, document.toString())

        assertEquals(listOf(original), ledger.load(SERVER, DIMENSION))
        assertEquals(1, document.get("version").asInt)
    }

    @Test
    fun `version one findings without dynamic bounds remain readable`() = withLedger { ledger, _ ->
        val original = finding(id = "legacy", serverKeyHash = ledger.hashScopeKey(SERVER))
        val file = ledger.scopePath(SERVER, DIMENSION)
        Files.createDirectories(file.parent)
        Files.writeString(file, BaseFinderGsonLedgerCodec.encode(BaseFinderLedgerDocument(findings = listOf(original))))

        assertFalse(
            JsonParser.parseString(Files.readString(file)).asJsonObject
                .getAsJsonArray("findings")
                .first().asJsonObject
                .has("bounds"),
        )
        assertEquals(listOf(original), ledger.load(SERVER, DIMENSION))
        assertEquals(null, ledger.load(SERVER, DIMENSION).single().bounds)
    }

    @Test
    fun `literal version one findings without score details remain readable`() = withLedger { ledger, _ ->
        val file = ledger.scopePath(SERVER, DIMENSION)
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            """
            {
              "version": 1,
              "findings": [{
                "id": "legacy-v1",
                "serverKeyHash": "stored-scope-is-replaced",
                "dimensionKey": "$DIMENSION",
                "anchor": {"x": 12, "y": 64, "z": -34},
                "confidence": 80,
                "tier": "LIKELY",
                "evidence": [{"family": "STORAGE", "score": 24, "keys": ["Storage"]}],
                "firstSeenAtMillis": 100,
                "lastSeenAtMillis": 200,
                "timesSeen": 2
              }]
            }
            """.trimIndent(),
        )

        val loaded = ledger.load(SERVER, DIMENSION).single()

        assertEquals("legacy-v1", loaded.id)
        assertEquals(ledger.hashScopeKey(SERVER), loaded.serverKeyHash)
        assertEquals(null, loaded.evidence.single().contributions)
        assertEquals(null, loaded.scoreBreakdown)
    }

    @Test
    fun `enriched score details survive persistence and JSON export`() = withLedger { ledger, _ ->
        val original = enrichedFinding(ledger.hashScopeKey(SERVER))

        assertTrue(runBlocking { ledger.save(SERVER, DIMENSION, listOf(original)).await() }.isSuccess)
        assertEquals(listOf(original), ledger.load(SERVER, DIMENSION))

        val export = ledger.exportBlocking(SERVER, DIMENSION, BaseFinderExportFormat.JSON)
        val findingJson = JsonParser.parseString(Files.readString(export)).asJsonObject
            .getAsJsonArray("findings")
            .single()
            .asJsonObject
        val contributions = findingJson.getAsJsonArray("evidence")
            .single()
            .asJsonObject
            .getAsJsonArray("contributions")

        assertEquals(6, contributions.size())
        assertEquals(64, contributions.first().asJsonObject["observations"].asInt)
        assertEquals(-21, contributions.last().asJsonObject["score"].asInt)
        assertEquals(89, findingJson.getAsJsonObject("scoreBreakdown")["confidenceCap"].asInt)
    }

    @Test
    fun `enriched findings require exact contribution and breakdown reconciliation`() = withLedger { ledger, _ ->
        val original = enrichedFinding(ledger.hashScopeKey(SERVER))
        val contributionMismatch = original.copy(
            id = "bad-contributions",
            evidence = original.evidence.map { summary ->
                summary.copy(contributions = summary.contributions.orEmpty().dropLast(1))
            },
        )
        val breakdownMismatch = original.copy(
            id = "bad-breakdown",
            scoreBreakdown = BaseScoreBreakdown(
                evidenceSubtotal = 85,
                diversityBonus = 0,
                falsePositivePenalty = 5,
                rawScore = 80,
                confidenceCap = 89,
                finalConfidence = 80,
            ),
        )

        runBlocking {
            ledger.save(SERVER, DIMENSION, listOf(contributionMismatch, breakdownMismatch)).await()
        }

        assertTrue(ledger.load(SERVER, DIMENSION).isEmpty())
    }

    @Test
    fun `server and dimension scopes remain isolated`() = withLedger { ledger, _ ->
        val first = finding("first", ledger.hashScopeKey(SERVER))
        val secondServer = "other.example:25565"
        val second = finding("second", ledger.hashScopeKey(secondServer))

        runBlocking {
            ledger.save(SERVER, DIMENSION, listOf(first)).await()
            ledger.save(secondServer, DIMENSION, listOf(second)).await()
            ledger.save(SERVER, "minecraft:the_end", emptyList()).await()
        }

        assertEquals(listOf(first), ledger.load(SERVER, DIMENSION))
        assertEquals(listOf(second), ledger.load(secondServer, DIMENSION))
        assertTrue(ledger.load(SERVER, "minecraft:the_end").isEmpty())
    }

    @Test
    fun `malformed and unsupported documents fail closed without replacing their contents`() = withLedger {
            ledger, _ ->
        val file = ledger.scopePath(SERVER, DIMENSION)
        Files.createDirectories(file.parent)

        Files.writeString(file, "{malformed")
        assertTrue(ledger.load(SERVER, DIMENSION).isEmpty())
        assertEquals("{malformed", Files.readString(file))

        Files.writeString(file, """{"version":2,"findings":[]}""")
        assertTrue(ledger.load(SERVER, DIMENSION).isEmpty())
        assertEquals(2, JsonParser.parseString(Files.readString(file)).asJsonObject["version"].asInt)
    }

    @Test
    fun `failed serialization preserves the last valid ledger`() = withLedger { ledger, root ->
        val existing = finding("existing", ledger.hashScopeKey(SERVER))
        runBlocking { ledger.save(SERVER, DIMENSION, listOf(existing)).await() }

        val failing = BaseFinderLedger(
            rootDirectory = root,
            dispatcher = Dispatchers.Unconfined,
            debounceMillis = 0,
            codec = object : BaseFinderLedgerCodec {
                override fun encode(document: BaseFinderLedgerDocument): String = error("codec failure")
                override fun decode(json: String): BaseFinderLedgerDocument = BaseFinderGsonLedgerCodec.decode(json)
            },
        )

        try {
            val result = runBlocking {
                failing.save(SERVER, DIMENSION, listOf(finding("replacement", ledger.hashScopeKey(SERVER)))).await()
            }
            assertTrue(result.isFailure)
            assertEquals(listOf(existing), ledger.load(SERVER, DIMENSION))
            val ledgerPath = ledger.scopePath(SERVER, DIMENSION)
            assertFalse(Files.exists(ledgerPath.resolveSibling("${ledgerPath.fileName}.tmp")))
        } finally {
            failing.close()
        }
    }

    @Test
    fun `retention keeps highest confidence then newest two thousand findings`() = withLedger { ledger, _ ->
        val serverHash = ledger.hashScopeKey(SERVER)
        val findings = (0..2_000).map { index ->
            finding(
                id = "finding-$index",
                serverKeyHash = serverHash,
                confidence = if (index == 0) 10 else 80,
                lastSeenAtMillis = 100L + index,
            )
        }

        runBlocking { ledger.save(SERVER, DIMENSION, findings).await() }
        val loaded = ledger.load(SERVER, DIMENSION)

        assertEquals(2_000, loaded.size)
        assertFalse(loaded.any { it.id == "finding-0" })
        assertTrue(loaded.any { it.id == "finding-2000" })
    }

    @Test
    fun `JSON and CSV exports are explicit local snapshots`() = withLedger(clock = { 1_234L }) { ledger, root ->
        val stored = finding("quoted,id", ledger.hashScopeKey(SERVER), evidenceKeys = listOf("Storage", "Signs,Named"))
        runBlocking { ledger.save(SERVER, DIMENSION, listOf(stored)).await() }

        val json = ledger.exportBlocking(SERVER, DIMENSION, BaseFinderExportFormat.JSON)
        val csv = ledger.exportBlocking(SERVER, DIMENSION, BaseFinderExportFormat.CSV)

        assertTrue(json.startsWith(root.resolve("exports")))
        assertTrue(csv.startsWith(root.resolve("exports")))
        assertEquals(1, JsonParser.parseString(Files.readString(json)).asJsonObject["version"].asInt)
        assertTrue(Files.readString(csv).contains("\"quoted,id\""))
        assertTrue(Files.readString(csv).contains("\"STORAGE:24:Storage+Signs,Named\""))
    }

    @Test
    fun `CSV preserves existing columns and appends detailed score fields`() = withLedger { ledger, _ ->
        val stored = enrichedFinding(ledger.hashScopeKey(SERVER))
        runBlocking { ledger.save(SERVER, DIMENSION, listOf(stored)).await() }

        val csv = Files.readString(ledger.exportBlocking(SERVER, DIMENSION, BaseFinderExportFormat.CSV))
        val lines = csv.lineSequence().filter(String::isNotBlank).toList()

        assertEquals(
            "id,serverKeyHash,dimensionKey,x,y,z,confidence,tier,evidence," +
                "firstSeenAtMillis,lastSeenAtMillis,timesSeen," +
                "detailedEvidence,rawScore,modifiers,confidenceCap",
            lines.first(),
        )
        assertTrue(
            lines.single { it != lines.first() }.endsWith(
                ",\"SEED_MISMATCH:89[" +
                    "seed_mismatch.unexpected_solid=+40@64+" +
                    "seed_mismatch.missing_solid=+25@128+" +
                    "seed_mismatch.utility_mismatch=+25@4+" +
                    "seed_mismatch.component_size=+15@196+" +
                    "seed_mismatch.horizontal_spread=+5@12+" +
                    "seed_mismatch.features_cap=-21]\",\"84\"," +
                    "\"diversityBonus=+0|falsePositivePenalty=-5\",\"89\"",
            ),
        )
    }

    @Test
    fun `clear cancels pending writes and reports whether current scope existed`() = withLedger(
        debounceMillis = 1_000,
    ) { ledger, root ->
        val pending = ledger.save(SERVER, DIMENSION, listOf(finding("pending", ledger.hashScopeKey(SERVER))))

        assertFalse(runBlocking { ledger.clear(SERVER, DIMENSION) })
        assertTrue(pending.isCancelled)
        assertFalse(Files.exists(ledger.scopePath(SERVER, DIMENSION)))

        val immediate = BaseFinderLedger(root, Dispatchers.Unconfined, debounceMillis = 0)
        try {
            runBlocking { immediate.save(SERVER, DIMENSION, emptyList()).await() }
            assertTrue(immediate.clearBlocking(SERVER, DIMENSION))
            assertFalse(immediate.clearBlocking(SERVER, DIMENSION))
        } finally {
            immediate.close()
        }
    }

    private fun finding(
        id: String,
        serverKeyHash: String,
        confidence: Int = 80,
        lastSeenAtMillis: Long = 200L,
        evidenceKeys: List<String> = listOf("Storage"),
        bounds: BaseFinderBounds? = null,
    ) = BaseFinding(
        id = id,
        serverKeyHash = serverKeyHash,
        dimensionKey = DIMENSION,
        anchor = BaseCoordinate(12, 64, -34),
        confidence = confidence,
        tier = ConfidenceTier.from(confidence),
        evidence = listOf(EvidenceSummary(BaseSignalFamily.STORAGE, 24, evidenceKeys)),
        firstSeenAtMillis = 100L,
        lastSeenAtMillis = lastSeenAtMillis,
        timesSeen = 2,
        bounds = bounds,
    )

    private fun enrichedFinding(serverKeyHash: String) = BaseFinding(
        id = "seed-evidence",
        serverKeyHash = serverKeyHash,
        dimensionKey = DIMENSION,
        anchor = BaseCoordinate(12, 64, -34),
        confidence = 84,
        tier = ConfidenceTier.LIKELY,
        evidence = listOf(
            EvidenceSummary(
                family = BaseSignalFamily.SEED_MISMATCH,
                score = 89,
                keys = listOf("seed_mismatch"),
                contributions = listOf(
                    ScoreContribution("seed_mismatch.unexpected_solid", 40, 64),
                    ScoreContribution("seed_mismatch.missing_solid", 25, 128),
                    ScoreContribution("seed_mismatch.utility_mismatch", 25, 4),
                    ScoreContribution("seed_mismatch.component_size", 15, 196),
                    ScoreContribution("seed_mismatch.horizontal_spread", 5, 12),
                    ScoreContribution("seed_mismatch.features_cap", -21),
                ),
            ),
        ),
        firstSeenAtMillis = 100L,
        lastSeenAtMillis = 200L,
        timesSeen = 2,
        scoreBreakdown = BaseScoreBreakdown(
            evidenceSubtotal = 89,
            diversityBonus = 0,
            falsePositivePenalty = 5,
            rawScore = 84,
            confidenceCap = 89,
            finalConfidence = 84,
        ),
    )

    private fun withLedger(
        debounceMillis: Long = 0,
        clock: () -> Long = { 99L },
        block: (BaseFinderLedger, Path) -> Unit,
    ) {
        val root = Files.createTempDirectory("basefinder-ledger-test")
        val ledger = BaseFinderLedger(
            rootDirectory = root,
            dispatcher = Dispatchers.Unconfined,
            debounceMillis = debounceMillis,
            clock = clock,
        )

        try {
            block(ledger, root)
        } finally {
            ledger.close()
            root.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val SERVER = "anarchy.example:25565"
        const val DIMENSION = "minecraft:overworld"
    }
}
