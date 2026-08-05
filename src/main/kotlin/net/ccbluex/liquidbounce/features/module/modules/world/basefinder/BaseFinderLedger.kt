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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

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
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

internal const val BASE_FINDER_LEDGER_VERSION = 1
internal const val BASE_FINDER_LEDGER_RETENTION = 2_000

internal enum class BaseFinderExportFormat(val extension: String) {
    JSON("json"),
    CSV("csv"),
}

internal data class BaseFinderLedgerDocument(
    val version: Int = BASE_FINDER_LEDGER_VERSION,
    val findings: List<BaseFinding> = emptyList(),
)

internal interface BaseFinderLedgerCodec {
    fun encode(document: BaseFinderLedgerDocument): String
    fun decode(json: String): BaseFinderLedgerDocument
}

internal object BaseFinderGsonLedgerCodec : BaseFinderLedgerCodec {
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    override fun encode(document: BaseFinderLedgerDocument): String {
        val tree = prettyGson.toJsonTree(document)
        return prettyGson.toJson(tree)
    }

    override fun decode(json: String): BaseFinderLedgerDocument =
        prettyGson.fromJson(JsonParser.parseString(json), BaseFinderLedgerDocument::class.java)
}

/**
 * Versioned, scope-isolated persistence for accepted BaseFinder findings.
 *
 * Writes are debounced on [dispatcher] and replace the destination only after a complete temporary
 * file has been written. Callers keep ownership of the in-memory finding list; the ledger stores
 * immutable snapshots and never exposes a mutable collection.
 */
@Suppress("TooManyFunctions")
internal class BaseFinderLedger(
    private val rootDirectory: Path = ConfigSystem.rootFolder.toPath().resolve("base-finder"),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val debounceMillis: Long = 500L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val codec: BaseFinderLedgerCodec = BaseFinderGsonLedgerCodec,
) : AutoCloseable {

    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val stateLock = Any()
    private val pendingWrites = mutableMapOf<LedgerScope, Deferred<Result<Path>>>()
    private val fileLocks = mutableMapOf<LedgerScope, Any>()
    private val scopeRevisions = mutableMapOf<LedgerScope, Long>()

    init {
        require(debounceMillis >= 0L) { "debounceMillis must not be negative" }
    }

    fun hashScopeKey(key: String): String = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    fun scopePath(serverKey: String, dimensionKey: String): Path {
        val scope = scope(serverKey, dimensionKey)
        return rootDirectory.resolve("v$BASE_FINDER_LEDGER_VERSION")
            .resolve(scope.serverKeyHash)
            .resolve("${scope.dimensionKeyHash}.json")
    }

    /** Reads one scope. Invalid, malformed, or future-version documents fail closed. */
    fun load(serverKey: String, dimensionKey: String): List<BaseFinding> {
        val scope = scope(serverKey, dimensionKey)
        val path = scopePath(serverKey, dimensionKey)
        return synchronized(fileLock(scope)) {
            readDocument(path, scope, dimensionKey)
        }
    }

    /** Schedules an immutable snapshot for atomic storage and cancels an older pending write. */
    fun save(
        serverKey: String,
        dimensionKey: String,
        findings: Collection<BaseFinding>,
    ): Deferred<Result<Path>> {
        val scope = scope(serverKey, dimensionKey)
        val snapshot = retain(findings, scope.serverKeyHash, dimensionKey)
        lateinit var write: Deferred<Result<Path>>

        synchronized(stateLock) {
            pendingWrites.remove(scope)?.cancel()
            val revision = scopeRevisions.getOrDefault(scope, 0L) + 1L
            scopeRevisions[scope] = revision
            write = coroutineScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                if (debounceMillis > 0L) {
                    delay(debounceMillis)
                }
                runCatching { write(scope, revision, snapshot) }
            }
            pendingWrites[scope] = write
            write.invokeOnCompletion {
                synchronized(stateLock) {
                    if (pendingWrites[scope] === write) {
                        pendingWrites.remove(scope)
                    }
                }
            }
            write.start()
        }

        return write
    }

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

    /** Writes the current command-visible snapshot without waiting for the normal debounce window. */
    suspend fun saveImmediately(
        serverKey: String,
        dimensionKey: String,
        findings: Collection<BaseFinding>,
    ): Result<Path> {
        val scope = scope(serverKey, dimensionKey)
        val snapshot = retain(findings, scope.serverKeyHash, dimensionKey)
        val revision = synchronized(stateLock) {
            pendingWrites.remove(scope)?.cancel()
            (scopeRevisions.getOrDefault(scope, 0L) + 1L).also { scopeRevisions[scope] = it }
        }
        return withContext(dispatcher) { runCatching { write(scope, revision, snapshot) } }
    }

    fun saveImmediatelyBlocking(
        serverKey: String,
        dimensionKey: String,
        findings: Collection<BaseFinding>,
    ): Result<Path> = runBlocking { saveImmediately(serverKey, dimensionKey, findings) }

    suspend fun export(
        serverKey: String,
        dimensionKey: String,
        format: BaseFinderExportFormat,
    ): Path {
        flush()
        val findings = load(serverKey, dimensionKey)
        val scope = scope(serverKey, dimensionKey)
        return withContext(dispatcher) {
            val exportDirectory = rootDirectory.resolve("exports")
            Files.createDirectories(exportDirectory)
            val fileName = "basefinder-${clock()}-${scope.serverKeyHash.take(12)}-" +
                "${scope.dimensionKeyHash.take(12)}.${format.extension}"
            val exportPath = exportDirectory.resolve(fileName)
            val contents = when (format) {
                BaseFinderExportFormat.JSON -> codec.encode(BaseFinderLedgerDocument(findings = findings))
                BaseFinderExportFormat.CSV -> encodeCsv(findings)
            }
            Files.writeString(exportPath, contents, StandardCharsets.UTF_8)
        }
    }

    fun exportBlocking(
        serverKey: String,
        dimensionKey: String,
        format: BaseFinderExportFormat,
    ): Path = runBlocking { export(serverKey, dimensionKey, format) }

    /** Cancels an unwritten snapshot and removes the current scope file, if one exists. */
    suspend fun clear(serverKey: String, dimensionKey: String): Boolean {
        val scope = scope(serverKey, dimensionKey)
        synchronized(stateLock) {
            pendingWrites.remove(scope)?.cancel()
            scopeRevisions[scope] = scopeRevisions.getOrDefault(scope, 0L) + 1L
        }
        return withContext(dispatcher) {
            synchronized(fileLock(scope)) {
                Files.deleteIfExists(scopePath(serverKey, dimensionKey))
            }
        }
    }

    fun clearBlocking(serverKey: String, dimensionKey: String): Boolean =
        runBlocking { clear(serverKey, dimensionKey) }

    override fun close() {
        synchronized(stateLock) {
            pendingWrites.values.forEach { it.cancel() }
            pendingWrites.clear()
        }
        coroutineScope.cancel()
    }

    private fun write(scope: LedgerScope, revision: Long, findings: List<BaseFinding>): Path {
        val path = rootDirectory.resolve("v$BASE_FINDER_LEDGER_VERSION")
            .resolve(scope.serverKeyHash)
            .resolve("${scope.dimensionKeyHash}.json")
        val temporaryPath = path.resolveSibling("${path.fileName}.tmp")
        val document = BaseFinderLedgerDocument(findings = findings)
        val encoded = codec.encode(document)

        return synchronized(fileLock(scope)) {
            ensureCurrent(scope, revision)
            Files.createDirectories(path.parent)
            try {
                Files.writeString(temporaryPath, encoded, StandardCharsets.UTF_8)
                ensureCurrent(scope, revision)
                moveAtomically(temporaryPath, path)
                path
            } catch (throwable: Throwable) {
                Files.deleteIfExists(temporaryPath)
                throw throwable
            }
        }
    }

    private fun readDocument(path: Path, scope: LedgerScope, dimensionKey: String): List<BaseFinding> {
        if (!Files.isRegularFile(path)) {
            return emptyList()
        }

        return runCatching {
            val document = codec.decode(Files.readString(path, StandardCharsets.UTF_8))
            if (document.version != BASE_FINDER_LEDGER_VERSION) {
                return emptyList()
            }
            retain(document.findings, scope.serverKeyHash, dimensionKey)
        }.getOrDefault(emptyList())
    }

    private fun retain(
        findings: Collection<BaseFinding>,
        serverKeyHash: String,
        dimensionKey: String,
    ): List<BaseFinding> = findings.asSequence()
        .mapNotNull { finding -> sanitize(finding, serverKeyHash, dimensionKey) }
        .groupBy(BaseFinding::id)
        .values
        .map { duplicates ->
            duplicates.maxWithOrNull(
                compareBy<BaseFinding> { it.confidence }
                    .thenBy { it.lastSeenAtMillis }
                    .thenBy { it.timesSeen }
            )!!
        }
        .sortedWith(
            compareByDescending<BaseFinding> { it.confidence }
                .thenByDescending { it.lastSeenAtMillis }
                .thenBy { it.id }
        )
        .take(BASE_FINDER_LEDGER_RETENTION)
        .toList()

    private fun sanitize(
        finding: BaseFinding,
        serverKeyHash: String,
        dimensionKey: String,
    ): BaseFinding? = runCatching {
        require(finding.id.isNotBlank())
        require(finding.confidence in 0..100)
        require(finding.timesSeen >= 0)
        require(finding.firstSeenAtMillis <= finding.lastSeenAtMillis)
        finding.evidence.forEach { evidence ->
            require(evidence.score >= 0)
            require(evidence.keys.none(String::isBlank))
        }
        finding.copy(serverKeyHash = serverKeyHash, dimensionKey = dimensionKey)
    }.getOrNull()

    private fun fileLock(scope: LedgerScope): Any = synchronized(stateLock) {
        fileLocks.getOrPut(scope) { Any() }
    }

    private fun ensureCurrent(scope: LedgerScope, revision: Long) {
        val current = synchronized(stateLock) { scopeRevisions[scope] }
        if (current != revision) {
            throw CancellationException("Superseded BaseFinder ledger write")
        }
    }

    private fun scope(serverKey: String, dimensionKey: String) = LedgerScope(
        serverKeyHash = hashScopeKey(serverKey),
        dimensionKeyHash = hashScopeKey(dimensionKey),
    )

    private fun moveAtomically(source: Path, destination: Path) {
        try {
            Files.move(source, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination, REPLACE_EXISTING)
        }
    }

    private fun encodeCsv(findings: List<BaseFinding>): String = buildString {
        appendLine(
            "id,serverKeyHash,dimensionKey,x,y,z,confidence,tier,evidence," +
                "firstSeenAtMillis,lastSeenAtMillis,timesSeen"
        )
        findings.forEach { finding ->
            val evidence = finding.evidence.joinToString("|") { summary ->
                buildString {
                    append(summary.family.name)
                    append(':')
                    append(summary.score)
                    if (summary.keys.isNotEmpty()) {
                        append(':')
                        append(summary.keys.joinToString("+"))
                    }
                }
            }
            val values = listOf(
                finding.id,
                finding.serverKeyHash,
                finding.dimensionKey,
                finding.anchor.x,
                finding.anchor.y,
                finding.anchor.z,
                finding.confidence,
                finding.tier.name,
                evidence,
                finding.firstSeenAtMillis,
                finding.lastSeenAtMillis,
                finding.timesSeen,
            )
            appendLine(values.joinToString(",") { value -> csvField(value.toString()) })
        }
    }

    private fun csvField(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private data class LedgerScope(
        val serverKeyHash: String,
        val dimensionKeyHash: String,
    )
}
