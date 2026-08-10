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
@file:Suppress("TooManyFunctions")

package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.events.BlockChangeEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugParameter
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.copyable
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.client.variable
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.level.LevelHeightAccessor
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor

/**
 * Passively identifies probable player bases in chunks that the server has already sent to the client.
 *
 * Optional [WorldSeed] comparison regenerates vanilla overworld/nether/end columns for the configured seed and treats
 * mismatches as seed-backed evidence. Custom datapack generators are out of scope for this path.
 */
@Suppress("TooManyFunctions", "LargeClass")
object ModuleBaseFinder : ClientModule("BaseFinder", ModuleCategories.WORLD) {

    private val minimumConfidenceSetting = int("MinimumConfidence", 0, 0..100, "%").apply(::tagBy)
    internal val minimumConfidence by minimumConfidenceSetting
    internal val highSensitivity by boolean("HighSensitivity", true)

    /** Detector family toggles that feed scoring. */
    internal object Evidence : ValueGroup("Evidence") {
        internal val storage by boolean("Storage", true)
        internal val utilities by boolean("Utilities", true)
        internal val automation by boolean("Automation", true)
        internal val entities by boolean("Entities", true)
        internal val structural by boolean("Structural", true)
        internal val geometry by boolean("Geometry", true)
        internal val activity by boolean("Activity", true)
        internal val chunkTrails by boolean("ChunkTrails", true)
    }

    /**
     * Seed-backed column regen, scoring evidence, and mismatch outlines.
     * Alias [SeedCompare] keeps older module configs loadable.
     *
     * Worker/cache/rescan knobs are fixed internals — only seed, scan range, and outlines are exposed.
     */
    internal object SeedMismatch : ToggleableValueGroup(
        ModuleBaseFinder,
        "SeedMismatch",
        true,
        aliases = listOf("SeedCompare"),
    ) {
        private val worldSeedSetting = text("WorldSeed", "").onChanged {
            onServerScopedSettingsChanged()
        }
        internal val worldSeed by worldSeedSetting

        internal fun applyWorldSeed(worldSeed: String) {
            worldSeedSetting.set(worldSeed)
        }

        /**
         * Features = full noise→carvers→biome decoration from the typed seed (background server; SP+MP).
         * Base column = fast noise-column API only.
         */
        internal val backend by enumChoice("Backend", BaseFinderWorldBackend.FEATURES)
        /**
         * Max Chebyshev radius (chunks) for SeedMismatch outlines around the player.
         * Effective radius is also capped by the client's render distance so we only scan loaded terrain.
         */
        internal val scanChunks by int("ScanChunks", 12, 1..16, "chunks")

        /**
         * Also outline solid blocks whose material differs from the seed (cobblestone where stone is
         * expected) instead of only missing/extra blocks. Materials a ticked world converts between
         * (grass↔path, water↔ice, …) stay silent. Overlay only — never changes confidence.
         *
         * Requires [BaseFinderWorldBackend.FEATURES]; the base-column backend has no real materials.
         */
        internal val compareMaterials by boolean("CompareMaterials", false)
    }

    /** Complete scoring matrix. Sections stay collapsible in both ClickGUI variants. */
    internal object Scoring : ValueGroup("Scoring") {
        private val settingsByWeight: Map<BaseFinderScoreWeight, Value<Int>>

        init {
            val mutableSettings = linkedMapOf<BaseFinderScoreWeight, Value<Int>>()
            BaseFinderScoreGroup.entries.forEach { group ->
                val section = ValueGroup(group.settingName)
                BaseFinderScoreWeight.entries
                    .filter { weight -> weight.group == group }
                    .forEach { weight ->
                        mutableSettings[weight] = section.int(
                            name = weight.settingName,
                            default = weight.defaultValue,
                            range = weight.range,
                        ).onChanged {
                            onServerScopedSettingsChanged()
                        }
                    }
                tree(section)
            }
            settingsByWeight = mutableSettings
            // Interop submits the complete group in order; reset must run after the submitted sliders.
            action("ResetToDefaults") { resetToDefaults() }
        }

        internal fun snapshot(): BaseFinderScoringWeights = BaseFinderScoringWeights.fromPersistedMap(
            settingsByWeight.mapKeys { (weight, _) -> weight.persistedKey }
                .mapValues { (_, setting) -> setting.get() },
        )

        internal fun applyWeights(weights: BaseFinderScoringWeights) {
            settingsByWeight.forEach { (weight, setting) ->
                setting.set(weights[weight])
            }
        }

        internal fun resetToDefaults() {
            updateServerScopedSettingsAtomically {
                applyWeights(BaseFinderScoringWeights.DEFAULT)
            }
        }
    }

    internal object Alerts : ValueGroup("Alerts") {
        internal val notifications by boolean("Notifications", true)
        internal val chatCoordinates by boolean("ChatCoordinates", true)
    }

    private val publishedSnapshot = AtomicReference<BaseFinderRenderSnapshot?>(null)
    private val renderRevision = AtomicLong()
    private val renderBatch = AtomicReference(BaseFinderRenderBatch.EMPTY)
    private val mismatchCellsSnapshot = AtomicReference<List<SeedMismatchCell>>(emptyList())
    private val ledger by lazy { BaseFinderLedger() }
    private val announcementState = BaseFinderAnnouncementState()
    private var findings: List<BaseFinding> = emptyList()
    private var lastEvidenceFingerprint = Int.MIN_VALUE
    private var overlayTickCounter = 0
    private var overlayRefreshCursor = 0
    private var sparseAuditCursor = 0
    private val seedRuntime = BaseFinderSeedRuntime()
    private val seedDebugListener: (String) -> Unit = { message ->
        if (ModuleDebug.running) {
            logger.info("[SeedMismatch] $message")
        }
    }
    private val serverSettingsBindingDelegate = lazy {
        BaseFinderServerSettingsBinding(
            store = BaseFinderServerSettingsStore(),
            snapshot = ::currentServerSettings,
            apply = ::applyServerSettings,
            onFailure = { throwable -> logger.error("Failed to persist BaseFinder server settings", throwable) },
        )
    }
    private val serverSettingsBinding: BaseFinderServerSettingsBinding
        get() = serverSettingsBindingDelegate.value

    /**
     * Finding markers: plain boxes or shared Gaussian glow, plus confidence colors and labels.
     * Alias [GlowBox] keeps older module configs loadable.
     */
    internal object Render : ToggleableValueGroup(
        ModuleBaseFinder,
        "Render",
        true,
        aliases = listOf("GlowBox"),
    ) {
        internal val maximumDistance by int("MaximumDistance", 512, 64..2048, "blocks")
        internal val renderLimit by int("RenderLimit", 32, 1..128, "markers")

        /** Fixed vs Dynamic footprint box — mode-owned settings only appear when selected. */
        internal val boxMode = choices("BoxMode", 0) { arrayOf(FixedBox, DynamicBox) }

        object FixedBox : Mode("Fixed") {
            override val parent: ModeValueGroup<Mode>
                get() = boxMode

            internal val boxRadius by int("BoxRadius", 4, 1..16, "blocks")
            internal val boxHeight by int("BoxHeight", 6, 1..32, "blocks")
        }

        object DynamicBox : Mode("Dynamic", aliases = listOf("Dynamic box")) {
            override val parent: ModeValueGroup<Mode>
                get() = boxMode

            internal val dynamicPadding by int("DynamicPadding", 1, 0..8, "blocks")
        }

        internal val activeBoxMode: BaseFinderBoxMode
            get() = when (boxMode.activeMode) {
                is DynamicBox -> BaseFinderBoxMode.DYNAMIC
                else -> BaseFinderBoxMode.FIXED
            }

        internal val mode = choices("Mode", 0) { arrayOf(Glow, Box) }

        /** Glow ESP style — only configurable under the Glow mode (not duplicated on the Render root). */
        object Glow : Mode("Glow") {
            override val parent: ModeValueGroup<Mode>
                get() = mode

            private val styleConfig = EspGlowStyleConfig(this)

            internal val style: EspGlowStyle
                get() = styleConfig.style
        }

        /** Through-wall boxes without the shared Gaussian glow pass. */
        object Box : Mode("Box") {
            override val parent: ModeValueGroup<Mode>
                get() = mode
        }

        internal val lowConfidenceColor by color("LowConfidenceColor", Color4b(255, 186, 32))
        internal val highConfidenceColor by color("HighConfidenceColor", Color4b(255, 60, 180))

        internal object Labels : ValueGroup("Labels") {
            internal val showLabels by boolean("ShowLabels", true)
            internal val maxLabels by int("MaxLabels", 8, 1..32)
            internal val labelText by text("LabelText", "")
            internal val labelScale by float("LabelScale", 1f, 0.5f..2.5f)
            internal val showEvidenceDetails by boolean("ShowEvidenceDetails", true)
            internal val maxEvidenceDetails by int("MaxEvidenceDetails", 4, 1..8)
        }

        init {
            tree(Labels)
        }

        override fun prepareDeserialize(jsonObject: JsonObject) {
            super.prepareDeserialize(jsonObject)
            migrateLegacyBaseFinderRenderConfig(jsonObject)
        }
    }

    init {
        Evidence.tree(SeedMismatch)
        treeAll(Evidence, Scoring, Alerts, Render)
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateBaseFinderSettings(jsonObject)
    }

    override fun onEnabled() {
        val epoch = BaseFinderTracker.onWorldChanged()
        seedRuntime.onWorldChanged(epoch)
        ChunkScanner.subscribe(BaseFinderTracker)
        val level = mc.level
        if (level != null) {
            activateScope(level, epoch)
        } else {
            syncSeedRuntimeSettings()
        }
    }

    override fun onDisabled() {
        seedRuntime.setDebugListener(null)
        persistCurrentScope()
        serverSettingsBinding.unbind(persist = false)
        ChunkScanner.unsubscribe(BaseFinderTracker)
        BaseFinderTracker.resetVolatile()
        seedRuntime.onDisabled()
        findings = emptyList()
        announcementState.clear()
        lastEvidenceFingerprint = Int.MIN_VALUE
        clearVolatileRenderState()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { event ->
        persistCurrentScope()
        serverSettingsBinding.unbind(persist = false)
        findings = emptyList()
        announcementState.clear()
        lastEvidenceFingerprint = Int.MIN_VALUE
        clearVolatileRenderState()

        val epoch = BaseFinderTracker.onWorldChanged()
        seedRuntime.onWorldChanged(epoch)
        val level = event.world
        if (level != null) {
            activateScope(level, epoch)
        } else {
            syncSeedRuntimeSettings()
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val level = mc.level ?: return@handler
        if (publishedSnapshot.get() == null) activateScope(level, BaseFinderTracker.worldEpoch)
        syncSeedRuntimeSettings()
        BaseFinderTracker.processDirtyChunks(level, DIRTY_CHUNKS_PER_TICK)
        if (level.gameTime % ENTITY_SAMPLE_INTERVAL_TICKS == 0L) {
            BaseFinderTracker.sampleBlockEntities(level)
            BaseFinderTracker.sampleEntities(level)
        }
        val snapshots = BaseFinderTracker.currentSnapshots()
        tickSeedCompare(level, snapshots)
        processEvidenceIfChanged(snapshots)
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (!Evidence.activity || event.origin != TransferOrigin.INCOMING) return@handler
        val packet = event.packet as? ClientboundSoundPacket ?: return@handler
        val soundPath = BuiltInRegistries.SOUND_EVENT.getKey(packet.sound.value()).toString()
        BaseFinderTracker.recordActivity(
            BaseFinderActivitySample(
                soundPath = soundPath,
                position = BaseCoordinate(
                    baseFinderBlockCoordinate(packet.x),
                    baseFinderBlockCoordinate(packet.y),
                    baseFinderBlockCoordinate(packet.z),
                ),
                timestampMillis = System.currentTimeMillis(),
            )
        )
    }

    /**
     * ChunkScanner receives server packets, but local predicted place/break changes may not be echoed back to the
     * actor. Capture accepted client changes too so their seed comparison is immediately made stale and refreshed.
     */
    @Suppress("unused")
    private val blockChangeHandler = handler<BlockChangeEvent> { event ->
        if (!running) return@handler
        BaseFinderTracker.recordBlock(event.blockPos, event.newState, cleared = false)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val cameraPosition = event.camera.position()
        val snapshot = publishedSnapshot.get()
        if (Render.running && snapshot != null) {
            val batch = BaseFinderRenderPlanner.plan(
                BaseFinderRenderRequest.fromSnapshot(
                    snapshot = snapshot,
                    cameraPosition = cameraPosition,
                    settings = currentRenderSettings(),
                    nowMillis = System.currentTimeMillis(),
                )
            )
            renderBatch.set(batch)
            val glowStyle = (Render.mode.activeMode as? Render.Glow)?.style
            BaseFinderRenderer.renderWorld(event, batch, glowStyle)
        } else {
            renderBatch.set(BaseFinderRenderBatch.EMPTY)
        }

        if (shouldRenderSeedMismatches()) {
            val cells = mismatchCellsSnapshot.get()
            if (cells.isNotEmpty()) {
                val mismatchBatch = BaseFinderSeedMismatchRenderPlanner.plan(
                    cells = cells,
                    cameraPosition = cameraPosition,
                    settings = currentMismatchRenderSettings(),
                )
                BaseFinderRenderer.renderMismatchWorld(event, mismatchBatch)
            }
        }
    }

    @Suppress("unused")
    private val overlayHandler = handler<OverlayRenderEvent> { event ->
        if (Render.running && Render.Labels.showLabels) {
            BaseFinderRenderer.renderLabels(event, renderBatch.get())
        }
    }

    internal fun publishRenderMarkers(scope: BaseFinderRenderScope, markers: Collection<BaseFinderMarker>) {
        publishedSnapshot.set(
            BaseFinderRenderSnapshot(
                worldEpoch = scope.worldEpoch,
                serverKey = scope.serverKey,
                dimensionKey = scope.dimensionKey,
                revision = renderRevision.incrementAndGet(),
                markers = immutableCopy(markers),
            )
        )
    }

    internal fun clearVolatileRenderState() {
        publishedSnapshot.set(null)
        renderBatch.set(BaseFinderRenderBatch.EMPTY)
        mismatchCellsSnapshot.set(emptyList())
        overlayRefreshCursor = 0
        sparseAuditCursor = 0
    }

    internal fun findingsForCurrentScope(): List<BaseFinding> {
        val scope = commandScope()
        return if (isPublishedScope(scope)) {
            immutableCopy(findings)
        } else {
            ledger.load(scope.serverKey, scope.dimensionKey)
        }
    }

    internal fun exportCurrentFindings(format: BaseFinderExportFormat): Path {
        val scope = commandScope()
        if (isPublishedScope(scope)) {
            ledger.saveImmediatelyBlocking(scope.serverKey, scope.dimensionKey, findings).getOrThrow()
        }
        return ledger.exportBlocking(scope.serverKey, scope.dimensionKey, format)
    }

    internal fun exportFindings(format: BaseFinderExportFormat): Path = exportCurrentFindings(format)

    internal fun clearCurrentFindings(): Int {
        val scope = commandScope()
        val removed = findingsForCurrentScope().size
        if (isPublishedScope(scope)) {
            findings = emptyList()
            announcementState.clear()
            publishRenderMarkers(scope, emptyList())
        }
        ledger.clearBlocking(scope.serverKey, scope.dimensionKey)
        return removed
    }

    /** Clears only rebuildable seed-comparison state; persisted findings and observed block edits remain intact. */
    internal fun clearSeedComparisonCache() {
        seedRuntime.clearCache()
        mismatchCellsSnapshot.set(emptyList())
        overlayTickCounter = 0
        overlayRefreshCursor = 0
        sparseAuditCursor = 0
    }

    private fun scopeFor(level: ClientLevel, worldEpoch: Long): BaseFinderRenderScope {
        // Include SP world seed so recreating "New World" with a different seed does not reload old findings.
        val serverKey = baseFinderServerSettingsKey(
            multiplayerAddress = mc.currentServer?.ip,
            singleplayerWorldName = mc.singleplayerServer?.worldData?.levelName,
            singleplayerWorldSeed = mc.singleplayerServer?.worldGenSettings?.options()?.seed(),
        )
        return BaseFinderRenderScope(serverKey, level.dimension().identifier().toString(), worldEpoch)
    }

    private fun activateScope(level: ClientLevel, worldEpoch: Long) {
        val scope = scopeFor(level, worldEpoch)
        serverSettingsBinding.bind(scope.serverKey)
        syncSeedRuntimeSettings()
        findings = ledger.load(scope.serverKey, scope.dimensionKey)
        announcementState.clear()
        findings.forEach { announcementState.remember(it.id, it.tier.ordinal) }
        publishRenderMarkers(scope, findings.map { it.toRenderMarker() })
    }

    private fun processEvidenceIfChanged(rawSnapshots: List<ChunkEvidenceSnapshot>) {
        val snapshots = rawSnapshots.map(::applyDetectorSettings)
        val scoringWeights = Scoring.snapshot()
        val fingerprint = baseFinderEvidenceFingerprint(
            snapshots,
            minimumConfidence,
            highSensitivity,
            enabledFamilies(),
            scoringWeights,
        )
        if (fingerprint == lastEvidenceFingerprint) return
        lastEvidenceFingerprint = fingerprint

        val relevant = snapshots.filter { BaseFinderScorer.evaluate(it, scoringWeights).isNotEmpty() }
        val before = findings
        val now = System.currentTimeMillis()
        for (cluster in BaseFinderScorer.cluster(relevant)) {
            val candidate = BaseFinderScorer.scoreCluster(
                cluster,
                minimumConfidence,
                highSensitivity,
                scoringWeights,
            )
            if (!candidate.accepted) continue
            val beforeUpsert = findings
            findings = BaseFinderScorer.upsertFinding(
                findings = findings,
                candidate = candidate,
                serverKeyHash = activeServerHash(),
                dimensionKey = activeScope().dimensionKey,
                nowMillis = now,
            )
            announceChangedFinding(beforeUpsert, now)
        }

        if (findings == before) return
        val scope = activeScope()
        publishRenderMarkers(scope, findings.map { it.toRenderMarker() })
        persistCurrentScope()
    }

    private fun applyDetectorSettings(snapshot: ChunkEvidenceSnapshot) = snapshot.copy(
        storage = if (Evidence.storage) snapshot.storage else StorageSignal(),
        utilities = if (Evidence.utilities) snapshot.utilities else UtilitiesSignal(),
        automation = if (Evidence.automation) snapshot.automation else AutomationSignal(),
        entities = if (Evidence.entities) snapshot.entities else EntitiesSignal(),
        structural = if (Evidence.structural) snapshot.structural else StructuralSignal(),
        geometry = if (Evidence.geometry) snapshot.geometry else GeometrySignal(),
        activity = if (Evidence.activity) snapshot.activity else ActivitySignal(),
        chunkTrails = if (Evidence.chunkTrails) snapshot.chunkTrails else ChunkTrailsSignal(),
        seedMismatch = resolveSeedMismatch(snapshot),
    )

    private fun resolveSeedMismatch(snapshot: ChunkEvidenceSnapshot): SeedMismatchSignal =
        if (SeedMismatch.running) {
            seedRuntime.signalFor(snapshot.chunk) ?: snapshot.seedMismatch
        } else {
            SeedMismatchSignal()
        }

    private fun syncSeedRuntimeSettings() {
        seedRuntime.setDebugListener(seedDebugListener.takeIf { ModuleDebug.running })
        val generationInvalidated = seedRuntime.updateSettings(
            BaseFinderSeedCompareSettings(
                worldSeedText = SeedMismatch.worldSeed,
                enabled = SeedMismatch.running,
                backend = SeedMismatch.backend,
                workerThreads = SEED_WORKER_THREADS,
                promotionsPerTick = SEED_PROMOTIONS_PER_TICK,
                sparseSamplesPerChunk = SEED_SPARSE_SAMPLES_PER_CHUNK,
                cacheChunks = SEED_CACHE_CHUNKS,
                compareMaterials = SeedMismatch.compareMaterials,
            )
        )
        if (generationInvalidated) {
            // Drop outline cells immediately; queued/in-flight compares for the old seed/backend are discarded.
            mismatchCellsSnapshot.set(emptyList())
        }
    }

    private fun tickSeedCompare(level: ClientLevel, snapshots: List<ChunkEvidenceSnapshot>) {
        if (!seedRuntime.isActive()) {
            publishSeedCompareDebug(froze = "inactive")
            return
        }
        val dimensionKey = level.dimension().identifier().toString()
        if (BaseFinderBackgroundServer.levelKeyFor(dimensionKey) == null) {
            publishSeedCompareDebug(froze = "wrong_dim")
            return
        }

        // Build/refresh context before freezing — freezes are useless while context is missing.
        seedRuntime.tick(
            registryAccess = level.registryAccess(),
            heightAccessor = LevelHeightAccessor.create(level.minY, level.height),
            dimensionKey = dimensionKey,
        )

        val player = mc.player
        val playerChunk = player?.blockPosition()?.let {
            ChunkCoordinate(it.x shr 4, it.z shr 4)
        }
        syncFeaturesServerFocus(dimensionKey, player)
        val scanTargets = if (playerChunk != null && seedMismatchOutlinesActive()) {
            chunksInChebyshevRadius(playerChunk, seedMismatchScanRadiusChunks())
                .filter { level.hasChunk(it.x, it.z) }
        } else {
            emptyList()
        }
        seedRuntime.retainChunks(seedCompareRetentionChunks(scanTargets, snapshots))

        val froze = when {
            !seedRuntime.isContextReady() -> "wait_context"
            else -> {
                val overlayFrozen = offerNearbyOverlayCompares(level, scanTargets, dimensionKey)
                when {
                    overlayFrozen > 0 -> "overlay:$overlayFrozen"
                    offerOneSparseCompare(level, dimensionKey, snapshots) -> "sparse"
                    else -> "none"
                }
            }
        }
        // Drain queued compares onto async workers (may launch several this tick).
        seedRuntime.tick(
            registryAccess = level.registryAccess(),
            heightAccessor = LevelHeightAccessor.create(level.minY, level.height),
            dimensionKey = dimensionKey,
        )
        refreshMismatchCellsSnapshot()
        announceSeedCompareFailure()
        publishSeedCompareDebug(froze = froze, dimensionKey = dimensionKey)
    }

    private fun announceSeedCompareFailure() {
        val reason = seedRuntime.consumeFailureNotice() ?: return
        notification(
            name,
            message("seedCompareFailed", reason).string,
            NotificationEvent.Severity.ERROR,
        )
    }

    /**
     * Keep the Features background server's respawn/focus on the real player.
     * Never starts/awaits the server on the client tick thread — only syncs when already ready.
     */
    private fun syncFeaturesServerFocus(dimensionKey: String, player: LocalPlayer?) {
        if (player == null || SeedMismatch.backend != BaseFinderWorldBackend.FEATURES) return
        val server = BaseFinderBackgroundServerHost.ifReady() ?: return
        val pos = player.blockPosition()
        server.syncPlayerFocus(
            dimensionKey = dimensionKey,
            blockX = pos.x,
            blockY = pos.y,
            blockZ = pos.z,
            yaw = player.yRot,
        )
    }

    private fun publishSeedCompareDebug(
        froze: String,
        dimensionKey: String = mc.level?.dimension()?.identifier()?.toString() ?: "-",
    ) {
        if (!ModuleDebug.running) return
        val snap = seedRuntime.debugSnapshot()
        val playerChunk = mc.player?.blockPosition()?.let {
            "${it.x shr 4},${it.z shr 4}"
        } ?: "-"
        val playerSignal = mc.player?.blockPosition()?.let {
            seedRuntime.signalFor(ChunkCoordinate(it.x shr 4, it.z shr 4))
        }
        val playerScore = playerSignal?.let { signal ->
            seedMismatchDebugReadout(signal.clusterProfile, signal.phase, signal.fidelity, Scoring.snapshot())
        }
        debugParameter("Seed/Active") { snap.active }
        debugParameter("Seed/Dimension") { dimensionKey }
        debugParameter("Seed/Context") {
            when {
                snap.contextReady -> "ready"
                snap.contextBuilding -> "building"
                snap.lastFailure != null -> "failed"
                else -> "missing"
            }
        }
        debugParameter("Seed/ContextError") { snap.lastFailure ?: "-" }
        debugParameter("Seed/Jobs") { "${snap.activeJobs}/${snap.workerLimit}" }
        debugParameter("Seed/Queues") {
            "pend=${snap.pending} overlay=${snap.overlayQueued} promo=${snap.promotions} cache=${snap.cacheSize}"
        }
        debugParameter("Seed/Signals") { snap.signalCount }
        debugParameter("Seed/Last") {
            "${snap.lastEvent} ${snap.lastPhase} chunk=${snap.lastChunk} ${snap.lastCompareMs}ms"
        }
        debugParameter("Seed/Freeze") { froze }
        debugParameter("Seed/PlayerChunk") { playerChunk }
        debugParameter("Seed/CompareMaterials") { SeedMismatch.compareMaterials }
        debugParameter("Seed/PlayerSignal") {
            if (playerSignal == null) {
                "none"
            } else {
                "${playerSignal.phase}/${playerSignal.fidelity} u=${playerSignal.unexpectedSolidCount} " +
                    "m=${playerSignal.missingSolidCount} util=${playerSignal.utilityMismatchCount} " +
                    "mat=${playerSignal.materialSwapCount} " +
                    "cells=${playerSignal.cells.size} ratio=${"%.3f".format(playerSignal.mismatchRatio)}"
            }
        }
        debugParameter("Seed/StrongestComponent") { playerScore?.component ?: "-" }
        debugParameter("Seed/StrongestScore") { playerScore?.score ?: 0 }
        debugParameter("Seed/StandaloneEligible") { playerScore?.standaloneEligible ?: false }
        debugParameter("Seed/ExpectorFail") {
            MinecraftFullBaseFinderChunkExpector.lastFailure() ?: "-"
        }
        publishSeedCompareRangeDebug()
        debugParameter("Seed/OutlineCells") { mismatchCellsSnapshot.get().size }
        debugParameter("Seed/NearestMismatch") {
            mismatchCellsSnapshot.get().firstOrNull()?.debugDescription() ?: "-"
        }
        debugParameter("Seed/ColorMissing") { SEED_MISMATCH_MISSING_SOLID_COLOR.toHexString() }
        debugParameter("Seed/ColorUnexpected") { SEED_MISMATCH_UNEXPECTED_SOLID_COLOR.toHexString() }
        debugParameter("Seed/ColorUtility") { SEED_MISMATCH_UTILITY_COLOR.toHexString() }
        debugParameter("Seed/ColorMaterialSwap") { SEED_MISMATCH_MATERIAL_SWAP_COLOR.toHexString() }
    }

    private fun publishSeedCompareRangeDebug() {
        debugParameter("Seed/ViewDistance") {
            // BG server ticket floor only — scan coverage is Seed/ScanRadius, not this.
            val target = MinecraftFullBaseFinderChunkExpector.targetViewDistance()
            val active = BaseFinderBackgroundServerHost.currentViewDistance()
            when {
                active == null -> "gen=$target (server down)"
                active == target -> "gen=$active"
                else -> "gen=$active→$target (restart pending)"
            }
        }
        debugParameter("Seed/ScanRadius") {
            val radius = seedMismatchScanRadiusChunks()
            "${radius}ch (~${seedMismatchMaxDistanceBlocks(radius).toInt()}m) cap=${SeedMismatch.scanChunks}"
        }
    }

    /**
     * Freezes loaded scan-ring chunks on the main thread (full column height, bedrock→sky).
     * Expected-column regen + compare run asynchronously afterward.
     *
     * @return number of chunks frozen this call
     */
    private fun offerNearbyOverlayCompares(
        level: ClientLevel,
        scanTargets: List<ChunkCoordinate>,
        dimensionKey: String,
    ): Int {
        if (!seedMismatchOutlinesActive() || scanTargets.isEmpty()) return 0
        overlayTickCounter++
        val rescanTick = overlayTickCounter % SEED_OVERLAY_RESCAN_INTERVAL_TICKS == 0
        val locals = BaseFinderSeedComparator.allChunkLocals()
        val budget = SEED_FREEZES_PER_TICK
        // Always prefer the player's chunk (index 0); rotate only the surrounding ring.
        val ordered = prioritizedOverlayChunks(scanTargets, ringStart = overlayRefreshCursor)
        val playerOverlayChunk = scanTargets.first()
        var frozen = 0
        var refreshedRingChunks = 0
        for (chunk in ordered) {
            if (frozen >= budget) break
            val ticket = BaseFinderTracker.ticketFor(chunk)
            // Only skip when a fresh full overlay already exists (sparse must not block).
            if (
                seedRuntime.hasOverlayWorkForTicket(ticket) ||
                seedRuntime.hasOverlaySignalForTicket(ticket) && !rescanTick
            ) {
                continue
            }
            // Full height: omit Y window so freeze uses chunk.minY .. chunk.minY+height.
            val observed = freezeSeedCompareObservation(
                level = level,
                chunk = chunk,
                locals = locals,
            ) ?: continue
            seedRuntime.offer(
                BaseFinderSeedCompareOffer(
                    ticket = ticket,
                    dimensionKey = dimensionKey,
                    observed = observed,
                    heuristicPriority = true,
                    overlayLocals = locals,
                    clientObservedUpdates = BaseFinderTracker.seedMismatchUpdatePositionsFor(chunk),
                )
            )
            frozen++
            if (chunk != playerOverlayChunk) refreshedRingChunks++
            if (ModuleDebug.running) {
                logger.info(
                    "[SeedMismatch] freeze overlay chunk=${chunk.x},${chunk.z} " +
                        "cols=${locals.size} y=${observed.minY}..${observed.minY + observed.height - 1}",
                )
            }
        }
        if (rescanTick) {
            overlayRefreshCursor = advanceOverlayRefreshCursor(
                overlayRefreshCursor,
                scanTargets.size - 1,
                refreshedRingChunks,
            )
        }
        return frozen
    }

    /** Freezes at most one sparse audit/priority chunk that still needs a fresh signal. */
    private fun offerOneSparseCompare(
        level: ClientLevel,
        dimensionKey: String,
        snapshots: List<ChunkEvidenceSnapshot>,
    ): Boolean {
        if (snapshots.isEmpty()) return false
        val priorityChunks = priorityChunksFrom(snapshots)
        val candidates = selectSparseCompareCandidates(
            snapshots = snapshots,
            priorityChunks = priorityChunks,
            auditOffset = sparseAuditCursor,
            auditLimit = SEED_SPARSE_AUDIT_WINDOW,
        )
        val auditChunkCount = snapshots.count { it.chunk !in priorityChunks }
        sparseAuditCursor = advanceSparseAuditCursor(
            cursor = sparseAuditCursor,
            auditChunkCount = auditChunkCount,
            auditLimit = SEED_SPARSE_AUDIT_WINDOW,
        )
        for (chunk in candidates) {
            if (tryOfferSparseChunk(level, chunk, dimensionKey, priority = chunk in priorityChunks)) {
                return true
            }
        }
        return false
    }

    private fun priorityChunksFrom(snapshots: Collection<ChunkEvidenceSnapshot>): Set<ChunkCoordinate> {
        val priorityChunks = LinkedHashSet<ChunkCoordinate>()
        for (snapshot in snapshots) {
            if (hasHeuristicPriority(snapshot)) {
                priorityChunks += snapshot.chunk
            }
        }
        return priorityChunks
    }

    private fun tryOfferSparseChunk(
        level: ClientLevel,
        chunk: ChunkCoordinate,
        dimensionKey: String,
        priority: Boolean,
    ): Boolean {
        val ticket = BaseFinderTracker.ticketFor(chunk)
        if (seedRuntime.hasSignalForTicket(ticket) || seedRuntime.hasSparseWorkForTicket(ticket)) return false
        // Reserve the player ring only while Debug's overlay is actually producing dense comparisons.
        val playerChunk = mc.player?.blockPosition()?.let { ChunkCoordinate(it.x shr 4, it.z shr 4) }
        if (
            seedMismatchSparseChunkReserved(
                chunk = chunk,
                playerChunk = playerChunk,
                scanRadius = seedMismatchScanRadiusChunks(),
                overlayActive = seedMismatchOutlinesActive(),
            )
        ) {
            return false
        }
        val observed = freezeSeedCompareObservation(
            level = level,
            chunk = chunk,
            sampleCount = SEED_SPARSE_SAMPLES_PER_CHUNK,
            full = false,
        ) ?: return false
        seedRuntime.offer(
            BaseFinderSeedCompareOffer(
                ticket = ticket,
                dimensionKey = dimensionKey,
                observed = observed,
                heuristicPriority = priority,
                clientObservedUpdates = BaseFinderTracker.seedMismatchUpdatePositionsFor(chunk),
            )
        )
        if (ModuleDebug.running) {
            logger.info(
                "[SeedMismatch] freeze sparse chunk=${chunk.x},${chunk.z} " +
                    "cols=${observed.columns.size} priority=$priority",
            )
        }
        return true
    }

    /** Outlines require BaseFinder + SeedMismatch + ModuleDebug (no separate Show Outlines toggle). */
    private fun seedMismatchOutlinesActive(): Boolean =
        seedMismatchOverlayEnabled(running, SeedMismatch.running, ModuleDebug.running)

    private fun shouldRenderSeedMismatches(): Boolean =
        seedMismatchOutlinesActive() && seedRuntime.isActive()

    private fun refreshMismatchCellsSnapshot() {
        if (!shouldRenderSeedMismatches()) {
            mismatchCellsSnapshot.set(emptyList())
            return
        }
        val player = mc.player ?: run {
            mismatchCellsSnapshot.set(emptyList())
            return
        }
        val playerPos = player.position()
        val maxDistSq = seedMismatchMaxDistanceBlocks(seedMismatchScanRadiusChunks()).let { it * it }
        val cells = ArrayList<SeedMismatchCell>(SEED_MISMATCH_RENDER_LIMIT)
        for ((_, signal) in seedRuntime.publishedSignals()) {
            for (cell in signal.cells) {
                val dx = cell.position.x + 0.5 - playerPos.x
                val dy = cell.position.y + 0.5 - playerPos.y
                val dz = cell.position.z + 0.5 - playerPos.z
                if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                    cells += cell
                }
            }
        }
        if (cells.size > 1) {
            cells.sortBy { cell ->
                val dx = cell.position.x + 0.5 - playerPos.x
                val dy = cell.position.y + 0.5 - playerPos.y
                val dz = cell.position.z + 0.5 - playerPos.z
                dx * dx + dy * dy + dz * dz
            }
        }
        val limited = if (cells.size > SEED_MISMATCH_RENDER_LIMIT) {
            cells.subList(0, SEED_MISMATCH_RENDER_LIMIT)
        } else {
            cells
        }
        mismatchCellsSnapshot.set(if (limited.isEmpty()) emptyList() else java.util.List.copyOf(limited))
    }

    private fun currentMismatchRenderSettings(): SeedMismatchRenderSettings {
        val maxDistance = seedMismatchMaxDistanceBlocks(seedMismatchScanRadiusChunks())
        return SeedMismatchRenderSettings(
            maximumDistance = maxDistance,
            renderLimit = SEED_MISMATCH_RENDER_LIMIT,
            missingSolidColor = SEED_MISMATCH_MISSING_SOLID_COLOR,
            unexpectedSolidColor = SEED_MISMATCH_UNEXPECTED_SOLID_COLOR,
            utilityMismatchColor = SEED_MISMATCH_UTILITY_COLOR,
            materialSwapColor = SEED_MISMATCH_MATERIAL_SWAP_COLOR,
        )
    }

    /**
     * Chebyshev radius used for overlay freeze/compare. Never exceeds client render distance
     * (unloaded chunks cannot be frozen) and never exceeds [SeedMismatch.scanChunks].
     */
    private fun seedMismatchScanRadiusChunks(): Int {
        val clientChunks = runCatching { mc.options.getEffectiveRenderDistance() }
            .getOrDefault(SeedMismatch.scanChunks)
        return minOf(SeedMismatch.scanChunks, clientChunks).coerceIn(1, 16)
    }

    private fun hasHeuristicPriority(snapshot: ChunkEvidenceSnapshot): Boolean =
        snapshot.storage.weightedPoints > 0 ||
            snapshot.utilities.categories.isNotEmpty() ||
            snapshot.automation.diversityPoints > 0 ||
            snapshot.geometry.anchors.isNotEmpty() ||
            snapshot.structural.anchors.isNotEmpty()

    private fun announceChangedFinding(previous: List<BaseFinding>, now: Long) {
        val previousById = previous.associateBy(BaseFinding::id)
        val changed = findings.firstOrNull { finding ->
            finding.lastSeenAtMillis == now && previousById[finding.id] != finding
        } ?: return
        if (!announcementState.shouldAnnounce(changed.id, changed.tier.ordinal)) return

        if (Alerts.notifications) {
            notification(
                name,
                message("found", changed.confidence, changed.anchor.x, changed.anchor.y, changed.anchor.z),
                NotificationEvent.Severity.INFO,
            )
        }
        if (Alerts.chatCoordinates) {
            val coordinates = "${changed.anchor.x} ${changed.anchor.y} ${changed.anchor.z}"
            val evidence = changed.evidence.sortedByDescending(EvidenceSummary::score)
                .take(2)
                .joinToString(" · ") {
                    baseFinderFamilyScoreLabel(familyLabel(it.family), it.score, it.family.showFamilyScore)
                }
            chat(
                message(
                    "coordinates",
                    variable(coordinates).copyable(copyContent = coordinates),
                    variable("${changed.confidence}%"),
                    variable(evidence),
                ),
                this,
            )
        }
    }

    private fun activeScope(): BaseFinderRenderScope =
        publishedSnapshot.get()?.let {
            BaseFinderRenderScope(it.serverKey, it.dimensionKey, it.worldEpoch)
        } ?: error("BaseFinder has no active scope")

    private fun activeServerHash(): String = ledger.hashScopeKey(activeScope().serverKey)

    private fun persistCurrentScope() {
        val scope = publishedSnapshot.get()?.let {
            BaseFinderRenderScope(it.serverKey, it.dimensionKey, it.worldEpoch)
        }
        if (scope != null) {
            ledger.save(scope.serverKey, scope.dimensionKey, findings)
        }
        if (serverSettingsBindingDelegate.isInitialized()) {
            serverSettingsBinding.persist()
        }
    }

    private fun currentServerSettings() = BaseFinderServerSettings(
        worldSeed = SeedMismatch.worldSeed,
        scoringWeights = Scoring.snapshot(),
    )

    private fun applyServerSettings(settings: BaseFinderServerSettings) {
        SeedMismatch.applyWorldSeed(settings.worldSeed)
        Scoring.applyWeights(settings.scoringWeights)
        lastEvidenceFingerprint = Int.MIN_VALUE
    }

    private fun onServerScopedSettingsChanged() {
        lastEvidenceFingerprint = Int.MIN_VALUE
        if (serverSettingsBindingDelegate.isInitialized()) {
            serverSettingsBinding.changed()
        }
    }

    private fun updateServerScopedSettingsAtomically(action: () -> Unit) {
        if (serverSettingsBindingDelegate.isInitialized()) {
            serverSettingsBinding.updateAtomically(action)
        } else {
            action()
        }
    }

    private fun BaseFinding.toRenderMarker() = BaseFinderMarker(
        id = id,
        anchor = anchor,
        confidence = confidence,
        topEvidenceKeys = evidence.sortedByDescending(EvidenceSummary::score)
            .take(2)
            .map { baseFinderFamilyScoreLabel(familyLabel(it.family), it.score, it.family.showFamilyScore) },
        updatedAtMillis = lastSeenAtMillis,
        evidenceDetails = evidence.sortedByDescending(EvidenceSummary::score)
            .map { summary ->
                baseFinderLabelEvidence(
                    summary = summary,
                    family = familyLabel(summary.family),
                    legacyUnavailable = message("breakdown.unavailable").string,
                    contributionLabel = ::contributionLabel,
                    observationText = ::contributionObservationText,
                )
            },
        bounds = bounds,
    )

    private fun currentRenderSettings() = BaseFinderRenderSettings(
        minimumConfidence = minimumConfidence,
        maximumDistance = Render.maximumDistance.toDouble(),
        renderLimit = Render.renderLimit,
        boxRadius = Render.FixedBox.boxRadius.toDouble(),
        boxHeight = Render.FixedBox.boxHeight.toDouble(),
        boxMode = Render.activeBoxMode,
        dynamicPadding = Render.DynamicBox.dynamicPadding,
        lowConfidenceColor = Render.lowConfidenceColor,
        highConfidenceColor = Render.highConfidenceColor,
        showLabels = Render.Labels.showLabels,
        maxLabels = Render.Labels.maxLabels,
        labelScale = Render.Labels.labelScale,
        showEvidenceDetails = Render.Labels.showEvidenceDetails,
        maxEvidenceDetails = Render.Labels.maxEvidenceDetails,
        baseLabel = Render.Labels.labelText.ifBlank { message("label.base").string },
        unknownEvidenceLabel = message("family.unknown").string,
        distanceSuffix = message("label.blocks").string,
    )

    private fun commandScope(): BaseFinderRenderScope {
        val level = mc.level ?: error("BaseFinder requires an active world")
        return scopeFor(level, BaseFinderTracker.worldEpoch)
    }

    private fun isPublishedScope(scope: BaseFinderRenderScope): Boolean = publishedSnapshot.get()?.let {
        it.serverKey == scope.serverKey && it.dimensionKey == scope.dimensionKey
    } == true

    private fun enabledFamilies(): Set<BaseSignalFamily> = listOfNotNull(
        BaseSignalFamily.STORAGE.takeIf { Evidence.storage },
        BaseSignalFamily.UTILITIES.takeIf { Evidence.utilities },
        BaseSignalFamily.AUTOMATION.takeIf { Evidence.automation },
        BaseSignalFamily.ENTITIES.takeIf { Evidence.entities },
        BaseSignalFamily.STRUCTURAL.takeIf { Evidence.structural },
        BaseSignalFamily.GEOMETRY.takeIf { Evidence.geometry },
        BaseSignalFamily.SEED_MISMATCH.takeIf { SeedMismatch.running },
        BaseSignalFamily.ACTIVITY.takeIf { Evidence.activity },
        BaseSignalFamily.CHUNK_TRAILS.takeIf { Evidence.chunkTrails },
    ).toSet()

    private fun familyLabel(family: BaseSignalFamily): String =
        message("family.${family.name.lowercase()}").string

    private fun contributionLabel(key: String): String = when {
        key.endsWith(".family_cap") -> message("contribution.family_cap").string
        key.startsWith("storage.") && key != "storage.weighted_points" ->
            message("contribution.storage.block", evidenceLabel(key)).string
        else -> message("contribution.$key").string
    }

    private fun contributionObservationText(contribution: ScoreContribution): String? {
        val observations = contribution.observations ?: return null
        val messageKey = baseFinderObservationMessageKey(contribution.key, observations) ?: return null
        return message(messageKey, observations).string
    }

    private fun evidenceLabel(key: String): String = key.substringAfter('.', key)
        .replace('_', ' ')
        .replaceFirstChar { character -> character.titlecase() }

    internal fun <T> immutableCopy(source: Collection<T>): List<T> = java.util.List.copyOf(source)
}

internal fun baseFinderBlockCoordinate(value: Double): Int = floor(value).toInt()

internal fun seedMismatchOverlayEnabled(baseFinder: Boolean, seedMismatch: Boolean, debug: Boolean): Boolean =
    baseFinder && seedMismatch && debug

internal fun baseFinderFamilyScoreLabel(family: String, score: Int, showScore: Boolean = true): String =
    if (showScore) "$family +$score" else family

internal fun baseFinderLabelEvidence(
    summary: EvidenceSummary,
    family: String,
    legacyUnavailable: String,
    contributionLabel: (String) -> String,
    observationText: (ScoreContribution) -> String?,
): BaseFinderLabelEvidence {
    val contributions = summary.contributions ?: return BaseFinderLabelEvidence(
        family = family,
        score = summary.score,
        detections = listOf(legacyUnavailable),
        showFamilyScore = summary.family.showFamilyScore,
    )
    return BaseFinderLabelEvidence(
        family = family,
        score = summary.score,
        detections = emptyList(),
        contributions = contributions.map { contribution ->
            BaseFinderLabelContribution(
                label = contributionLabel(contribution.key),
                score = contribution.score,
                observationText = observationText(contribution),
            )
        },
        showFamilyScore = summary.family.showFamilyScore,
    )
}

internal fun baseFinderObservationMessageKey(key: String, observations: Int): String? {
    val unit = when {
        key in SEED_MISMATCH_BLOCK_CONTRIBUTIONS -> "block"
        key == "seed_mismatch.component_size" -> "cell"
        key == "seed_mismatch.horizontal_spread" -> "column"
        key.startsWith("storage.") -> "point"
        key.startsWith("utility.") || key.startsWith("activity.") -> "category"
        key in POINT_CONTRIBUTIONS -> "point"
        else -> return null
    }
    val pluralUnit = when (unit) {
        "category" -> "categories"
        else -> "${unit}s"
    }
    return "observation.${if (observations == 1) unit else pluralUnit}"
}

internal data class SeedMismatchDebugReadout(
    val component: String,
    val score: Int,
    val standaloneEligible: Boolean,
)

internal fun seedMismatchDebugReadout(
    profile: SeedMismatchClusterProfile,
    phase: SeedComparePhase,
    fidelity: ExpectedTerrainFidelity,
    scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
): SeedMismatchDebugReadout {
    val assessment = BaseFinderSeedEvidenceScorer.assess(profile, phase, fidelity, scoringWeights)
    return SeedMismatchDebugReadout(
        component = "cells=${profile.cellCount} cols=${profile.horizontalColumnCount} " +
            "u=${profile.unexpectedSolidCount} m=${profile.missingSolidCount} util=${profile.utilityMismatchCount}",
        score = assessment.subtotal,
        standaloneEligible = assessment.standaloneEligible,
    )
}

private val SEED_MISMATCH_BLOCK_CONTRIBUTIONS = setOf(
    "seed_mismatch.unexpected_solid",
    "seed_mismatch.missing_solid",
    "seed_mismatch.utility_mismatch",
)

private val POINT_CONTRIBUTIONS = setOf(
    "automation.diversity",
    "automation.density",
    "entity.diversity",
    "entity.density",
)

internal fun baseFinderEvidenceFingerprint(
    snapshots: List<ChunkEvidenceSnapshot>,
    minimumConfidence: Int,
    highSensitivity: Boolean,
    enabledFamilies: Set<BaseSignalFamily>,
    scoringWeights: BaseFinderScoringWeights = BaseFinderScoringWeights.DEFAULT,
): Int = listOf(snapshots, minimumConfidence, highSensitivity, enabledFamilies, scoringWeights).hashCode()

internal fun baseFinderServerSettingsKey(
    multiplayerAddress: String?,
    singleplayerWorldName: String?,
    singleplayerWorldSeed: Long?,
): String {
    if (!multiplayerAddress.isNullOrBlank()) return multiplayerAddress

    val worldName = singleplayerWorldName ?: "unknown"
    return if (singleplayerWorldSeed == null) {
        "singleplayer:$worldName"
    } else {
        "singleplayer:$worldName:$singleplayerWorldSeed"
    }
}

/** Coordinates server-scoped profile loading while suppressing partial saves during bulk application. */
internal class BaseFinderServerSettingsBinding(
    private val store: BaseFinderServerSettingsStore,
    private val snapshot: () -> BaseFinderServerSettings,
    private val apply: (BaseFinderServerSettings) -> Unit,
    private val onFailure: (Throwable) -> Unit = {},
) {
    private var boundServerKey: String? = null
    private var applying = false

    fun bind(serverKey: String) {
        if (serverKey == boundServerKey) return
        persist()
        val legacyCandidate = snapshot()
        val settings = runCatching {
            store.loadOrInitialize(serverKey, legacyCandidate)
        }.getOrElse { throwable ->
            onFailure(throwable)
            legacyCandidate
        }
        boundServerKey = serverKey
        applyWithoutPersistence { apply(settings) }
    }

    fun changed() {
        if (!applying) persist()
    }

    fun updateAtomically(update: () -> Unit) {
        applyWithoutPersistence(update)
        persist()
    }

    fun persist() {
        val serverKey = boundServerKey ?: return
        runCatching { store.save(serverKey, snapshot()) }.onFailure(onFailure)
    }

    fun unbind(persist: Boolean = true) {
        if (persist) persist()
        boundServerKey = null
    }

    private fun applyWithoutPersistence(block: () -> Unit) {
        val wasApplying = applying
        applying = true
        try {
            block()
        } finally {
            applying = wasApplying
        }
    }
}

internal fun migrateBaseFinderSettings(jsonObject: JsonObject) {
    migrateLegacyBaseFinderSensitivity(jsonObject)
    migrateBaseFinderGroupedSettings(jsonObject)
}

internal fun migrateLegacyBaseFinderSensitivity(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val valuesByName = storedValues
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .associateBy { it["name"]?.asString.orEmpty() }
    if ("HighSensitivity" in valuesByName) return

    val legacyMinimumConfidence = valuesByName["MinimumConfidence"] ?: return
    val storedConfidence = legacyMinimumConfidence["value"]
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asInt
        ?: return
    if (storedConfidence == LEGACY_BASE_FINDER_MINIMUM_CONFIDENCE) {
        legacyMinimumConfidence.addProperty("value", 0)
    }
}

/**
 * Moves flat legacy BaseFinder values into Evidence / Evidence.SeedMismatch / Alerts groups,
 * and folds the old root SeedCompare / SeedMismatch groups and Evidence.SeedMismatch toggle into the nested group.
 * Obsolete Performance / SeedMismatch tuning knobs are dropped (hardcoded defaults now).
 */
internal fun migrateBaseFinderGroupedSettings(jsonObject: JsonObject) {
    val root = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val migrator = BaseFinderSettingsMigrator(root)
    migrator.renameRootGroup("SeedCompare", "SeedMismatch")
    migrator.renameRootGroup("GlowBox", "Render")
    migrator.foldLegacySeedMismatchToggle()
    migrator.moveInto(
        "Evidence",
        listOf(
            "Storage",
            "Utilities",
            "Automation",
            "Entities",
            "Structural",
            "Geometry",
            "Activity",
            "ChunkTrails",
        ),
    )
    migrator.moveInto("SeedMismatch", listOf("WorldSeed"))
    migrator.moveGroupInto("Evidence", "SeedMismatch")
    // ScanChunks used to mean spiral count (default 9 ≈ 3×3). It is now a Chebyshev radius cap.
    migrator.bumpLegacyScanChunksCountToRadius()
    migrator.dropRoot(
        "Performance",
        "DirtyChunksPerTick",
        "EntitySampleInterval",
        "FreezesPerTick",
        "WorkerThreads",
        "PromotionsPerTick",
        "SparseSamplesPerChunk",
        "CacheChunks",
        "OverlayYRadius",
        "OverlayRescanInterval",
        "OverlaySamplesPerChunk",
        "MismatchRenderLimit",
        "MismatchMaxDistance",
    )
    migrator.dropFromNestedGroup("Evidence", "SeedMismatch", listOf(
            "ShowOutlines",
            "ShowMismatches",
            "FreezesPerTick",
            "WorkerThreads",
            "PromotionsPerTick",
            "SparseSamplesPerChunk",
            "CacheChunks",
            "OverlayYRadius",
            "OverlayRescanInterval",
            "OverlaySamplesPerChunk",
            "MismatchRenderLimit",
            "MismatchMaxDistance",
            "MissingSolidColor",
            "UnexpectedSolidColor",
            "UtilityMismatchColor",
    ))
    migrator.dropNested("Render", listOf("Pulse"))
    migrator.dropNested("GlowBox", listOf("Pulse"))
    migrator.moveInto("Alerts", listOf("Notifications", "ChatCoordinates"))
}

private class BaseFinderSettingsMigrator(private val root: JsonArray) {
    private val byName = root.filter { it.isJsonObject }.map { it.asJsonObject }
        .associateBy { it["name"]?.asString.orEmpty() }
        .toMutableMap()

    fun renameRootGroup(from: String, to: String) {
        if (to in byName || from !in byName) return
        val group = byName.remove(from) ?: return
        group.addProperty("name", to)
        byName[to] = group
    }

    fun foldLegacySeedMismatchToggle() {
        val legacyToggle = takeNestedBoolean("Evidence", "SeedMismatch") ?: takeRootBoolean("SeedMismatch")
        val legacyEnabled = legacyToggle
            ?.get("value")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
            ?: return
        val nested = groupChildren("SeedMismatch")
        if ("Enabled" in nestedNames(nested)) return
        nested.add(JsonObject().apply {
            addProperty("name", "Enabled")
            addProperty("value", legacyEnabled)
        })
    }

    fun moveGroupInto(parentGroupName: String, groupName: String) {
        val moving = takeRoot(groupName) ?: return
        val parent = groupChildren(parentGroupName)
        val existing = parent.filter { it.isJsonObject }.map { it.asJsonObject }
            .firstOrNull { it["name"]?.asString == groupName }
        if (existing == null) {
            parent.add(moving)
            return
        }

        val existingChildren = existing["value"]?.takeIf { it.isJsonArray }?.asJsonArray
        val movingChildren = moving["value"]?.takeIf { it.isJsonArray }?.asJsonArray
        if (existingChildren == null || movingChildren == null) {
            parent.remove(existing)
            parent.add(moving)
            return
        }

        val present = nestedNames(existingChildren).toMutableSet()
        movingChildren.filter { it.isJsonObject }.forEach { child ->
            val name = child.asJsonObject["name"]?.asString
            if (name == null || present.add(name)) existingChildren.add(child)
        }
    }

    fun moveInto(groupName: String, names: Collection<String>) {
        val nested = groupChildren(groupName)
        val present = nestedNames(nested)
        names.forEach { name ->
            if (name in present) {
                takeRoot(name)
            } else {
                takeRoot(name)?.let(nested::add)
            }
        }
    }

    fun dropRoot(vararg names: String) {
        names.forEach { takeRoot(it) }
    }

    fun dropNested(groupName: String, names: Collection<String>) {
        names.forEach { takeNested(groupName, it) }
    }

    fun dropFromNestedGroup(parentGroupName: String, groupName: String, names: Collection<String>) {
        val nested = nestedGroupChildren(parentGroupName, groupName) ?: return
        names.forEach { name ->
            val child = nested.filter { it.isJsonObject }.map { it.asJsonObject }
                .firstOrNull { it["name"]?.asString == name }
                ?: return@forEach
            nested.remove(child)
        }
    }

    /**
     * Old ScanChunks default (9) meant "spiral count" (~3×3). Radius mode needs a higher default so
     * client render distance 10 is not capped by the legacy count.
     */
    fun bumpLegacyScanChunksCountToRadius() {
        val nested = nestedGroupChildren("Evidence", "SeedMismatch") ?: return
        val entry = nested.filter { it.isJsonObject }.map { it.asJsonObject }
            .firstOrNull { it["name"]?.asString == "ScanChunks" }
            ?: return
        val value = entry["value"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
            ?: return
        if (value == LEGACY_SCAN_CHUNKS_SPIRAL_COUNT) {
            entry.addProperty("value", DEFAULT_SCAN_CHUNKS_RADIUS)
        }
    }

    private fun takeRoot(name: String): JsonObject? {
        val entry = byName.remove(name) ?: return null
        root.remove(entry)
        return entry
    }

    private fun takeRootBoolean(name: String): JsonObject? {
        val entry = byName[name] ?: return null
        val value = entry["value"] ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) return null
        return takeRoot(name)
    }

    private fun takeNestedBoolean(groupName: String, childName: String): JsonObject? {
        val group = byName[groupName] ?: return null
        val nested = group["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val child = nested.filter { it.isJsonObject }.map { it.asJsonObject }
            .firstOrNull { it["name"]?.asString == childName }
            ?: return null
        val value = child["value"] ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) return null
        nested.remove(child)
        return child
    }

    private fun takeNested(groupName: String, childName: String): JsonObject? {
        val group = byName[groupName] ?: return null
        val nested = group["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val child = nested.filter { it.isJsonObject }.map { it.asJsonObject }
            .firstOrNull { it["name"]?.asString == childName }
            ?: return null
        nested.remove(child)
        return child
    }

    private fun groupChildren(groupName: String): JsonArray {
        val existing = byName[groupName]
        if (existing != null) {
            val value = existing["value"]
            if (value != null && value.isJsonArray) return value.asJsonArray
            val nested = JsonArray()
            existing.add("value", nested)
            return nested
        }
        val nested = JsonArray()
        val group = JsonObject().apply {
            addProperty("name", groupName)
            add("value", nested)
        }
        root.add(group)
        byName[groupName] = group
        return nested
    }

    private fun nestedGroupChildren(parentGroupName: String, groupName: String): JsonArray? {
        val parent = byName[parentGroupName] ?: return null
        val parentChildren = parent["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val group = parentChildren.filter { it.isJsonObject }.map { it.asJsonObject }
            .firstOrNull { it["name"]?.asString == groupName }
            ?: return null
        return group["value"]?.takeIf { it.isJsonArray }?.asJsonArray
    }

    private fun nestedNames(group: JsonArray): Set<String> =
        group.filter { it.isJsonObject }.mapNotNull { it.asJsonObject["name"]?.asString }.toSet()
}

private const val LEGACY_BASE_FINDER_MINIMUM_CONFIDENCE = 65
private const val LEGACY_SCAN_CHUNKS_SPIRAL_COUNT = 9
private const val DEFAULT_SCAN_CHUNKS_RADIUS = 12

/** Fixed BaseFinder evidence / seed-compare tuning (not exposed in ClickGUI). */
private const val DIRTY_CHUNKS_PER_TICK = 2
private const val ENTITY_SAMPLE_INTERVAL_TICKS = 20
private const val SEED_WORKER_THREADS = DEFAULT_BASE_FINDER_SEED_WORKER_THREADS
private const val SEED_PROMOTIONS_PER_TICK = 1
private const val SEED_SPARSE_SAMPLES_PER_CHUNK = 16
private const val SEED_SPARSE_AUDIT_WINDOW = 2
/** Packed expected columns are large; keep only a small LRU around the scan ring. */
private const val SEED_CACHE_CHUNKS = 64
private const val SEED_FREEZES_PER_TICK = 4
private const val SEED_OVERLAY_RESCAN_INTERVAL_TICKS = 40
private const val SEED_MISMATCH_RENDER_LIMIT = 2048
private val SEED_MISMATCH_MISSING_SOLID_COLOR = Color4b(64, 220, 255)
private val SEED_MISMATCH_UNEXPECTED_SOLID_COLOR = Color4b(255, 140, 40)
private val SEED_MISMATCH_UTILITY_COLOR = Color4b(255, 64, 220)
private val SEED_MISMATCH_MATERIAL_SWAP_COLOR = Color4b(240, 230, 90)

/** Outline distance covering the far edge of chunks at [radius] (Chebyshev). */
internal fun seedMismatchMaxDistanceBlocks(radius: Int): Double =
    (radius.coerceAtLeast(0) + 1) * 16.0

/** Tracks only presentation state; persisted findings remain the source of truth. */
internal class BaseFinderAnnouncementState {
    private val announcedTiers = mutableMapOf<String, Int>()

    fun shouldAnnounce(findingId: String, tierOrder: Int): Boolean {
        val previous = announcedTiers[findingId]
        if (previous != null && tierOrder <= previous) return false

        announcedTiers[findingId] = tierOrder
        return true
    }

    fun remember(findingId: String, tierOrder: Int) {
        announcedTiers.merge(findingId, tierOrder, ::maxOf)
    }

    fun clear() = announcedTiers.clear()
}
