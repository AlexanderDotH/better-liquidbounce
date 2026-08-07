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

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchCheckpoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SeedCrackerLedgerTest {

    @Test
    fun `scope paths hash server dimension and generation profile`() = withLedger { ledger, _ ->
        val path = ledger.scopePath(SERVER, DIMENSION, PROFILE)

        assertFalse(path.toString().contains("anarchy.example"))
        assertFalse(path.toString().contains("minecraft"))
        assertFalse(path.toString().contains(ledger.generationProfileKey(PROFILE)))
        assertEquals(64, path.parent.fileName.toString().length)
        assertEquals(134, path.fileName.toString().length)
    }

    @Test
    fun `versioned JSON round trip preserves a profile-bound snapshot and ignores unknown fields`() = withLedger {
            ledger, _ ->
        val observation = StructureObservation(
            id = EvidenceId("igloo:3:-4"),
            scope = CrackScope(SERVER, DIMENSION, PROFILE),
            type = StructureType.IGLOO,
            anchorChunk = ChunkCoordinate(3, -4),
            snapshotHash = 8_765L,
            matchedBlockIds = setOf("minecraft:snow_block", "minecraft:ice"),
            confidence = EvidenceConfidence.STRONG,
            status = EvidenceStatus.ACCEPTED,
            revision = 7L,
        )
        val snapshot = SeedCrackerLedgerSnapshot(
            structureObservations = listOf(observation),
            rejectedEvidenceIds = listOf(EvidenceId("rejected:1")),
        )

        assertTrue(runBlocking { ledger.save(SERVER, DIMENSION, PROFILE, snapshot).await() }.isSuccess)
        val file = ledger.scopePath(SERVER, DIMENSION, PROFILE)
        val document = JsonParser.parseString(Files.readString(file)).asJsonObject
        document.addProperty("futureTopLevelField", true)
        Files.writeString(file, document.toString())

        assertEquals(snapshot, ledger.load(SERVER, DIMENSION, PROFILE))
        assertEquals(SEED_CRACKER_LEDGER_VERSION, document.get("version").asInt)
        assertEquals(ledger.hashScopeKey(SERVER), document.get("serverKeyHash").asString)
        assertEquals(ledger.hashScopeKey(DIMENSION), document.get("dimensionKeyHash").asString)
        assertEquals(ledger.generationProfileKey(PROFILE), document.get("generationProfile").asString)
    }

    @Test
    fun `server dimension and profile scopes remain isolated`() = withLedger { ledger, _ ->
        val otherServer = "other.example:25565"
        val stored = SeedCrackerLedgerSnapshot(
            structureObservations = listOf(
                StructureObservation(
                    id = EvidenceId("pyramid:1:2"),
                    scope = CrackScope(SERVER, DIMENSION, PROFILE),
                    type = StructureType.DESERT_PYRAMID,
                    anchorChunk = ChunkCoordinate(1, 2),
                    snapshotHash = 10L,
                ),
            ),
        )

        runBlocking {
            ledger.save(SERVER, DIMENSION, PROFILE, stored).await()
        }

        assertEquals(stored, ledger.load(SERVER, DIMENSION, PROFILE))
        assertEquals(SeedCrackerLedgerSnapshot(), ledger.load(otherServer, DIMENSION, PROFILE))
        assertEquals(SeedCrackerLedgerSnapshot(), ledger.load(SERVER, "minecraft:the_nether", PROFILE))
    }

    @Test
    fun `complete Nether bit planes survive an atomic restart round trip`() = withLedger { ledger, root ->
        val scope = CrackScope(SERVER, "minecraft:the_nether", PROFILE)
        val snapshot = SeedCrackerLedgerSnapshot(
            netherBedrockObservations = listOf(
                NetherBedrockChunkObservation(
                    id = EvidenceId("nether:0:0"),
                    scope = scope,
                    chunk = ChunkCoordinate(0, 0),
                    revision = 3L,
                    floor = NetherBedrockBitPlane.fromPredicate { x, z -> x == 0 && z == 0 },
                    roof = NetherBedrockBitPlane.fromPredicate { x, z -> x == 15 && z == 15 },
                ),
            ),
            netherSearchCheckpoint = NetherBedrockSearchCheckpoint("selected-evidence", 12_345L),
        )

        assertTrue(ledger.saveImmediatelyBlocking(scope, snapshot).isSuccess)
        val restarted = SeedCrackerLedger(
            rootDirectory = root,
            dispatcher = Dispatchers.Unconfined,
            debounceMillis = 0,
        )

        try {
            assertEquals(snapshot, restarted.load(scope))
        } finally {
            restarted.close()
        }
    }

    @Test
    fun `a ledger copied into another hashed scope fails closed`() = withLedger { ledger, _ ->
        val snapshot = snapshot("source")
        runBlocking { ledger.save(SERVER, DIMENSION, PROFILE, snapshot).await() }

        val foreignPath = ledger.scopePath("foreign.example:25565", DIMENSION, PROFILE)
        Files.createDirectories(foreignPath.parent)
        Files.copy(ledger.scopePath(SERVER, DIMENSION, PROFILE), foreignPath)

        assertEquals(SeedCrackerLedgerSnapshot(), ledger.load("foreign.example:25565", DIMENSION, PROFILE))
    }

    @Test
    fun `malformed unsupported and profile-mismatched documents fail closed without replacement`() = withLedger {
            ledger, _ ->
        val file = ledger.scopePath(SERVER, DIMENSION, PROFILE)
        Files.createDirectories(file.parent)

        Files.writeString(file, "{malformed")
        assertEquals(SeedCrackerLedgerSnapshot(), ledger.load(SERVER, DIMENSION, PROFILE))
        assertEquals("{malformed", Files.readString(file))

        val serverHash = ledger.hashScopeKey(SERVER)
        val dimensionHash = ledger.hashScopeKey(DIMENSION)
        Files.writeString(
            file,
            """
            {
              "version": ${SEED_CRACKER_LEDGER_VERSION + 1},
              "serverKeyHash": "$serverHash",
              "dimensionKeyHash": "$dimensionHash",
              "generationProfile": "${PROFILE.storageKey}",
              "snapshot": {}
            }
            """.trimIndent(),
        )
        assertEquals(SeedCrackerLedgerSnapshot(), ledger.load(SERVER, DIMENSION, PROFILE))
        assertEquals(
            SEED_CRACKER_LEDGER_VERSION + 1,
            JsonParser.parseString(Files.readString(file)).asJsonObject["version"].asInt,
        )

        Files.writeString(
            file,
            """
            {
              "version": $SEED_CRACKER_LEDGER_VERSION,
              "serverKeyHash": "$serverHash",
              "dimensionKeyHash": "$dimensionHash",
              "generationProfile": "other-profile",
              "snapshot": {}
            }
            """.trimIndent(),
        )
        assertEquals(SeedCrackerLedgerSnapshot(), ledger.load(SERVER, DIMENSION, PROFILE))
        val mismatchedDocument = JsonParser.parseString(Files.readString(file)).asJsonObject
        assertEquals("other-profile", mismatchedDocument["generationProfile"].asString)
    }

    @Test
    fun `failed serialization leaves a valid ledger and no temporary file behind`() = withLedger { ledger, root ->
        val existing = SeedCrackerLedgerSnapshot()
        runBlocking { ledger.save(SERVER, DIMENSION, PROFILE, existing).await() }

        val failing = SeedCrackerLedger(
            rootDirectory = root,
            dispatcher = Dispatchers.Unconfined,
            debounceMillis = 0,
            codec = object : SeedCrackerLedgerCodec {
                override fun encode(document: SeedCrackerLedgerDocument): String = error("codec failure")

                override fun decode(json: String): SeedCrackerLedgerDocument = SeedCrackerGsonLedgerCodec.decode(json)
            },
        )

        try {
            val result = runBlocking {
                failing.save(SERVER, DIMENSION, PROFILE, SeedCrackerLedgerSnapshot()).await()
            }
            assertTrue(result.isFailure)
            assertEquals(existing, ledger.load(SERVER, DIMENSION, PROFILE))
            val ledgerPath = ledger.scopePath(SERVER, DIMENSION, PROFILE)
            assertFalse(Files.exists(ledgerPath.resolveSibling("${ledgerPath.fileName}.tmp")))
        } finally {
            failing.close()
        }
    }

    @Test
    fun `clear cancels an unwritten snapshot and removes only the selected scope`() = withLedger(
        debounceMillis = 1_000,
    ) { ledger, _ ->
        val pending = ledger.save(SERVER, DIMENSION, PROFILE, SeedCrackerLedgerSnapshot())

        assertFalse(runBlocking { ledger.clear(SERVER, DIMENSION, PROFILE) })
        assertTrue(pending.isCancelled)
        assertFalse(Files.exists(ledger.scopePath(SERVER, DIMENSION, PROFILE)))
    }

    @Test
    fun `a newer debounced snapshot supersedes an older snapshot from the same scope`() = withLedger(
        debounceMillis = 10,
    ) { ledger, _ ->
        val older = ledger.save(SERVER, DIMENSION, PROFILE, snapshot("older"))
        val newerSnapshot = snapshot("newer")
        val newer = ledger.save(SERVER, DIMENSION, PROFILE, newerSnapshot)

        assertTrue(older.isCancelled)
        assertTrue(runBlocking { newer.await() }.isSuccess)
        assertEquals(newerSnapshot, ledger.load(SERVER, DIMENSION, PROFILE))
    }

    @Test
    fun `clear all removes only current-version ledgers and cancels every pending write`() = withLedger(
        debounceMillis = 1_000,
    ) { ledger, root ->
        assertTrue(
            ledger.saveImmediatelyBlocking(SERVER, DIMENSION, PROFILE, snapshot("existing")).isSuccess,
        )
        val first = ledger.save(SERVER, DIMENSION, PROFILE, snapshot("first"))
        val second = ledger.save("other.example:25565", DIMENSION, PROFILE, snapshotFor("other.example:25565"))
        val futureVersionFile = root.resolve("v${SEED_CRACKER_LEDGER_VERSION + 1}").resolve("future.json")
        Files.createDirectories(futureVersionFile.parent)
        Files.writeString(futureVersionFile, "future")

        assertTrue(ledger.clearAllBlocking() > 0)
        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
        assertFalse(Files.exists(ledger.scopePath(SERVER, DIMENSION, PROFILE)))
        assertFalse(Files.exists(ledger.scopePath("other.example:25565", DIMENSION, PROFILE)))
        assertEquals("future", Files.readString(futureVersionFile))
    }

    private fun withLedger(
        debounceMillis: Long = 0,
        block: (SeedCrackerLedger, Path) -> Unit,
    ) {
        val root = Files.createTempDirectory("seed-cracker-ledger-test")
        val ledger = SeedCrackerLedger(
            rootDirectory = root,
            dispatcher = Dispatchers.Unconfined,
            debounceMillis = debounceMillis,
        )

        try {
            block(ledger, root)
        } finally {
            ledger.close()
            root.toFile().deleteRecursively()
        }
    }

    private fun snapshot(id: String) = SeedCrackerLedgerSnapshot(
        structureObservations = listOf(
            StructureObservation(
                id = EvidenceId(id),
                scope = CrackScope(SERVER, DIMENSION, PROFILE),
                type = StructureType.IGLOO,
                anchorChunk = ChunkCoordinate(0, 0),
                snapshotHash = id.hashCode().toLong(),
            ),
        ),
    )

    private fun snapshotFor(server: String) = SeedCrackerLedgerSnapshot(
        structureObservations = listOf(
            StructureObservation(
                id = EvidenceId("other"),
                scope = CrackScope(server, DIMENSION, PROFILE),
                type = StructureType.IGLOO,
                anchorChunk = ChunkCoordinate(0, 0),
                snapshotHash = 1L,
            ),
        ),
    )

    private companion object {
        const val SERVER = "anarchy.example:25565"
        const val DIMENSION = "minecraft:overworld"
        val PROFILE = GenerationProfile.JAVA_26_2
    }
}
