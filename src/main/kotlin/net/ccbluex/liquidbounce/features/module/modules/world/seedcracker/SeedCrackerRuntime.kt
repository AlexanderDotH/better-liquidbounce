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

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockChunkSnapshot
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockCollector
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockConstraintSolver
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockLayer
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockPrefixRange
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchBatchOutcome
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchCheckpoint
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchCursor
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchEngine
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSearchProgress
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolvePlan
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockSolvePlanner
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockStartGate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockVerification
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether.NetherBedrockWorldSeedCandidate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.SeedFindingStructureConstraintAdapter
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureBlockSnapshot
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureChunkSnapshot
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedCancellationProbe
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedCollectionPlan
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedSearchCursor
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedSolveResult
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSeedSolver
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.StructureSignatureDetector
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures.toStructureSeedEvidenceOrNull
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.copyable
import net.ccbluex.liquidbounce.utils.world.forEachSectionBlock
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** A single local presentation that the module may show as both toast and client chat. */
internal data class SeedCrackerPresentation(
    val message: Component,
    val severity: NotificationEvent.Severity = NotificationEvent.Severity.INFO,
)

internal fun seedCrackerTranslation(key: String, vararg arguments: Any) =
    translation("liquidbounce.module.seedCracker.$key", *arguments)

/**
 * Coordinates client-visible observation, persistence, and background solvers.
 *
 * The [ChunkScanner] callbacks only reduce chunks into immutable values. All Minecraft access stays inside those
 * short callbacks or [onTick]; solvers only receive [SeedCrackerSnapshot] value objects.
 */
@Suppress(
    "CognitiveComplexMethod",
    "LargeClass",
    "LongMethod",
    "LoopWithTooManyJumpStatements",
    "MaxLineLength",
    "ReturnCount",
    "TooManyFunctions",
)
internal object SeedCrackerRuntime : ChunkScanner.BlockChangeSubscriber {

    override val shouldCallRecordBlockOnChunkUpdate: Boolean = false

    private val ledger = SeedCrackerLedger()
    private val bedrockCollector = NetherBedrockCollector()
    private val structureObservations = ConcurrentHashMap<String, StructureObservation>()
    private val bedrockObservations = ConcurrentHashMap<String, NetherBedrockChunkObservation>()
    private val rejectedEvidenceIds = ConcurrentHashMap.newKeySet<EvidenceId>()
    private val revisions = ConcurrentHashMap<ScopedChunk, AtomicLong>()
    private val dirtyChunks = ConcurrentHashMap.newKeySet<ScopedChunk>()
    private val presentations = ConcurrentLinkedQueue<SeedCrackerPresentation>()
    private val activeScope = AtomicReference<CrackScope?>()
    private val candidate = AtomicReference<SeedCandidate?>()
    private val latestSolveResult = AtomicReference<RuntimeSolveResult?>()
    private val latestStatus = AtomicReference<SeedCrackerStatus?>()
    private val structureSearchCursor = AtomicReference<StructureSeedSearchCursor?>()
    private val structureEvidenceFingerprint = AtomicReference<String?>()
    private val netherSearchCursor = AtomicReference(NetherBedrockSearchCursor())
    private val netherSearchProgress = AtomicReference<NetherBedrockSearchProgress?>()
    private val netherEvidenceFingerprint = AtomicReference<String?>()
    private val lastPersistedNetherCheckpointBucket = AtomicLong(-1L)

    private val tracker = SeedCrackerTracker<CrackScope, SeedCrackerSnapshot, RuntimeSolveResult>(
        freezeSnapshot = ::freezeSnapshot,
        solve = ::solveSnapshot,
    )

    @Volatile
    private var settings = RuntimeSettings()

    @Volatile
    private var enabled = false

    @Volatile
    private var subscribed = false

    private var lastGuidanceKey: String? = null

    fun onEnabled(
        structuresEnabled: Boolean,
        netherBedrockEnabled: Boolean,
        autoAcceptStrongEvidence: Boolean,
        persistProgress: Boolean,
        workerLimit: Int,
    ) {
        enabled = true
        updateSettings(
            structuresEnabled = structuresEnabled,
            netherBedrockEnabled = netherBedrockEnabled,
            autoAcceptStrongEvidence = autoAcceptStrongEvidence,
            persistProgress = persistProgress,
            workerLimit = workerLimit,
        )
        warnAboutParallelSeedCrackerX()
        activateCurrentScope()
        // ChunkScanner can immediately replay already-loaded chunks. Establish the scope before subscribing so an
        // Igloo the player is already standing in is not dropped by an activeScope == null race.
        subscribe()
        refreshStatusProjection()
    }

    fun updateSettings(
        structuresEnabled: Boolean,
        netherBedrockEnabled: Boolean,
        autoAcceptStrongEvidence: Boolean,
        persistProgress: Boolean,
        workerLimit: Int,
    ) {
        val previousSettings = settings
        settings = RuntimeSettings(
            structuresEnabled = structuresEnabled,
            netherBedrockEnabled = netherBedrockEnabled,
            autoAcceptStrongEvidence = autoAcceptStrongEvidence,
            persistProgress = persistProgress,
            workerLimit = workerLimit.coerceIn(MIN_WORKERS, MAX_WORKERS),
        )
        if (settings != previousSettings) {
            invalidateCandidate()
        }
        tracker.updateWorkerLimit(settings.workerLimit)
        activeScope.get()?.let(::offerCurrentSnapshot)
        refreshStatusProjection()
    }

    fun onDisabled(persistProgress: Boolean) {
        val scope = activeScope.get()
        if (persistProgress) scope?.let(::persist)
        enabled = false
        unsubscribe()
        tracker.deactivate()
        clearVolatileEvidence()
        activeScope.set(null)
        candidate.set(null)
        latestSolveResult.set(null)
        latestStatus.set(null)
        lastGuidanceKey = null
        presentations.clear()
    }

    fun onWorldChanged() {
        activeScope.get()?.takeIf { settings.persistProgress }?.let(::persist)
        clearVolatileEvidence()
        candidate.set(null)
        latestSolveResult.set(null)
        lastGuidanceKey = null
        activateCurrentScope()
        refreshStatusProjection()
    }

    /** Main-thread tick hook: performs at most two rescans requested by block-update callbacks. */
    fun onTick() {
        refreshSolverResult()
        rescanDirtyChunks()
        refreshStatusProjection()
        publishGuidanceIfChanged()
    }

    fun consumePresentation(): SeedCrackerPresentation? {
        refreshSolverResult()
        refreshStatusProjection()
        publishGuidanceIfChanged()
        return presentations.poll()
    }

    fun status(): SeedCrackerPresentation {
        refreshSolverResult()
        val status = refreshStatusProjection() ?: return presentation("noWorld", NotificationEvent.Severity.ERROR)
        latestSolveResult.get()?.conflictReport?.let { return conflictPresentation(it) }
        return statusPresentation(status)
    }

    fun hudStatus(): SeedCrackerStatus? {
        refreshSolverResult()
        return refreshStatusProjection()
    }

    fun pendingEvidenceIds(): List<String> = latestStatus.get()?.pendingEvidenceIds.orEmpty()

    fun evidenceIds(): List<String> {
        val scope = activeScope.get() ?: return emptyList()
        return (structureObservations.values.asSequence() + bedrockObservations.values.asSequence())
            .filter { it.scope == scope }
            .map { it.id.value }
            .distinct()
            .sorted()
            .toList()
    }

    fun confirm(id: String): SeedCrackerPresentation = changeStructureStatus(id, EvidenceStatus.ACCEPTED, "evidenceConfirmed")

    fun reject(id: String): SeedCrackerPresentation = changeStructureStatus(id, EvidenceStatus.REJECTED, "evidenceRejected")

    fun confirmGuided(): SeedCrackerPresentation = changeGuidedStructureStatus(
        status = EvidenceStatus.ACCEPTED,
        resultKey = "evidenceConfirmed",
    )

    fun rejectGuided(): SeedCrackerPresentation = changeGuidedStructureStatus(
        status = EvidenceStatus.REJECTED,
        resultKey = "evidenceRejected",
    )

    fun undo(id: String): SeedCrackerPresentation {
        val scope = activeScope.get() ?: return presentation("noWorld", NotificationEvent.Severity.ERROR)
        val removed = structureObservations.entries.removeIf { it.value.id.value == id }
        val removedBedrockChunks = bedrockObservations.values
            .filter { it.id.value == id }
            .map(NetherBedrockChunkObservation::chunk)
        val removedBedrock = bedrockObservations.entries.removeIf { it.value.id.value == id }
        removedBedrockChunks.forEach { bedrockCollector.remove(scope, it) }
        rejectedEvidenceIds.remove(EvidenceId(id))
        if (!removed && !removedBedrock) return presentation("unknownEvidence", NotificationEvent.Severity.ERROR, id)
        invalidateCandidate()
        persist(scope)
        offerCurrentSnapshot(scope)
        refreshStatusProjection(scope)
        return presentation("evidenceUndone", NotificationEvent.Severity.SUCCESS, id)
    }

    fun pause(): SeedCrackerPresentation = if (tracker.pause()) {
        netherSearchProgress.updateAndGet { it?.copy(paused = true) }
        activeScope.get()?.let(::persist)
        refreshStatusProjection()
        presentation("paused", NotificationEvent.Severity.INFO)
    } else {
        presentation("alreadyPaused", NotificationEvent.Severity.INFO)
    }

    fun resume(): SeedCrackerPresentation = if (tracker.resume()) {
        netherSearchProgress.updateAndGet { it?.copy(paused = false) }
        refreshStatusProjection()
        presentation("resumed", NotificationEvent.Severity.INFO)
    } else {
        presentation("alreadyRunning", NotificationEvent.Severity.INFO)
    }

    fun resetCurrent(): SeedCrackerPresentation {
        val scope = activeScope.get() ?: return presentation("noWorld", NotificationEvent.Severity.ERROR)
        ledger.clearBlocking(scope)
        clearVolatileEvidence()
        candidate.set(null)
        latestSolveResult.set(null)
        tracker.reset()
        refreshStatusProjection()
        publishGuidanceIfChanged(force = true)
        return presentation("resetCurrent", NotificationEvent.Severity.SUCCESS)
    }

    fun resetAll(): SeedCrackerPresentation {
        ledger.clearAllBlocking()
        clearVolatileEvidence()
        candidate.set(null)
        latestSolveResult.set(null)
        tracker.reset()
        refreshStatusProjection()
        publishGuidanceIfChanged(force = true)
        return presentation("resetAll", NotificationEvent.Severity.SUCCESS)
    }

    override fun recordBlock(pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState, cleared: Boolean) {
        val scope = activeScope.get() ?: return
        if (!enabled || !isRelevantBlockUpdate(scope, pos.y, state, cleared)) return
        dirtyChunks += ScopedChunk(scope, ChunkCoordinate(pos.x shr CHUNK_SHIFT, pos.z shr CHUNK_SHIFT))
    }

    override fun chunkUpdate(chunk: LevelChunk) {
        val scope = activeScope.get() ?: return
        if (!enabled) return
        val coordinate = ChunkCoordinate(chunk.pos.x, chunk.pos.z)
        val scopedChunk = ScopedChunk(scope, coordinate)
        val revision = revisions.computeIfAbsent(scopedChunk) { AtomicLong() }.incrementAndGet()
        scanChunk(scope, chunk, revision)
    }

    override fun clearChunk(pos: ChunkPos) {
        val scope = activeScope.get() ?: return
        val scopedChunk = ScopedChunk(scope, ChunkCoordinate(pos.x, pos.z))
        revisions.remove(scopedChunk)
        dirtyChunks.remove(scopedChunk)
        // An unload only drops the client's live chunk object. It must not retract an immutable observation that
        // was already seen and persisted; otherwise travelling away from collected Nether chunks would erase the
        // exact floor/roof constraints needed for a later solve.
    }

    override fun clearAllChunks() {
        // ChunkScanner clears its cache before our WorldChangeEvent handler has persisted the old scope.
        // Keep accepted evidence intact until that lifecycle handler can save it, but discard any scanner-local
        // scheduling data which could otherwise refer to unloaded LevelChunk coordinates.
        revisions.clear()
        dirtyChunks.clear()
    }

    private fun activateCurrentScope() {
        if (!enabled) return
        val scope = currentScope() ?: run {
            activeScope.set(null)
            tracker.deactivate()
            presentations += presentation("noWorld", NotificationEvent.Severity.INFO)
            return
        }
        clearVolatileEvidence()
        activeScope.set(scope)
        tracker.deactivate()
        tracker.activate(scope)
        candidate.set(null)
        latestSolveResult.set(null)
        load(scope)
        lastGuidanceKey = null
        offerCurrentSnapshot(scope)
        refreshStatusProjection(scope)
        presentations += presentation("enabled", NotificationEvent.Severity.INFO, scope.dimensionKey)
    }

    private fun subscribe() {
        if (subscribed) return
        ChunkScanner.subscribe(this)
        subscribed = true
    }

    private fun unsubscribe() {
        if (!subscribed) return
        ChunkScanner.unsubscribe(this)
        subscribed = false
    }

    private fun warnAboutParallelSeedCrackerX() {
        if (FabricLoader.getInstance().isModLoaded("seedcrackerx")) {
            presentations += presentation("externalSeedCrackerX", NotificationEvent.Severity.ERROR)
        }
    }

    private fun rescanDirtyChunks() {
        val scope = activeScope.get() ?: return
        val world = mc.level ?: return
        var remaining = MAX_DIRTY_RESCANS_PER_TICK
        val iterator = dirtyChunks.iterator()
        while (iterator.hasNext() && remaining > 0) {
            val scopedChunk = iterator.next()
            if (scopedChunk.scope != scope) continue
            if (!dirtyChunks.remove(scopedChunk)) continue
            if (!world.hasChunk(scopedChunk.chunk.x, scopedChunk.chunk.z)) continue
            val chunk = world.getChunk(scopedChunk.chunk.x, scopedChunk.chunk.z)
            if (chunk.isEmpty) continue
            val revision = revisions.computeIfAbsent(scopedChunk) { AtomicLong() }.incrementAndGet()
            scanChunk(scope, chunk, revision)
            remaining--
        }
    }

    private fun scanChunk(scope: CrackScope, chunk: LevelChunk, revision: Long) {
        if (activeScope.get() != scope) return
        var changed = false
        val newlyAcceptedStructures = mutableListOf<StructureObservation>()
        val currentSettings = settings
        if (scope.isNether && currentSettings.netherBedrockEnabled && shouldCollectNetherChunk(scope, chunk)) {
            val change = bedrockCollector.record(netherSnapshot(scope, chunk, revision))
            if (change.changed) {
                bedrockObservations[change.observation.deduplicationKey] = change.observation
                changed = true
            }
        }
        if (scope.isOverworld && currentSettings.structuresEnabled) {
            val snapshot = structureSnapshot(scope, chunk, revision)
            StructureSignatureDetector.detect(snapshot).forEach { match ->
                var observation = match.toObservation(scope)
                val previous = structureObservations[observation.deduplicationKey]
                observation = observation.preserveDecisionFrom(previous)
                if (observation.id in rejectedEvidenceIds) {
                    observation = observation.copy(status = EvidenceStatus.REJECTED)
                } else if (
                    previous == null && observation.confidence == EvidenceConfidence.STRONG &&
                    !currentSettings.autoAcceptStrongEvidence
                ) {
                    observation = observation.copy(status = EvidenceStatus.PENDING_CONFIRMATION)
                }
                structureObservations[observation.deduplicationKey] = observation
                if (previous != observation) changed = true
                if (previous?.status != EvidenceStatus.ACCEPTED && observation.status == EvidenceStatus.ACCEPTED) {
                    newlyAcceptedStructures += observation
                }
            }
        }
        if (activeScope.get() != scope || !changed) return
        invalidateCandidate()
        persist(scope)
        offerCurrentSnapshot(scope)
        refreshStatusProjection(scope)
        newlyAcceptedStructures.forEach { observation ->
            presentations += presentation(
                "evidenceAccepted",
                NotificationEvent.Severity.SUCCESS,
                observation.type.id,
                observation.anchorChunk.x.toString(),
                observation.anchorChunk.z.toString(),
            )
        }
    }

    private fun netherSnapshot(scope: CrackScope, chunk: LevelChunk, revision: Long): NetherBedrockChunkSnapshot {
        val mutable = BlockPos.MutableBlockPos()
        fun plane(y: Int) = NetherBedrockBitPlane.fromPredicate { localX, localZ ->
            mutable.set(chunk.pos.minBlockX + localX, y, chunk.pos.minBlockZ + localZ)
            chunk.getBlockState(mutable).block == Blocks.BEDROCK
        }
        return NetherBedrockChunkSnapshot(
            scope = scope,
            chunk = ChunkCoordinate(chunk.pos.x, chunk.pos.z),
            revision = revision,
            floor = plane(NetherBedrockLayer.FLOOR.blockY),
            roof = plane(NetherBedrockLayer.ROOF.blockY),
        )
    }

    private fun structureSnapshot(scope: CrackScope, chunk: LevelChunk, revision: Long): StructureChunkSnapshot {
        val blocks = ArrayList<StructureBlockSnapshot>()
        val mutable = BlockPos.MutableBlockPos()
        chunk.sections.forEachIndexed { index, section ->
            if (section.hasOnlyAir()) return@forEachIndexed
            chunk.forEachSectionBlock(index, mutable) { position, state ->
                val blockId = BuiltInRegistries.BLOCK.getKey(state.block).toString()
                if (blockId.toStableStructureBlockId() in RELEVANT_STRUCTURE_BLOCKS) {
                    blocks += StructureBlockSnapshot(position.x, position.y, position.z, blockId)
                }
            }
        }
        return StructureChunkSnapshot(
            chunkX = chunk.pos.x,
            chunkZ = chunk.pos.z,
            rawDimensionKey = scope.dimensionKey,
            revision = revision,
            blocks = blocks,
        )
    }

    private fun isRelevantBlockUpdate(
        scope: CrackScope,
        y: Int,
        state: net.minecraft.world.level.block.state.BlockState,
        cleared: Boolean,
    ): Boolean {
        if (scope.isNether && settings.netherBedrockEnabled && y in NETHER_PATTERN_LAYERS) return true
        if (!scope.isOverworld || !settings.structuresEnabled) return false
        if (cleared) return true
        return BuiltInRegistries.BLOCK.getKey(state.block).toString().toStableStructureBlockId() in RELEVANT_STRUCTURE_BLOCKS
    }

    private fun shouldCollectNetherChunk(scope: CrackScope, chunk: LevelChunk): Boolean {
        val retained = NetherBedrockSolvePlanner.retain(scope, bedrockObservations.values)
        val coordinate = ChunkCoordinate(chunk.pos.x, chunk.pos.z)
        return retained.size < NetherBedrockSolvePlanner.MAX_RETAINED_CHUNKS || retained.any { it.chunk == coordinate }
    }

    private fun offerCurrentSnapshot(scope: CrackScope) {
        if (activeScope.get() != scope || !enabled) return
        val snapshot = snapshotFor(scope)
        val structureFingerprint = snapshot.structures
            .filter(StructureObservation::isAccepted)
            .sortedBy(StructureObservation::deduplicationKey)
            .joinToString(separator = "|") { observation ->
                "${observation.id.value}:${observation.revision}"
            }
        if (structureEvidenceFingerprint.getAndSet(structureFingerprint) != structureFingerprint) {
            structureSearchCursor.set(null)
        }
        if (scope.isNether) {
            val plan = NetherBedrockSolvePlanner.plan(scope, snapshot.netherBedrock)
            val previousFingerprint = netherEvidenceFingerprint.getAndSet(plan.fingerprint)
            if (previousFingerprint != plan.fingerprint) {
                netherSearchCursor.set(NetherBedrockSearchCursor())
                netherSearchProgress.set(null)
                lastPersistedNetherCheckpointBucket.set(-1L)
            } else if (tracker.snapshot().input != null) {
                return
            }
        }
        val netherPlan = NetherBedrockSolvePlanner.plan(scope, snapshot.netherBedrock)
        val hasEnoughStructureInformation = scope.isOverworld &&
            CrackingTechnique.STRUCTURES in snapshot.enabledTechniques &&
            StructureSeedCollectionPlan.progress(snapshot.structures).isReady
        val hasEnoughNetherInformation = scope.isNether &&
            CrackingTechnique.NETHER_BEDROCK in snapshot.enabledTechniques &&
            netherPlan.isReady
        if (!hasEnoughStructureInformation && !hasEnoughNetherInformation) {
            tracker.reset()
            return
        }
        tracker.offer(scope, snapshot)
    }

    private suspend fun solveSnapshot(snapshot: SeedCrackerSnapshot): RuntimeSolveResult? {
        val coroutineContext = currentCoroutineContext()
        val cancelled = { !coroutineContext.isActive || activeScope.get() != snapshot.scope }
        if (cancelled()) return null

        if (snapshot.scope.isOverworld && CrackingTechnique.STRUCTURES in snapshot.enabledTechniques) {
            val result = StructureSeedSolver(SeedFindingStructureConstraintAdapter()).solve(
                snapshot.structures.mapNotNull(StructureObservation::toStructureSeedEvidenceOrNull),
                StructureSeedCancellationProbe { cancelled() },
                structureSearchCursor.get(),
            )
            when (result) {
                is StructureSeedSolveResult.FullSeed -> return RuntimeSolveResult(
                    candidate = SeedCandidate(
                        scope = snapshot.scope,
                        seed = result.seed,
                        source = CandidateSource.STRUCTURES,
                        evidenceIds = snapshot.structures.filter(StructureObservation::isAccepted).mapTo(linkedSetOf()) { it.id },
                        verification = CandidateVerification.UNVERIFIED,
                        calculatedRevision = snapshot.revision,
                    ),
                    state = CrackerState.CANDIDATE,
                    messageKey = "candidateFound",
                )

                is StructureSeedSolveResult.StructureSeeds -> {
                    val structureSeed = result.candidates.singleOrNull()
                    if (structureSeed == null) {
                        return RuntimeSolveResult(
                            state = CrackerState.NEEDS_ACTION,
                            messageKey = "structureSeedCandidates",
                            messageArguments = listOf(result.candidates.size.toString()),
                        )
                    }

                    return RuntimeSolveResult(
                        candidate = SeedCandidate(
                            scope = snapshot.scope,
                            seed = structureSeed,
                            source = CandidateSource.STRUCTURES,
                            kind = SeedCandidateKind.STRUCTURE_SEED_48,
                            evidenceIds = snapshot.structures.filter(StructureObservation::isAccepted)
                                .mapTo(linkedSetOf()) { it.id },
                            verification = CandidateVerification.UNVERIFIED,
                            calculatedRevision = snapshot.revision,
                        ),
                        state = CrackerState.CANDIDATE,
                        messageKey = "candidateFound",
                    )
                }

                is StructureSeedSolveResult.Searching -> return RuntimeSolveResult(
                    state = CrackerState.SOLVING,
                    nextStructureCursor = result.continuation,
                )

                is StructureSeedSolveResult.ContradictedEvidence -> {
                    val acceptedById = snapshot.structures
                        .filter(StructureObservation::isAccepted)
                        .associateBy { it.id.value }
                    val involved = result.conflictingEvidenceIds.mapNotNull(acceptedById::get)
                        .ifEmpty { acceptedById.values.toList() }
                    return RuntimeSolveResult(
                        state = CrackerState.CONTRADICTED,
                        messageKey = "candidateContradicted",
                        severity = NotificationEvent.Severity.ERROR,
                        conflictReport = SeedCrackerConflictReport.inconsistentStructures(
                            detail = result.detail,
                            evidence = involved.map { observation ->
                                SeedCrackerConflictReport.StructureEvidence(
                                    id = observation.id,
                                    type = observation.type,
                                    chunkX = observation.anchorChunk.x,
                                    chunkZ = observation.anchorChunk.z,
                                )
                            },
                        ),
                    )
                }

                is StructureSeedSolveResult.NeedMoreEvidence,
                StructureSeedSolveResult.Unavailable,
                StructureSeedSolveResult.Cancelled -> return RuntimeSolveResult(state = CrackerState.NEEDS_ACTION)
            }
        }

        if (snapshot.scope.isNether && CrackingTechnique.NETHER_BEDROCK in snapshot.enabledTechniques) {
            return solveNetherSnapshot(snapshot, cancelled)
        }

        return null
    }

    private suspend fun solveNetherSnapshot(
        snapshot: SeedCrackerSnapshot,
        cancelled: () -> Boolean,
    ): RuntimeSolveResult {
        val plan = NetherBedrockSolvePlanner.plan(snapshot.scope, snapshot.netherBedrock)
        if (!plan.isReady || netherEvidenceFingerprint.get() != plan.fingerprint) {
            return RuntimeSolveResult(state = CrackerState.NEEDS_ACTION)
        }
        val chunks = NetherBedrockConstraintSolver.fromAcceptedObservations(snapshot.scope, plan.allObservations)
        when (NetherBedrockConstraintSolver.startGate(chunks)) {
            is NetherBedrockStartGate.NeedsMoreInformation -> return RuntimeSolveResult(state = CrackerState.NEEDS_ACTION)
            is NetherBedrockStartGate.Ready -> Unit
        }
        val heldOut = chunks.last()
        val sourceChunks = chunks.dropLast(1)
        var cursor = netherSearchCursor.get()
        val measuredFromPrefix = cursor.nextPrefix
        val startedAt = System.nanoTime()

        while (!cancelled() && netherEvidenceFingerprint.get() == plan.fingerprint) {
            val outcome = NetherBedrockSearchEngine.searchBatch(
                sourceChunks = sourceChunks,
                heldOutChunks = listOf(heldOut),
                cursor = cursor,
                workerCount = settings.workerLimit,
                isCancelled = cancelled,
            )
            when (outcome) {
                is NetherBedrockSearchBatchOutcome.Progress -> {
                    if (!netherSearchCursor.compareAndSet(cursor, outcome.cursor)) {
                        return RuntimeSolveResult(state = CrackerState.COLLECTING)
                    }
                    cursor = outcome.cursor
                    publishNetherProgress(snapshot.scope, cursor, measuredFromPrefix, startedAt)
                }

                is NetherBedrockSearchBatchOutcome.Complete -> {
                    val completedCursor = NetherBedrockSearchCursor(
                        nextPrefix = NetherBedrockPrefixRange.TOTAL_PREFIXES,
                        candidates = outcome.candidates,
                    )
                    if (!netherSearchCursor.compareAndSet(cursor, completedCursor)) {
                        return RuntimeSolveResult(state = CrackerState.COLLECTING)
                    }
                    publishNetherProgress(
                        snapshot.scope,
                        completedCursor,
                        measuredFromPrefix,
                        startedAt,
                    )
                    return completedNetherResult(snapshot, plan, outcome.candidates)
                }

                is NetherBedrockSearchBatchOutcome.CandidateBudgetExceeded -> return RuntimeSolveResult(
                    state = CrackerState.NEEDS_ACTION,
                    messageKey = "netherWorldSeedCandidates",
                    messageArguments = listOf(outcome.candidateLimit.toString()),
                )

                is NetherBedrockSearchBatchOutcome.Cancelled -> {
                    return RuntimeSolveResult(state = CrackerState.COLLECTING)
                }
            }
        }
        return RuntimeSolveResult(state = CrackerState.COLLECTING)
    }

    private fun completedNetherResult(
        snapshot: SeedCrackerSnapshot,
        plan: NetherBedrockSolvePlan,
        candidates: List<NetherBedrockWorldSeedCandidate>,
    ): RuntimeSolveResult {
        val worldCandidates = candidates.distinctBy { it.seed }
        val worldCandidate = worldCandidates.singleOrNull()
        if (worldCandidate == null && worldCandidates.isNotEmpty()) {
            return RuntimeSolveResult(
                state = CrackerState.NEEDS_ACTION,
                messageKey = "netherWorldSeedCandidates",
                messageArguments = listOf(worldCandidates.size.toString()),
            )
        }
        if (worldCandidate == null) {
            return RuntimeSolveResult(
                state = CrackerState.CONTRADICTED,
                messageKey = "candidateContradicted",
                severity = NotificationEvent.Severity.ERROR,
                conflictReport = SeedCrackerConflictReport.inconsistentNether(
                    detail = "No Java 26.2 Nether seed matches the selected floor and roof observations",
                    evidence = plan.allObservations.map { observation ->
                        SeedCrackerConflictReport.NetherEvidence(
                            id = observation.id,
                            chunkX = observation.chunk.x,
                            chunkZ = observation.chunk.z,
                        )
                    },
                ),
            )
        }
        return RuntimeSolveResult(
            candidate = SeedCandidate(
                scope = snapshot.scope,
                seed = worldCandidate.seed,
                source = CandidateSource.NETHER_BEDROCK,
                evidenceIds = plan.sourceObservations.mapTo(linkedSetOf()) { it.id },
                verificationEvidenceIds = setOf(checkNotNull(plan.heldOutObservation).id),
                verification = if (worldCandidate.verification == NetherBedrockVerification.HELD_OUT_VALIDATED) {
                    CandidateVerification.VERIFIED
                } else {
                    CandidateVerification.UNVERIFIED
                },
                calculatedRevision = snapshot.revision,
            ),
            state = CrackerState.CANDIDATE,
            messageKey = "candidateFound",
        )
    }

    private fun publishNetherProgress(
        scope: CrackScope,
        cursor: NetherBedrockSearchCursor,
        measuredFromPrefix: Long,
        startedAt: Long,
    ) {
        val elapsedMillis = (System.nanoTime() - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLI
        netherSearchProgress.set(
            NetherBedrockSearchProgress(
                checkedPrefixes = cursor.nextPrefix,
                elapsedMillis = elapsedMillis,
                measuredPrefixes = (cursor.nextPrefix - measuredFromPrefix).coerceAtLeast(0L),
            ),
        )
        val bucket = cursor.nextPrefix / NETHER_CHECKPOINT_PREFIX_INTERVAL
        if (lastPersistedNetherCheckpointBucket.getAndSet(bucket) != bucket) {
            persist(scope)
        }
    }

    private fun refreshSolverResult() {
        val trackerSnapshot = tracker.snapshot()
        val result = trackerSnapshot.result ?: return
        if (latestSolveResult.getAndSet(result) == result) return
        val appliedCandidate = result.candidate?.copy(calculatedRevision = trackerSnapshot.ticket.revision)
        appliedCandidate?.let(candidate::set)
        if (result.state == CrackerState.CONTRADICTED) {
            candidate.set(null)
        }
        val scope = activeScope.get() ?: return
        if (appliedCandidate != null || result.state == CrackerState.CONTRADICTED) {
            persist(scope)
        }
        appliedCandidate?.let { seedCandidate ->
            presentations += candidatePresentation(seedCandidate, result.messageKey ?: "candidateFound", result.severity)
            lastGuidanceKey = SeedCrackerGuidance.nextAction(snapshotFor(scope)).deduplicationKey
        } ?: result.conflictReport?.let { report ->
            presentations += conflictPresentation(report)
        } ?: result.messageKey?.let { key ->
            presentations += presentation(key, result.severity, *result.messageArguments.toTypedArray())
        }
        if (appliedCandidate == null) {
            val guidance = SeedCrackerGuidance.nextAction(snapshotFor(scope))
            if (guidance.matchesPresentationKey(result.messageKey)) {
                lastGuidanceKey = guidance.deduplicationKey
            }
        }

        result.nextStructureCursor?.let { nextCursor ->
            if (enabled && activeScope.get() == scope) {
                structureSearchCursor.set(nextCursor)
                offerCurrentSnapshot(scope)
            }
        }

    }

    private fun publishGuidanceIfChanged(force: Boolean = false) {
        val status = refreshStatusProjection() ?: return
        val guidance = status.nextAction
        if (!force && guidance.deduplicationKey == lastGuidanceKey) return
        lastGuidanceKey = guidance.deduplicationKey
        presentations += presentation(
            guidance.key.removePrefix("seedcracker.guidance."),
            severityFor(guidance.kind),
            *guidance.arguments.toTypedArray(),
        )
    }

    private fun snapshotFor(scope: CrackScope): SeedCrackerSnapshot {
        val trackerSnapshot = tracker.snapshot()
        val result = trackerSnapshot.result
        val state = resolveCrackerState(trackerSnapshot.phase, result?.state)
        return SeedCrackerSnapshot(
            scope = scope,
            worldEpoch = trackerSnapshot.ticket.worldEpoch.coerceAtLeast(0L),
            revision = trackerSnapshot.ticket.revision.coerceAtLeast(0L),
            state = state,
            structures = structureObservations.values.filter { it.scope == scope }.sortedBy(StructureObservation::deduplicationKey),
            netherBedrock = NetherBedrockSolvePlanner.retain(scope, bedrockObservations.values),
            candidate = candidate.get()?.takeIf {
                it.scope == scope && it.calculatedRevision == trackerSnapshot.ticket.revision
            },
            enabledTechniques = settings.enabledTechniques,
        )
    }

    private fun freezeSnapshot(snapshot: SeedCrackerSnapshot): SeedCrackerSnapshot = snapshot.copy(
        structures = snapshot.structures.toList(),
        netherBedrock = snapshot.netherBedrock.toList(),
        enabledTechniques = snapshot.enabledTechniques.toSet(),
    )

    private fun load(scope: CrackScope) {
        val persisted = ledger.load(scope)
        persisted.structureObservations.forEach { structureObservations[it.deduplicationKey] = it }
        val retainedBedrock = NetherBedrockSolvePlanner.retain(scope, persisted.netherBedrockObservations)
        retainedBedrock.forEach { bedrockObservations[it.deduplicationKey] = it }
        bedrockCollector.restore(retainedBedrock)
        rejectedEvidenceIds += persisted.rejectedEvidenceIds
        candidate.set(persisted.candidate)
        if (scope.isNether) {
            val plan = NetherBedrockSolvePlanner.plan(scope, retainedBedrock)
            netherEvidenceFingerprint.set(plan.fingerprint)
            val nextPrefix = persisted.netherSearchCheckpoint
                ?.takeIf { it.evidenceFingerprint == plan.fingerprint }
                ?.nextPrefix
                ?: 0L
            val restoredCandidates = persisted.netherSearchCheckpoint
                ?.takeIf { it.evidenceFingerprint == plan.fingerprint }
                ?.candidates
                .orEmpty()
            netherSearchCursor.set(NetherBedrockSearchCursor(nextPrefix, restoredCandidates))
            netherSearchProgress.set(
                NetherBedrockSearchProgress(
                    checkedPrefixes = nextPrefix,
                    elapsedMillis = 0L,
                    measuredPrefixes = 0L,
                ).takeIf { nextPrefix > 0L },
            )
        }
    }

    private fun persist(scope: CrackScope) {
        if (!settings.persistProgress) return
        val retainedBedrock = NetherBedrockSolvePlanner.retain(scope, bedrockObservations.values)
        val plan = NetherBedrockSolvePlanner.plan(scope, retainedBedrock)
        val cursor = netherSearchCursor.get()
        val checkpoint = if (scope.isNether && plan.fingerprint.isNotBlank()) {
            NetherBedrockSearchCheckpoint(
                evidenceFingerprint = plan.fingerprint,
                nextPrefix = cursor.nextPrefix,
                candidates = cursor.candidates,
            )
        } else {
            null
        }
        ledger.save(scope, SeedCrackerLedgerSnapshot(
            structureObservations = structureObservations.values.filter { it.scope == scope }.toList(),
            netherBedrockObservations = retainedBedrock,
            rejectedEvidenceIds = rejectedEvidenceIds.toList(),
            candidate = candidate.get()?.takeIf { it.scope == scope },
            netherSearchCheckpoint = checkpoint,
        ))
    }

    private fun changeStructureStatus(
        rawId: String,
        status: EvidenceStatus,
        resultKey: String,
    ): SeedCrackerPresentation {
        val scope = activeScope.get() ?: return presentation("noWorld", NotificationEvent.Severity.ERROR)
        val entry = structureObservations.entries.firstOrNull { it.value.id.value == rawId }
            ?: return presentation("unknownEvidence", NotificationEvent.Severity.ERROR, rawId)
        val previous = entry.value
        structureObservations[entry.key] = previous.copy(status = status)
        if (status == EvidenceStatus.REJECTED) rejectedEvidenceIds += previous.id else rejectedEvidenceIds -= previous.id
        invalidateCandidate()
        persist(scope)
        offerCurrentSnapshot(scope)
        refreshStatusProjection(scope)
        return presentation(resultKey, NotificationEvent.Severity.SUCCESS, rawId)
    }

    private fun changeGuidedStructureStatus(
        status: EvidenceStatus,
        resultKey: String,
    ): SeedCrackerPresentation {
        val scope = activeScope.get() ?: return presentation("noWorld", NotificationEvent.Severity.ERROR)
        val observations = structureObservations.values.filter { it.scope == scope }
        val candidates = StructureSeedCollectionPlan.guidedPendingEvidenceCandidates(observations)
        val selected = candidates.singleOrNull()
        if (selected != null) {
            return changeStructureStatus(selected.id.value, status, resultKey)
        }
        if (candidates.size > 1) {
            return presentation(
                "multiplePendingEvidence",
                NotificationEvent.Severity.ERROR,
                candidates.size.toString(),
                candidates.first().type.id,
            )
        }
        return presentation("noPendingEvidence", NotificationEvent.Severity.INFO)
    }

    private fun refreshStatusProjection(scope: CrackScope? = activeScope.get()): SeedCrackerStatus? {
        val currentScope = scope?.takeIf { it == activeScope.get() } ?: run {
            latestStatus.set(null)
            return null
        }
        return SeedCrackerStatusProjection.from(
            snapshot = snapshotFor(currentScope),
            netherProgress = netherSearchProgress.get(),
        ).also(latestStatus::set)
    }

    private fun statusPresentation(status: SeedCrackerStatus): SeedCrackerPresentation {
        val message = Component.empty()
            .append(seedCrackerTranslation("status.scope", status.scope.dimensionKey))
            .append(Component.literal("\n"))
            .append(seedCrackerTranslation("status.state"))
            .append(seedCrackerTranslation("status.state.${status.state.localizationKey}"))

        if (status.scope.isOverworld) {
            message.append(Component.literal("\n"))
                .append(
                    seedCrackerTranslation(
                        "status.structures",
                        status.acceptedStructureCount,
                        status.pendingStructureCount,
                    ),
                )
            status.structureProgress?.let { progress ->
                message.append(Component.literal("\n"))
                    .append(
                        seedCrackerTranslation(
                            "status.shipwreckProgress",
                            progress.acceptedIndependentEvidence,
                            progress.requiredIndependentEvidence,
                        ),
                    )
            }
        } else if (status.scope.isNether) {
            message.append(Component.literal("\n"))
                .append(
                    seedCrackerTranslation(
                        "status.netherBedrock",
                        status.acceptedNetherBedrockChunkCount,
                        status.pendingNetherBedrockChunkCount,
                    ),
                )
            status.netherSearchProgress?.let { progress ->
                val key = if (progress.paused) "status.netherProgressPaused" else "status.netherProgress"
                message.append(Component.literal("\n"))
                    .append(
                        seedCrackerTranslation(
                            key,
                            progress.formattedPercent(),
                            progress.formattedRate(),
                            progress.formattedEta(),
                        ),
                    )
            }
        }

        message.append(Component.literal("\n"))
            .append(seedCrackerTranslation("status.next"))
            .append(
                seedCrackerTranslation(
                    status.nextAction.key.removePrefix("seedcracker.guidance."),
                    *status.nextAction.arguments.toTypedArray(),
                ),
            )
        return SeedCrackerPresentation(message, severityFor(status.nextAction.kind))
    }

    private fun conflictPresentation(report: SeedCrackerConflictReport): SeedCrackerPresentation {
        val message = Component.empty()
            .append(seedCrackerTranslation("evidenceConflictHeader", report.evidence.size))
        report.evidence.forEach { evidence ->
            message.append(Component.literal("\n • "))
                .append(Component.literal(evidence.displayLabel).copyable(copyContent = evidence.id.value))
        }
        message.append(Component.literal("\n"))
            .append(seedCrackerTranslation("evidenceConflictAction"))
        return SeedCrackerPresentation(message, NotificationEvent.Severity.ERROR)
    }

    private fun clearVolatileEvidence() {
        structureObservations.clear()
        bedrockObservations.clear()
        rejectedEvidenceIds.clear()
        revisions.clear()
        dirtyChunks.clear()
        bedrockCollector.clear()
        netherSearchCursor.set(NetherBedrockSearchCursor())
        netherSearchProgress.set(null)
        netherEvidenceFingerprint.set(null)
        lastPersistedNetherCheckpointBucket.set(-1L)
        structureSearchCursor.set(null)
        structureEvidenceFingerprint.set(null)
    }

    /** A candidate belongs to exactly one frozen tracker revision and must never survive changed evidence. */
    private fun invalidateCandidate() {
        candidate.set(null)
        latestSolveResult.set(null)
    }

    private fun currentScope(): CrackScope? {
        val level = mc.level ?: return null
        val localWorldName = mc.singleplayerServer?.worldData?.levelName ?: "unknown"
        val rawServerIdentity = mc.currentServer?.ip ?: "singleplayer:$localWorldName"
        return CrackScope(
            CrackScope.fingerprintServerIdentity(rawServerIdentity),
            level.dimension().identifier().toString(),
        )
    }

    private fun presentation(
        key: String,
        severity: NotificationEvent.Severity,
        vararg arguments: String,
    ) = SeedCrackerPresentation(
        message = seedCrackerTranslation(key, *arguments),
        severity = severity,
    )

    private fun candidatePresentation(
        seedCandidate: SeedCandidate,
        key: String,
        severity: NotificationEvent.Severity,
    ): SeedCrackerPresentation {
        val source = seedCrackerTranslation("source.${seedCandidate.source.id}")
        val verification = seedCrackerTranslation(
            "verification.${seedCandidate.verification.name.lowercase()}",
        )
        val decimal = seedCandidate.seed.toString()
        val presentationKey = when (seedCandidate.kind) {
            SeedCandidateKind.NETHER_PATTERN_SEED_48 -> "netherPatternCandidate"
            SeedCandidateKind.STRUCTURE_SEED_48 -> "structureSeedCandidate"
            SeedCandidateKind.WORLD_SEED -> key
        }
        val message = seedCrackerTranslation(presentationKey, source, verification)
            .append(" ")
            .append(Component.literal(decimal).copyable(copyContent = decimal))
            .append(" ")
            .append(Component.literal(seedCandidate.hexSeed).copyable(copyContent = seedCandidate.hexSeed))
        return SeedCrackerPresentation(message, severity)
    }

    private fun severityFor(kind: GuidanceKind): NotificationEvent.Severity = when (kind) {
        GuidanceKind.WARNING -> NotificationEvent.Severity.ERROR
        GuidanceKind.RESULT -> NotificationEvent.Severity.SUCCESS
        GuidanceKind.INFO, GuidanceKind.ACTION -> NotificationEvent.Severity.INFO
    }

    private val CrackerState.localizationKey: String
        get() = when (this) {
            CrackerState.COLLECTING -> "collecting"
            CrackerState.NEEDS_ACTION -> "needsAction"
            CrackerState.SOLVING -> "solving"
            CrackerState.CANDIDATE -> "candidate"
            CrackerState.CONTRADICTED -> "contradicted"
            CrackerState.PAUSED -> "paused"
        }

    private fun String.toStableStructureBlockId(): String = substringAfter(':', this).substringBefore('[').lowercase()

    private data class RuntimeSettings(
        val structuresEnabled: Boolean = true,
        val netherBedrockEnabled: Boolean = true,
        val autoAcceptStrongEvidence: Boolean = true,
        val persistProgress: Boolean = true,
        val workerLimit: Int = DEFAULT_WORKERS,
    ) {
        val enabledTechniques: Set<CrackingTechnique>
            get() = buildSet {
                if (structuresEnabled) add(CrackingTechnique.STRUCTURES)
                if (netherBedrockEnabled) add(CrackingTechnique.NETHER_BEDROCK)
            }
    }

    private data class RuntimeSolveResult(
        val candidate: SeedCandidate? = null,
        val state: CrackerState,
        val messageKey: String? = null,
        val messageArguments: List<String> = emptyList(),
        val severity: NotificationEvent.Severity = NotificationEvent.Severity.INFO,
        val conflictReport: SeedCrackerConflictReport? = null,
        val nextStructureCursor: StructureSeedSearchCursor? = null,
    )

    private data class ScopedChunk(
        val scope: CrackScope,
        val chunk: ChunkCoordinate,
    )

    private const val CHUNK_SHIFT = 4
    private const val MIN_WORKERS = 1
    private const val MAX_WORKERS = 8
    private const val DEFAULT_WORKERS = 2
    private const val MAX_DIRTY_RESCANS_PER_TICK = 2
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val NETHER_CHECKPOINT_PREFIX_INTERVAL = 1L shl 30
    private val NETHER_PATTERN_LAYERS = setOf(NetherBedrockLayer.FLOOR.blockY, NetherBedrockLayer.ROOF.blockY)
    private val RELEVANT_STRUCTURE_BLOCKS = setOf(
        "snow_block", "redstone_torch", "chest", "ladder", "blue_terracotta", "stone_pressure_plate",
        "tripwire_hook", "redstone_wire", "dispenser", "mossy_cobblestone", "cobblestone", "cauldron",
        "crafting_table", "oak_fence", "spruce_planks", "oak_planks", "oak_stairs", "oak_trapdoor",
        "stripped_oak_log", "dark_oak_log", "dark_oak_planks", "prismarine", "prismarine_bricks",
        "dark_prismarine", "sea_lantern",
    )
}

internal fun resolveCrackerState(
    phase: SeedCrackerTrackerPhase,
    resultState: CrackerState?,
): CrackerState {
    if (phase == SeedCrackerTrackerPhase.PAUSED) return CrackerState.PAUSED
    return resultState ?: when (phase) {
        SeedCrackerTrackerPhase.INACTIVE,
        SeedCrackerTrackerPhase.COLLECTING -> CrackerState.COLLECTING

        SeedCrackerTrackerPhase.DEBOUNCING,
        SeedCrackerTrackerPhase.SOLVING -> CrackerState.SOLVING

        SeedCrackerTrackerPhase.CANDIDATE -> CrackerState.CANDIDATE
        SeedCrackerTrackerPhase.PAUSED -> CrackerState.PAUSED
        SeedCrackerTrackerPhase.FAILED -> CrackerState.CONTRADICTED
    }
}
