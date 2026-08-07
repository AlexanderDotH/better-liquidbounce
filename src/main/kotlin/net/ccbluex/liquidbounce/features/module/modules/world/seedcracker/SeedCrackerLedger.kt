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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockPrefixRange
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchCheckpoint
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.Comparator

// V1 structure observations used loose per-chunk signatures. Keep those files untouched and fail closed instead
// of letting their speculative anchors participate in the stricter collection policy.
internal const val SEED_CRACKER_LEDGER_VERSION = 2

/**
 * The persisted portion of a seed cracking session. Runtime guidance and jobs deliberately do not
 * belong here: they are rebuilt after load from these immutable observations.
 */
internal data class SeedCrackerLedgerSnapshot(
    val structureObservations: List<StructureObservation> = emptyList(),
    val netherBedrockObservations: List<NetherBedrockChunkObservation> = emptyList(),
    val rejectedEvidenceIds: List<EvidenceId> = emptyList(),
    val candidate: SeedCandidate? = null,
    val netherSearchCheckpoint: NetherBedrockSearchCheckpoint? = null,
)

internal data class SeedCrackerLedgerDocument(
    val version: Int = SEED_CRACKER_LEDGER_VERSION,
    val serverKeyHash: String = "",
    val dimensionKeyHash: String = "",
    val generationProfile: String = "",
    val snapshot: SeedCrackerLedgerSnapshot = SeedCrackerLedgerSnapshot(),
)

internal interface SeedCrackerLedgerCodec {
    fun encode(document: SeedCrackerLedgerDocument): String

    fun decode(json: String): SeedCrackerLedgerDocument
}

internal object SeedCrackerGsonLedgerCodec : SeedCrackerLedgerCodec {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun encode(document: SeedCrackerLedgerDocument): String = gson.toJson(gson.toJsonTree(document))

    override fun decode(json: String): SeedCrackerLedgerDocument {
        val tree = JsonParser.parseString(json).asJsonObject
        require(tree.has("version")) { "Seed cracker ledger has no version" }
        require(tree.has("serverKeyHash")) { "Seed cracker ledger has no server hash" }
        require(tree.has("dimensionKeyHash")) { "Seed cracker ledger has no dimension hash" }
        require(tree.has("generationProfile")) { "Seed cracker ledger has no generation profile" }
        require(tree.has("snapshot")) { "Seed cracker ledger has no snapshot" }
        require(tree["snapshot"].isJsonObject) { "Seed cracker ledger snapshot must be an object" }
        return gson.fromJson(tree, SeedCrackerLedgerDocument::class.java)
    }
}

/**
 * Atomic, versioned persistence for a single server/dimension/generation-profile scope.
 *
 * The ledger hashes all scope keys before deriving a path. It never parses or replaces malformed,
 * unsupported, or profile-mismatched files. Writes retain only frozen collection snapshots and
 * are superseded per scope, so an older asynchronous write cannot overwrite new evidence.
 */
@Suppress("TooManyFunctions")
internal class SeedCrackerLedger(
    private val rootDirectory: Path = ConfigSystem.rootFolder.toPath().resolve("seed-cracker"),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val codec: SeedCrackerLedgerCodec = SeedCrackerGsonLedgerCodec,
) : AutoCloseable {

    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stateLock = Any()
    private val pendingWrites = mutableMapOf<LedgerScope, Deferred<Result<Path>>>()
    private val fileLocks = mutableMapOf<LedgerScope, Any>()
    private val scopeRevisions = mutableMapOf<LedgerScope, Long>()
    private val allFilesLock = Any()

    init {
        require(debounceMillis >= 0L) { "debounceMillis must not be negative" }
    }

    fun hashScopeKey(key: String): String = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    fun generationProfileKey(profile: GenerationProfile): String = profile.storageKey

    fun scopePath(
        serverKey: String,
        dimensionKey: String,
        generationProfile: GenerationProfile,
    ): Path {
        val scope = scope(serverKey, dimensionKey, generationProfile)
        return path(scope)
    }

    fun scopePath(scope: CrackScope): Path = scopePath(
        serverKey = scope.serverFingerprint,
        dimensionKey = scope.dimensionKey,
        generationProfile = scope.generationProfile,
    )

    /** Loads one exact scope. Any invalid document is ignored without changing it. */
    fun load(
        serverKey: String,
        dimensionKey: String,
        generationProfile: GenerationProfile,
    ): SeedCrackerLedgerSnapshot {
        val expectedScope = CrackScope(serverKey, dimensionKey, generationProfile)
        val scope = scope(serverKey, dimensionKey, generationProfile)
        return synchronized(fileLock(scope)) {
            read(path(scope), scope, expectedScope)
        }
    }

    fun load(scope: CrackScope): SeedCrackerLedgerSnapshot = load(
        serverKey = scope.serverFingerprint,
        dimensionKey = scope.dimensionKey,
        generationProfile = scope.generationProfile,
    )

    /** Debounces an immutable snapshot and atomically replaces an older committed file. */
    fun save(
        serverKey: String,
        dimensionKey: String,
        generationProfile: GenerationProfile,
        snapshot: SeedCrackerLedgerSnapshot,
    ): Deferred<Result<Path>> {
        val expectedScope = CrackScope(serverKey, dimensionKey, generationProfile)
        val scope = scope(serverKey, dimensionKey, generationProfile)
        val frozenSnapshot = freeze(snapshot, expectedScope)
        lateinit var pendingWrite: Deferred<Result<Path>>

        synchronized(stateLock) {
            pendingWrites.remove(scope)?.cancel()
            val revision = nextRevision(scope)
            pendingWrite = coroutineScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                if (debounceMillis > 0L) {
                    delay(debounceMillis)
                }
                runCatching { write(scope, revision, frozenSnapshot) }
            }
            pendingWrites[scope] = pendingWrite
            pendingWrite.invokeOnCompletion {
                synchronized(stateLock) {
                    if (pendingWrites[scope] === pendingWrite) {
                        pendingWrites.remove(scope)
                    }
                }
            }
            pendingWrite.start()
        }

        return pendingWrite
    }

    fun save(
        scope: CrackScope,
        snapshot: SeedCrackerLedgerSnapshot,
    ): Deferred<Result<Path>> = save(
        serverKey = scope.serverFingerprint,
        dimensionKey = scope.dimensionKey,
        generationProfile = scope.generationProfile,
        snapshot = snapshot,
    )

    suspend fun saveImmediately(
        serverKey: String,
        dimensionKey: String,
        generationProfile: GenerationProfile,
        snapshot: SeedCrackerLedgerSnapshot,
    ): Result<Path> {
        val expectedScope = CrackScope(serverKey, dimensionKey, generationProfile)
        val scope = scope(serverKey, dimensionKey, generationProfile)
        val revision = synchronized(stateLock) {
            pendingWrites.remove(scope)?.cancel()
            nextRevision(scope)
        }
        return withContext(dispatcher) {
            runCatching { write(scope, revision, freeze(snapshot, expectedScope)) }
        }
    }

    suspend fun saveImmediately(
        scope: CrackScope,
        snapshot: SeedCrackerLedgerSnapshot,
    ): Result<Path> = saveImmediately(
        serverKey = scope.serverFingerprint,
        dimensionKey = scope.dimensionKey,
        generationProfile = scope.generationProfile,
        snapshot = snapshot,
    )

    fun saveImmediatelyBlocking(
        serverKey: String,
        dimensionKey: String,
        generationProfile: GenerationProfile,
        snapshot: SeedCrackerLedgerSnapshot,
    ): Result<Path> = runBlocking {
        saveImmediately(serverKey, dimensionKey, generationProfile, snapshot)
    }

    fun saveImmediatelyBlocking(
        scope: CrackScope,
        snapshot: SeedCrackerLedgerSnapshot,
    ): Result<Path> = runBlocking { saveImmediately(scope, snapshot) }

    suspend fun flush() {
        while (true) {
            val pending = synchronized(stateLock) { pendingWrites.values.toList() }
            if (pending.isEmpty()) {
                return
            }
            pending.joinAll()
        }
    }

    fun flushBlocking() = runBlocking { flush() }

    /** Cancels pending work for one scope before deleting only that scope's ledger file. */
    suspend fun clear(
        serverKey: String,
        dimensionKey: String,
        generationProfile: GenerationProfile,
    ): Boolean {
        val scope = scope(serverKey, dimensionKey, generationProfile)
        synchronized(stateLock) {
            pendingWrites.remove(scope)?.cancel()
            nextRevision(scope)
        }
        return withContext(dispatcher) {
            synchronized(allFilesLock) {
                synchronized(fileLock(scope)) {
                    Files.deleteIfExists(path(scope))
                }
            }
        }
    }

    suspend fun clear(scope: CrackScope): Boolean = clear(
        serverKey = scope.serverFingerprint,
        dimensionKey = scope.dimensionKey,
        generationProfile = scope.generationProfile,
    )

    fun clearBlocking(
        serverKey: String,
        dimensionKey: String,
        generationProfile: GenerationProfile,
    ): Boolean = runBlocking { clear(serverKey, dimensionKey, generationProfile) }

    fun clearBlocking(scope: CrackScope): Boolean = runBlocking { clear(scope) }

    /**
     * Deletes all ledgers for this on-disk format while leaving future format versions untouched.
     * Command handling requires an explicit confirmation before invoking this operation.
     */
    suspend fun clearAll(): Int {
        synchronized(stateLock) {
            pendingWrites.values.toList().forEach { it.cancel() }
            pendingWrites.clear()
            scopeRevisions.keys.toList().forEach(::nextRevision)
        }

        return withContext(dispatcher) {
            val versionDirectory = versionDirectory()
            if (!Files.exists(versionDirectory)) {
                return@withContext 0
            }

            synchronized(allFilesLock) {
                var deleted = 0
                Files.walk(versionDirectory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { path ->
                        if (Files.deleteIfExists(path)) {
                            deleted++
                        }
                    }
                }
                deleted
            }
        }
    }

    fun clearAllBlocking(): Int = runBlocking { clearAll() }

    override fun close() {
        synchronized(stateLock) {
            pendingWrites.values.toList().forEach { it.cancel() }
            pendingWrites.clear()
        }
        coroutineScope.cancel()
    }

    private fun write(
        scope: LedgerScope,
        revision: Long,
        snapshot: SeedCrackerLedgerSnapshot,
    ): Path {
        val destination = path(scope)
        val temporary = destination.resolveSibling("${destination.fileName}.tmp")
        val encoded = codec.encode(
            SeedCrackerLedgerDocument(
                serverKeyHash = scope.serverKeyHash,
                dimensionKeyHash = scope.dimensionKeyHash,
                generationProfile = scope.generationProfileKey,
                snapshot = snapshot,
            ),
        )

        return synchronized(allFilesLock) {
            synchronized(fileLock(scope)) {
                ensureCurrent(scope, revision)
                Files.createDirectories(destination.parent)
                try {
                    Files.writeString(temporary, encoded, StandardCharsets.UTF_8)
                    ensureCurrent(scope, revision)
                    moveAtomically(temporary, destination)
                    destination
                } catch (throwable: Throwable) {
                    Files.deleteIfExists(temporary)
                    throw throwable
                }
            }
        }
    }

    private fun read(
        path: Path,
        scope: LedgerScope,
        expectedScope: CrackScope,
    ): SeedCrackerLedgerSnapshot {
        if (!Files.isRegularFile(path)) {
            return SeedCrackerLedgerSnapshot()
        }

        return runCatching {
            val document = codec.decode(Files.readString(path, StandardCharsets.UTF_8))
            require(document.version == SEED_CRACKER_LEDGER_VERSION) { "Unsupported seed cracker ledger version" }
            require(document.serverKeyHash == scope.serverKeyHash) { "Server scope mismatch" }
            require(document.dimensionKeyHash == scope.dimensionKeyHash) { "Dimension scope mismatch" }
            require(document.generationProfile == scope.generationProfileKey) { "Generation profile mismatch" }
            freeze(document.snapshot, expectedScope)
        }.getOrDefault(SeedCrackerLedgerSnapshot())
    }

    private fun freeze(
        snapshot: SeedCrackerLedgerSnapshot,
        expectedScope: CrackScope,
    ): SeedCrackerLedgerSnapshot {
        val structures = snapshot.structureObservations.onEach { observation ->
            require(observation.scope == expectedScope) { "Structure evidence scope mismatch" }
            require(observation.id.value.isNotBlank()) { "Structure evidence id must not be blank" }
        }.toList()
        val bedrock = snapshot.netherBedrockObservations.onEach { observation ->
            require(observation.scope == expectedScope) { "Bedrock evidence scope mismatch" }
            require(observation.id.value.isNotBlank()) { "Bedrock evidence id must not be blank" }
        }.toList()
        val rejectedIds = snapshot.rejectedEvidenceIds.onEach { id ->
            require(id.value.isNotBlank()) { "Rejected evidence id must not be blank" }
        }.distinct()
        val candidate = snapshot.candidate?.also {
            require(it.scope == expectedScope) { "Candidate scope mismatch" }
        }
        val checkpoint = snapshot.netherSearchCheckpoint?.also {
            require(expectedScope.isNether) { "A Nether checkpoint requires a Nether scope" }
            require(it.evidenceFingerprint.isNotBlank()) { "A Nether checkpoint needs an evidence fingerprint" }
            require(it.nextPrefix in 0..NetherBedrockPrefixRange.TOTAL_PREFIXES) {
                "Nether checkpoint is out of range"
            }
            require(it.candidates.distinctBy { candidate -> candidate.seed }.size == it.candidates.size) {
                "Nether checkpoint candidates must be unique"
            }
        }

        return snapshot.copy(
            structureObservations = structures,
            netherBedrockObservations = bedrock,
            rejectedEvidenceIds = rejectedIds,
            candidate = candidate,
            netherSearchCheckpoint = checkpoint,
        )
    }

    private fun scope(
        serverKey: String,
        dimensionKey: String,
        generationProfile: GenerationProfile,
    ): LedgerScope {
        require(serverKey.isNotBlank()) { "Server key must not be blank" }
        require(dimensionKey.isNotBlank()) { "Dimension key must not be blank" }
        val generationProfileKey = generationProfileKey(generationProfile)
        require(generationProfileKey.isNotBlank()) { "Generation profile key must not be blank" }
        return LedgerScope(
            serverKeyHash = hashScopeKey(serverKey),
            dimensionKeyHash = hashScopeKey(dimensionKey),
            generationProfileKey = generationProfileKey,
            generationProfileHash = hashScopeKey(generationProfileKey),
        )
    }

    private fun versionDirectory(): Path = rootDirectory.resolve("v$SEED_CRACKER_LEDGER_VERSION")

    private fun path(scope: LedgerScope): Path = versionDirectory()
        .resolve(scope.serverKeyHash)
        .resolve("${scope.dimensionKeyHash}-${scope.generationProfileHash}.json")

    private fun nextRevision(scope: LedgerScope): Long {
        val next = scopeRevisions.getOrDefault(scope, 0L) + 1L
        scopeRevisions[scope] = next
        return next
    }

    private fun fileLock(scope: LedgerScope): Any = synchronized(stateLock) {
        fileLocks.getOrPut(scope) { Any() }
    }

    private fun ensureCurrent(scope: LedgerScope, revision: Long) {
        val current = synchronized(stateLock) { scopeRevisions[scope] }
        if (current != revision) {
            throw CancellationException("Superseded seed cracker ledger write")
        }
    }

    private fun moveAtomically(source: Path, destination: Path) {
        try {
            Files.move(source, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination, REPLACE_EXISTING)
        }
    }

    private data class LedgerScope(
        val serverKeyHash: String,
        val dimensionKeyHash: String,
        val generationProfileKey: String,
        val generationProfileHash: String,
    )

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 500L
    }
}
