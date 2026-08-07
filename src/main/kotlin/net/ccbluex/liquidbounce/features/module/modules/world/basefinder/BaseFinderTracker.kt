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

import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.ccbluex.liquidbounce.utils.world.forEachSectionBlock
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong

/** A packet-safe copy of a sound event. No packet or mutable Minecraft object is retained. */
internal data class BaseFinderActivitySample(
    val soundPath: String,
    val position: BaseCoordinate,
    val timestampMillis: Long,
)

/** Revision token used to reject work completed after unload, replacement, disable, or world change. */
internal data class BaseFinderScanTicket(
    val chunk: ChunkCoordinate,
    val worldEpoch: Long,
    val revision: Long,
)

/**
 * Passive evidence acquisition for chunks already present in the client cache.
 *
 * [ChunkScanner] may call this object concurrently. All callback inputs are reduced to immutable primitives before
 * being retained. Entity iteration and bounded dirty rescans are deliberately exposed for the module's game-tick
 * handler, which keeps mutable Minecraft collections on the main thread.
 */
@Suppress("TooManyFunctions")
internal object BaseFinderTracker : ChunkScanner.BlockChangeSubscriber {

    override val shouldCallRecordBlockOnChunkUpdate: Boolean = false

    private val epoch = AtomicLong()
    private val revisions = ConcurrentHashMap<Long, Long>()
    private val dirtyChunks = ConcurrentSkipListSet<Long>()
    private val loadedChunks = ConcurrentHashMap.newKeySet<Long>()
    private val liquidUpdateChunks = ConcurrentHashMap.newKeySet<Long>()
    private val staticSnapshots = ConcurrentHashMap<Long, ChunkEvidenceSnapshot>()
    private val blockEntityStorageSignals = ConcurrentHashMap<Long, StorageSignal>()
    private val entitySignals = ConcurrentHashMap<Long, EntitiesSignal>()
    private val entityStorageSignals = ConcurrentHashMap<Long, StorageSignal>()
    private val activitySamples = ConcurrentHashMap<ActivityKey, ConcurrentLinkedDeque<ActivityRecord>>()

    val worldEpoch: Long
        get() = epoch.get()

    override fun recordBlock(pos: BlockPos, state: BlockState, cleared: Boolean) {
        val chunk = ChunkCoordinate(pos.x shr 4, pos.z shr 4)
        markDirtyNeighborhood(chunk)

        val fluid = state.fluidState
        if (!fluid.isEmpty && !fluid.isSource) {
            liquidUpdateChunks += chunk.pack()
        }
    }

    override fun chunkUpdate(chunk: LevelChunk) {
        val key = chunk.pos.pack()
        loadedChunks += key
        val ticket = beginScan(chunk.pos.toCoordinate())
        val snapshot = scanChunk(chunk)
        commitIfCurrent(ticket, snapshot)
    }

    override fun clearChunk(pos: ChunkPos) {
        val key = pos.pack()
        invalidateChunk(key)
        dirtyChunks -= key
        loadedChunks -= key
        liquidUpdateChunks -= key
        staticSnapshots.remove(key)
        blockEntityStorageSignals.remove(key)
        entitySignals.remove(key)
        entityStorageSignals.remove(key)
        activitySamples.keys.removeIf { it.chunkKey == key }
    }

    override fun clearAllChunks() {
        resetVolatile()
    }

    fun onWorldChanged(): Long {
        resetVolatile()
        return worldEpoch
    }

    fun resetVolatile() {
        epoch.incrementAndGet()
        revisions.clear()
        dirtyChunks.clear()
        loadedChunks.clear()
        liquidUpdateChunks.clear()
        staticSnapshots.clear()
        blockEntityStorageSignals.clear()
        entitySignals.clear()
        entityStorageSignals.clear()
        activitySamples.clear()
    }

    /** Runs on the game thread and processes no more than [limit] coalesced chunk changes. */
    fun processDirtyChunks(level: ClientLevel, limit: Int): List<ChunkEvidenceSnapshot> {
        if (limit <= 0) return emptyList()

        return drainDirtyChunkKeys(limit).mapNotNull { key ->
            val coordinate = key.toCoordinate()
            if (!level.hasChunk(coordinate.x, coordinate.z)) return@mapNotNull null

            val ticket = beginScan(coordinate)
            val snapshot = scanChunk(level.getChunk(coordinate.x, coordinate.z))
            if (!commitIfCurrent(ticket, snapshot)) return@mapNotNull null
            composeSnapshot(key)
        }
    }

    /** Replaces transient entity evidence using only the entities currently tracked by the client. */
    fun sampleEntities(level: ClientLevel): List<ChunkEvidenceSnapshot> {
        val accumulators = HashMap<Long, EntityAccumulator>()
        for (entity in level.entitiesForRendering()) {
            val category = BaseFinderEvidenceClassifier.entityCategory(entity) ?: continue
            val position = BaseCoordinate.of(entity.blockPosition())
            val key = position.chunk.pack()
            accumulators.getOrPut(key, ::EntityAccumulator).add(category, position)
        }

        entitySignals.clear()
        entityStorageSignals.clear()
        accumulators.forEach { (key, accumulator) ->
            val evidence = accumulator.toEvidence()
            entitySignals[key] = evidence.entities
            if (evidence.storage.weightedPoints > 0) entityStorageSignals[key] = evidence.storage
        }
        return currentSnapshots()
    }

    /** Samples client-visible block entities on the game thread without requesting or loading chunks. */
    fun sampleBlockEntities(level: ClientLevel): List<ChunkEvidenceSnapshot> {
        val sampled = HashMap<Long, StorageAccumulator>()
        for (key in loadedChunks) {
            val coordinate = key.toCoordinate()
            if (!level.hasChunk(coordinate.x, coordinate.z)) continue
            for (blockEntity in level.getChunk(coordinate.x, coordinate.z).blockEntities.values) {
                val weight = BaseFinderEvidenceClassifier.storageWeight(blockEntity.blockState)
                if (weight <= 0) continue
                sampled.getOrPut(key, ::StorageAccumulator).add(
                    weight,
                    BaseCoordinate.of(blockEntity.blockPos),
                    BuiltInRegistries.BLOCK.getKey(blockEntity.blockState.block).path,
                )
            }
        }

        blockEntityStorageSignals.clear()
        sampled.forEach { (key, accumulator) -> blockEntityStorageSignals[key] = accumulator.toSignal() }
        return currentSnapshots()
    }

    /** Thread-safe packet ingress. Repetition is aggregated only when immutable snapshots are requested. */
    fun recordActivity(sample: BaseFinderActivitySample) {
        val category = BaseFinderEvidenceClassifier.activityCategory(sample.soundPath) ?: return
        val key = ActivityKey(sample.position.chunk.pack(), category)
        val records = activitySamples.computeIfAbsent(key) { ConcurrentLinkedDeque() }
        records += ActivityRecord(sample.position, sample.timestampMillis)
        pruneActivity(records, sample.timestampMillis)
    }

    fun currentSnapshots(): List<ChunkEvidenceSnapshot> {
        val now = System.currentTimeMillis()
        val keys = HashSet<Long>(staticSnapshots.keys)
        keys += blockEntityStorageSignals.keys
        keys += entitySignals.keys
        keys += entityStorageSignals.keys
        keys += activitySamples.keys.map(ActivityKey::chunkKey)
        return keys.sorted().map { composeSnapshot(it, now) }
    }

    internal fun dirtyChunksForTest(): List<ChunkCoordinate> = dirtyChunks.map { it.toCoordinate() }

    internal fun drainDirtyChunksForTest(limit: Int): List<ChunkCoordinate> =
        drainDirtyChunkKeys(limit).map { it.toCoordinate() }

    internal fun scanTicketForTest(chunk: ChunkCoordinate): BaseFinderScanTicket = currentTicket(chunk)

    internal fun isCurrentForTest(ticket: BaseFinderScanTicket): Boolean = isCurrent(ticket)

    internal fun hasAlignedRunForTest(positions: List<BaseCoordinate>, minimum: Int): Boolean =
        hasAlignedRun(positions, minimum)

    internal fun entityEvidenceForTest(
        samples: List<Pair<BaseFinderEntityCategory, BaseCoordinate>>,
    ): BaseFinderSampledEntityEvidence = EntityAccumulator().apply {
        samples.forEach { (category, position) -> add(category, position) }
    }.toEvidence()

    internal fun scanBlocksForTest(
        blocks: List<Pair<BlockPos, BlockState>>,
        dimensionKey: String = "minecraft:overworld",
    ): ChunkEvidenceSnapshot = ChunkAccumulator(ChunkCoordinate(0, 0), dimensionKey).apply {
        blocks.forEach { (position, state) -> accept(position, state) }
    }.toSnapshot()

    private fun scanChunk(chunk: LevelChunk): ChunkEvidenceSnapshot {
        val accumulator = ChunkAccumulator(
            chunk.pos.toCoordinate(),
            chunk.level.dimension().identifier().toString(),
        )
        val mutable = BlockPos.MutableBlockPos()
        chunk.sections.forEachIndexed { sectionIndex, section ->
            if (section.hasOnlyAir()) return@forEachIndexed
            chunk.forEachSectionBlock(sectionIndex, mutable, accumulator::accept)
        }
        return accumulator.toSnapshot()
    }

    private fun composeSnapshot(key: Long, now: Long = System.currentTimeMillis()): ChunkEvidenceSnapshot {
        val base = staticSnapshots[key] ?: ChunkEvidenceSnapshot(key.toCoordinate())
        val blockStorage = blockEntityStorageSignals[key]
        val authoritativeStorage = if (
            blockStorage != null && blockStorage.weightedPoints > base.storage.weightedPoints
        ) {
            blockStorage
        } else {
            base.storage
        }
        val entityStorage = entityStorageSignals[key] ?: StorageSignal()
        return base.copy(
            storage = StorageSignal(
                authoritativeStorage.weightedPoints + entityStorage.weightedPoints,
                authoritativeStorage.anchors + entityStorage.anchors,
            ),
            entities = entitySignals[key] ?: EntitiesSignal(),
            activity = activitySignal(key, now),
            chunkTrails = chunkTrailSignal(key, base),
        )
    }

    private fun activitySignal(chunkKey: Long, now: Long): ActivitySignal {
        val repeated = activitySamples.entries.filter { it.key.chunkKey == chunkKey }.mapNotNull { (key, records) ->
            pruneActivity(records, now)
            if (records.isEmpty()) activitySamples.remove(key, records)
            val latest = records.peekLast() ?: return@mapNotNull null
            key.category to latest.takeIf { records.size >= REPEATED_ACTIVITY_COUNT }
        }.filter { it.second != null }

        return ActivitySignal(
            repeatedCategories = repeated.size,
            anchors = repeated.map { (category, record) ->
                EvidenceAnchor(record!!.position, ACTIVITY_ANCHOR_WEIGHT, "activity.$category")
            },
        )
    }

    private fun chunkTrailSignal(key: Long, snapshot: ChunkEvidenceSnapshot): ChunkTrailsSignal {
        if (key !in liquidUpdateChunks) return ChunkTrailsSignal()

        val chunk = key.toCoordinate()
        val oldNeighborCount = NEIGHBOR_OFFSETS.count { (dx, dz) ->
            val neighbor = ChunkCoordinate(chunk.x + dx, chunk.z + dz).pack()
            neighbor in loadedChunks && neighbor !in liquidUpdateChunks
        }
        val hasSeedEvidence = snapshot.storage.weightedPoints > 0 ||
            snapshot.utilities.categories.isNotEmpty() ||
            snapshot.automation.diversityPoints > 0 ||
            snapshot.structural.anchors.isNotEmpty() ||
            snapshot.geometry.anchors.isNotEmpty()
        val anchor = strongestAnchor(snapshot) ?: EvidenceAnchor(
            BaseCoordinate(chunk.x * 16 + 8, 0, chunk.z * 16 + 8),
            CHUNK_TRAIL_ANCHOR_WEIGHT,
            "chunk_trail",
        )
        return ChunkTrailsSignal(oldNeighborCount >= 2, hasSeedEvidence, listOf(anchor))
    }

    private fun strongestAnchor(snapshot: ChunkEvidenceSnapshot): EvidenceAnchor? = sequenceOf(
        snapshot.storage.anchors,
        snapshot.utilities.anchors,
        snapshot.automation.anchors,
        snapshot.structural.anchors,
        snapshot.geometry.anchors,
    ).flatten().maxByOrNull(EvidenceAnchor::weight)

    private fun beginScan(chunk: ChunkCoordinate): BaseFinderScanTicket {
        val key = chunk.pack()
        val revision = revisions.compute(key) { _, old -> (old ?: 0L) + 1L }!!
        return BaseFinderScanTicket(chunk, worldEpoch, revision)
    }

    private fun currentTicket(chunk: ChunkCoordinate): BaseFinderScanTicket {
        val revision = revisions.computeIfAbsent(chunk.pack()) { 0L }
        return BaseFinderScanTicket(chunk, worldEpoch, revision)
    }

    private fun commitIfCurrent(ticket: BaseFinderScanTicket, snapshot: ChunkEvidenceSnapshot): Boolean {
        if (!isCurrent(ticket)) return false
        val key = ticket.chunk.pack()
        staticSnapshots[key] = snapshot
        if (isCurrent(ticket)) return true

        staticSnapshots.remove(key, snapshot)
        return false
    }

    private fun isCurrent(ticket: BaseFinderScanTicket): Boolean {
        if (ticket.worldEpoch != worldEpoch) return false
        return revisions[ticket.chunk.pack()] == ticket.revision
    }

    private fun invalidateChunk(key: Long) {
        revisions.compute(key) { _, old -> (old ?: 0L) + 1L }
    }

    private fun markDirtyNeighborhood(chunk: ChunkCoordinate) {
        for (dx in -1..1) {
            for (dz in -1..1) {
                val key = ChunkCoordinate(chunk.x + dx, chunk.z + dz).pack()
                invalidateChunk(key)
                dirtyChunks += key
            }
        }
    }

    private fun drainDirtyChunkKeys(limit: Int): List<Long> {
        if (limit <= 0) return emptyList()
        val drained = ArrayList<Long>(limit)
        val iterator = dirtyChunks.iterator()
        while (iterator.hasNext() && drained.size < limit) {
            val key = iterator.next()
            if (dirtyChunks.remove(key)) drained += key
        }
        return drained
    }

    private fun pruneActivity(records: ConcurrentLinkedDeque<ActivityRecord>, now: Long) {
        val oldestAllowed = now - ACTIVITY_WINDOW_MILLIS
        while (records.peekFirst()?.timestampMillis?.let { it < oldestAllowed } == true) {
            records.pollFirst()
        }
    }

    private fun ChunkPos.toCoordinate() = ChunkCoordinate(x, z)

    private fun ChunkCoordinate.pack() = ChunkPos.pack(x, z)

    private fun Long.toCoordinate() = ChunkCoordinate(ChunkPos.getX(this), ChunkPos.getZ(this))

    private data class ActivityKey(val chunkKey: Long, val category: String)

    private data class ActivityRecord(val position: BaseCoordinate, val timestampMillis: Long)

    private class EntityAccumulator {
        private val categories = HashSet<BaseFinderEntityCategory>()
        private val anchors = ArrayList<EvidenceAnchor>()
        private var count = 0
        private var containerCount = 0
        private var hasContainer = false

        fun add(category: BaseFinderEntityCategory, position: BaseCoordinate) {
            categories += category
            count++
            hasContainer = hasContainer || category.container
            if (category.container) containerCount++
            if (anchors.size < MAX_ANCHORS_PER_FAMILY) {
                anchors += EvidenceAnchor(position, ENTITY_ANCHOR_WEIGHT, "entity.${category.name.lowercase()}")
            }
        }

        fun toEvidence(): BaseFinderSampledEntityEvidence {
            val entitySignal = EntitiesSignal(
                diversityPoints = (categories.size * 2).coerceAtMost(6),
                densityPoints = densityPoints(count, 4),
                hasContainerVehicleOrChestedMount = hasContainer,
                anchors = anchors.toList(),
            )
            val containerAnchors = anchors
                .filter { it.key.contains("container_vehicle") || it.key.contains("chested_mount") }
                .map { it.copy(weight = CONTAINER_ENTITY_STORAGE_WEIGHT, key = "storage.container_vehicle") }
            return BaseFinderSampledEntityEvidence(
                entities = entitySignal,
                storage = StorageSignal(containerCount * CONTAINER_ENTITY_STORAGE_WEIGHT, containerAnchors),
            )
        }
    }

    private class StorageAccumulator {
        private var points = 0
        private val anchors = ArrayList<EvidenceAnchor>()

        fun add(weight: Int, position: BaseCoordinate, path: String) {
            points += weight
            if (anchors.size < MAX_ANCHORS_PER_FAMILY) {
                anchors += EvidenceAnchor(position, weight, "storage.$path")
            }
        }

        fun toSignal() = StorageSignal(points, anchors.toList())
    }

    private class ChunkAccumulator(
        private val chunk: ChunkCoordinate,
        private val dimensionKey: String,
    ) {
        private var storagePoints = 0
        private var storageCount = 0
        private val storageAnchors = ArrayList<EvidenceAnchor>()
        private val utilityCategories = HashSet<String>()
        private val utilityAnchors = ArrayList<EvidenceAnchor>()
        private val automationCategories = HashSet<String>()
        private val automationCounts = HashMap<String, Int>()
        private val automationPositions = LinkedHashSet<BaseCoordinate>()
        private val automationAnchors = ArrayList<EvidenceAnchor>()
        private val structuralCounts = HashMap<String, Int>()
        private val structuralAnchors = ArrayList<EvidenceAnchor>()
        private val craftedPositions = LinkedHashSet<BaseCoordinate>()
        private val pathCounts = HashMap<String, Int>()
        private var undergroundAirCount = 0
        private var firstUndergroundAir: BaseCoordinate? = null

        fun accept(pos: BlockPos, state: BlockState) {
            if (state.isAir) {
                recordUndergroundAir(pos)
                return
            }

            val classified = BaseFinderEvidenceClassifier.classifyBlock(state)
            recordPath(classified.path)
            val coordinate = BaseCoordinate.of(pos)
            recordStorage(classified, coordinate)
            recordUtility(classified, coordinate)
            recordAutomation(classified, coordinate)
            recordStructural(classified, coordinate)
        }

        fun toSnapshot(): ChunkEvidenceSnapshot {
            val automationAligned = hasAlignedRun(automationPositions, MIN_ALIGNED_AUTOMATION)
            val artificialPattern = automationAligned || hasAlignedRun(craftedPositions, MIN_ALIGNED_CRAFTED)
            val caveDisturbance = undergroundAirCount in CAVE_AIR_RANGE && craftedPositions.size >= 3
            val falsePositives = detectFalsePositives()
            return ChunkEvidenceSnapshot(
                chunk = chunk,
                storage = StorageSignal(storagePoints, storageAnchors.toList()),
                utilities = UtilitiesSignal(utilityCategories.toSet(), utilityAnchors.toList()),
                automation = AutomationSignal(
                    diversityPoints = (automationCategories.size * 2).coerceAtMost(8),
                    densityPoints = densityPoints(automationCounts.values.sum(), 8),
                    organizedPattern = automationAligned,
                    anchors = automationAnchors.toList(),
                ),
                structural = StructuralSignal(
                    portalShape = structuralCounts.getOrDefault("portal", 0) >= 2,
                    bedGroup = structuralCounts.getOrDefault("bed", 0) >= 2,
                    infrastructure = structuralCounts.getOrDefault("infrastructure", 0) > 0,
                    decorationCluster = structuralCounts.getOrDefault("decoration", 0) >= 3,
                    anchors = structuralAnchors.toList(),
                ),
                geometry = GeometrySignal(
                    caveDisturbance = caveDisturbance,
                    artificialPattern = artificialPattern,
                    anchors = geometryAnchors(caveDisturbance, artificialPattern),
                ),
                falsePositives = falsePositives,
                dimensionKey = dimensionKey,
            )
        }

        private fun recordUndergroundAir(pos: BlockPos) {
            if (pos.y >= CAVE_MAX_Y) return
            undergroundAirCount++
            if (firstUndergroundAir == null) firstUndergroundAir = BaseCoordinate.of(pos)
        }

        private fun recordPath(path: String) {
            if (path in STRUCTURE_CONTEXT_PATHS || path.endsWith("_rail") || path.endsWith("_bricks")) {
                pathCounts.merge(path, 1, Int::plus)
            }
        }

        private fun recordStorage(classified: BaseFinderBlockClassification, position: BaseCoordinate) {
            if (classified.storageWeight <= 0) return
            storagePoints += classified.storageWeight
            storageCount++
            addAnchor(storageAnchors, position, classified.storageWeight, "storage.${classified.path}")
            craftedPositions += position
        }

        private fun recordUtility(classified: BaseFinderBlockClassification, position: BaseCoordinate) {
            val category = classified.utilityCategory ?: return
            if (utilityCategories.add(category)) {
                addAnchor(utilityAnchors, position, UTILITY_ANCHOR_WEIGHT, "utility.$category")
            }
            craftedPositions += position
        }

        private fun recordAutomation(classified: BaseFinderBlockClassification, position: BaseCoordinate) {
            val category = classified.automationCategory ?: return
            automationCategories += category
            automationCounts.merge(category, 1, Int::plus)
            automationPositions += position
            addAnchor(automationAnchors, position, AUTOMATION_ANCHOR_WEIGHT, "automation.$category")
            craftedPositions += position
        }

        private fun recordStructural(classified: BaseFinderBlockClassification, position: BaseCoordinate) {
            val category = classified.structuralCategory ?: return
            structuralCounts.merge(category, 1, Int::plus)
            addAnchor(structuralAnchors, position, STRUCTURAL_ANCHOR_WEIGHT, "structural.$category")
            craftedPositions += position
        }

        private fun geometryAnchors(cave: Boolean, artificial: Boolean): List<EvidenceAnchor> = buildList {
            if (artificial) {
                craftedPositions.firstOrNull()?.let {
                    add(EvidenceAnchor(it, GEOMETRY_ANCHOR_WEIGHT, "geometry.artificial_pattern"))
                }
            }
            if (cave) {
                firstUndergroundAir?.let {
                    add(EvidenceAnchor(it, GEOMETRY_ANCHOR_WEIGHT, "geometry.cave_disturbance"))
                }
            }
        }

        @Suppress("CognitiveComplexMethod")
        private fun detectFalsePositives(): Set<BaseFalsePositive> = buildSet {
            val beds = structuralCounts.getOrDefault("bed", 0)
            val crops = automationCounts.getOrDefault("crop", 0)
            val workstations = VILLAGE_WORKSTATIONS.sumOf { pathCounts.getOrDefault(it, 0) }
            val rails = automationCounts.getOrDefault("rail", 0)
            val portalBlocks = structuralCounts.getOrDefault("portal", 0)
            val obsidian = pathCounts.getOrDefault("obsidian", 0)
            val netherrack = pathCounts.getOrDefault("netherrack", 0)
            val goldBlocks = pathCounts.getOrDefault("gold_block", 0)
            val ruinedPortalMaterials = obsidian >= 8 && portalBlocks == 0 && netherrack > 0 && goldBlocks > 0
            val netherBricks = pathCounts.filterKeys { it.endsWith("nether_bricks") }.values.sum()
            val endCityBlocks = pathCounts.filterKeys { it.startsWith("purpur_") }.values.sum()

            if (beds >= 2 && crops >= 8 && workstations >= 2) add(BaseFalsePositive.VILLAGE)
            if (pathCounts.getOrDefault("spawner", 0) > 0 || rails >= 12 && automationCategories.size == 1) {
                add(BaseFalsePositive.MINESHAFT_OR_DUNGEON)
            }
            if (ruinedPortalMaterials && storagePoints <= 3 && utilityCategories.size <= 1) {
                add(BaseFalsePositive.RUINED_PORTAL)
            }
            if ((netherBricks >= 64 && pathCounts.getOrDefault("nether_wart", 0) >= 8) || endCityBlocks >= 64) {
                add(BaseFalsePositive.FORTRESS_BASTION_OR_END_CITY)
            }
            val generatedContext = pathCounts.getOrDefault("spawner", 0) > 0 || rails >= 4 || workstations >= 2
            if (storageCount == 1 && storagePoints <= 3 && generatedContext) {
                add(BaseFalsePositive.ISOLATED_GENERATED_LOOT_CONTAINER)
            }
            if (automationCounts.values.maxOrNull()?.let { it >= 16 } == true && automationCategories.size == 1) {
                add(BaseFalsePositive.HOMOGENEOUS_SIGNAL)
            }
        }

        private fun addAnchor(
            destination: MutableList<EvidenceAnchor>,
            position: BaseCoordinate,
            weight: Int,
            key: String,
        ) {
            if (destination.size < MAX_ANCHORS_PER_FAMILY) destination += EvidenceAnchor(position, weight, key)
        }
    }

    private fun hasAlignedRun(positions: Collection<BaseCoordinate>, minimum: Int): Boolean {
        val distinctPositions = positions.toSet()
        if (distinctPositions.size < minimum) return false
        val alongX = HashMap<Pair<Int, Int>, Int>()
        val alongZ = HashMap<Pair<Int, Int>, Int>()
        for (position in distinctPositions) {
            if (alongX.merge(position.y to position.z, 1, Int::plus)!! >= minimum) return true
            if (alongZ.merge(position.x to position.y, 1, Int::plus)!! >= minimum) return true
        }
        return false
    }

    private fun densityPoints(count: Int, maximum: Int): Int = when {
        count >= 32 -> maximum
        count >= 16 -> minOf(maximum, 6)
        count >= 8 -> minOf(maximum, 4)
        count >= 3 -> minOf(maximum, 2)
        else -> 0
    }

    private const val ACTIVITY_WINDOW_MILLIS = 10_000L
    private const val REPEATED_ACTIVITY_COUNT = 3
    private const val MAX_ANCHORS_PER_FAMILY = 32
    private const val ACTIVITY_ANCHOR_WEIGHT = 2
    private const val CHUNK_TRAIL_ANCHOR_WEIGHT = 1
    private const val ENTITY_ANCHOR_WEIGHT = 2
    private const val CONTAINER_ENTITY_STORAGE_WEIGHT = 3
    private const val UTILITY_ANCHOR_WEIGHT = 3
    private const val AUTOMATION_ANCHOR_WEIGHT = 2
    private const val STRUCTURAL_ANCHOR_WEIGHT = 3
    private const val GEOMETRY_ANCHOR_WEIGHT = 5
    private const val CAVE_MAX_Y = 32
    private const val MIN_ALIGNED_AUTOMATION = 4
    private const val MIN_ALIGNED_CRAFTED = 6
    private val CAVE_AIR_RANGE = 24..768
    private val NEIGHBOR_OFFSETS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    private val VILLAGE_WORKSTATIONS = setOf(
        "blast_furnace", "smoker", "cartography_table", "fletching_table", "grindstone", "lectern",
        "loom", "smithing_table", "stonecutter", "composter",
    )
    private val STRUCTURE_CONTEXT_PATHS = VILLAGE_WORKSTATIONS + setOf(
        "spawner", "obsidian", "netherrack", "gold_block", "nether_wart", "nether_bricks", "red_nether_bricks",
        "purpur_block", "purpur_pillar", "purpur_stairs", "purpur_slab",
    )
}

internal data class BaseFinderSampledEntityEvidence(
    val entities: EntitiesSignal,
    val storage: StorageSignal,
)
