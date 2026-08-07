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

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
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
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor

/**
 * Passively identifies probable player bases in chunks that the server has already sent to the client.
 */
@Suppress("TooManyFunctions")
object ModuleBaseFinder : ClientModule("BaseFinder", ModuleCategories.WORLD) {

    private val minimumConfidenceSetting = int("MinimumConfidence", 0, 0..100, "%").apply(::tagBy)
    internal val minimumConfidence by minimumConfidenceSetting
    internal val highSensitivity by boolean("HighSensitivity", true)

    internal val storage by boolean("Storage", true)
    internal val utilities by boolean("Utilities", true)
    internal val automation by boolean("Automation", true)
    internal val entities by boolean("Entities", true)
    internal val structural by boolean("Structural", true)
    internal val geometry by boolean("Geometry", true)
    internal val activity by boolean("Activity", true)
    internal val chunkTrails by boolean("ChunkTrails", true)

    internal val dirtyChunksPerTick by int("DirtyChunksPerTick", 2, 1..8)
    internal val entitySampleInterval by int("EntitySampleInterval", 20, 5..100, "ticks")
    internal val notifications by boolean("Notifications", true)
    internal val chatCoordinates by boolean("ChatCoordinates", true)

    private val publishedSnapshot = AtomicReference<BaseFinderRenderSnapshot?>(null)
    private val renderRevision = AtomicLong()
    private val renderBatch = AtomicReference(BaseFinderRenderBatch.EMPTY)
    private val ledger by lazy { BaseFinderLedger() }
    private val announcementState = BaseFinderAnnouncementState()
    private var findings: List<BaseFinding> = emptyList()
    private var lastEvidenceFingerprint = Int.MIN_VALUE

    internal object GlowBox : ToggleableValueGroup(ModuleBaseFinder, "GlowBox", true) {
        internal val maximumDistance by int("MaximumDistance", 512, 64..2048, "blocks")
        internal val renderLimit by int("RenderLimit", 32, 1..128, "markers")
        internal val boxRadius by int("BoxRadius", 4, 1..16, "blocks")
        internal val boxHeight by int("BoxHeight", 6, 1..32, "blocks")
        internal val boxMode by enumChoice("BoxMode", BaseFinderBoxMode.FIXED)
        internal val dynamicPadding by int("DynamicPadding", 1, 0..8, "blocks")
        internal val lowConfidenceColor by color("LowConfidenceColor", Color4b(255, 186, 32))
        internal val highConfidenceColor by color("HighConfidenceColor", Color4b(255, 60, 180))
        internal val showLabels by boolean("ShowLabels", true)
        internal val maxLabels by int("MaxLabels", 8, 1..32)
        internal val labelText by text("LabelText", "")
        internal val labelScale by float("LabelScale", 1f, 0.5f..2.5f)
        internal val showEvidenceDetails by boolean("ShowEvidenceDetails", true)
        internal val maxEvidenceDetails by int("MaxEvidenceDetails", 4, 1..8)

        internal object Pulse : ToggleableValueGroup(GlowBox, "Pulse", true) {
            internal val speed by float("Speed", 0.8f, 0.25f..3f, "Hz")
            internal val amount by int("Amount", 15, 0..40, "%")
        }

        private val styleConfig = EspGlowStyleConfig(this)

        internal val style: EspGlowStyle
            get() = styleConfig.style

        init {
            tree(Pulse)
        }
    }

    init {
        tree(GlowBox)
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacyBaseFinderSensitivity(jsonObject)
    }

    override fun onEnabled() {
        val epoch = BaseFinderTracker.onWorldChanged()
        ChunkScanner.subscribe(BaseFinderTracker)
        mc.level?.let { activateScope(it, epoch) }
    }

    override fun onDisabled() {
        persistCurrentScope()
        ChunkScanner.unsubscribe(BaseFinderTracker)
        BaseFinderTracker.resetVolatile()
        findings = emptyList()
        announcementState.clear()
        lastEvidenceFingerprint = Int.MIN_VALUE
        clearVolatileRenderState()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { event ->
        persistCurrentScope()
        findings = emptyList()
        announcementState.clear()
        lastEvidenceFingerprint = Int.MIN_VALUE
        clearVolatileRenderState()

        val epoch = BaseFinderTracker.onWorldChanged()
        event.world?.let { activateScope(it, epoch) }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val level = mc.level ?: return@handler
        if (publishedSnapshot.get() == null) activateScope(level, BaseFinderTracker.worldEpoch)
        BaseFinderTracker.processDirtyChunks(level, dirtyChunksPerTick)
        if (level.gameTime % entitySampleInterval == 0L) {
            BaseFinderTracker.sampleBlockEntities(level)
            BaseFinderTracker.sampleEntities(level)
        }
        processEvidenceIfChanged()
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (!activity || event.origin != TransferOrigin.INCOMING) return@handler
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

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val snapshot = publishedSnapshot.get() ?: return@handler
        if (!GlowBox.running) {
            renderBatch.set(BaseFinderRenderBatch.EMPTY)
            return@handler
        }

        val batch = BaseFinderRenderPlanner.plan(
            BaseFinderRenderRequest.fromSnapshot(
                snapshot = snapshot,
                cameraPosition = event.camera.position(),
                settings = currentRenderSettings(),
                nowMillis = System.currentTimeMillis(),
            )
        )
        renderBatch.set(batch)
        BaseFinderRenderer.renderWorld(event, batch, GlowBox.style)
    }

    @Suppress("unused")
    private val overlayHandler = handler<OverlayRenderEvent> { event ->
        if (GlowBox.running && GlowBox.showLabels) {
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

    private fun scopeFor(level: ClientLevel, worldEpoch: Long): BaseFinderRenderScope {
        val localWorldName = mc.singleplayerServer?.worldData?.levelName ?: "unknown"
        val serverKey = mc.currentServer?.ip ?: "singleplayer:$localWorldName"
        return BaseFinderRenderScope(serverKey, level.dimension().identifier().toString(), worldEpoch)
    }

    private fun activateScope(level: ClientLevel, worldEpoch: Long) {
        val scope = scopeFor(level, worldEpoch)
        findings = ledger.load(scope.serverKey, scope.dimensionKey)
        announcementState.clear()
        findings.forEach { announcementState.remember(it.id, it.tier.ordinal) }
        publishRenderMarkers(scope, findings.map { it.toRenderMarker() })
    }

    private fun processEvidenceIfChanged() {
        val snapshots = BaseFinderTracker.currentSnapshots().map(::applyDetectorSettings)
        val fingerprint = baseFinderEvidenceFingerprint(
            snapshots,
            minimumConfidence,
            highSensitivity,
            enabledFamilies(),
        )
        if (fingerprint == lastEvidenceFingerprint) return
        lastEvidenceFingerprint = fingerprint

        val relevant = snapshots.filter { BaseFinderScorer.evaluate(it).isNotEmpty() }
        val before = findings
        val now = System.currentTimeMillis()
        for (cluster in BaseFinderScorer.cluster(relevant)) {
            val candidate = BaseFinderScorer.scoreCluster(cluster, minimumConfidence, highSensitivity)
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
        storage = if (storage) snapshot.storage else StorageSignal(),
        utilities = if (utilities) snapshot.utilities else UtilitiesSignal(),
        automation = if (automation) snapshot.automation else AutomationSignal(),
        entities = if (entities) snapshot.entities else EntitiesSignal(),
        structural = if (structural) snapshot.structural else StructuralSignal(),
        geometry = if (geometry) snapshot.geometry else GeometrySignal(),
        activity = if (activity) snapshot.activity else ActivitySignal(),
        chunkTrails = if (chunkTrails) snapshot.chunkTrails else ChunkTrailsSignal(),
    )

    private fun announceChangedFinding(previous: List<BaseFinding>, now: Long) {
        val previousById = previous.associateBy(BaseFinding::id)
        val changed = findings.firstOrNull { finding ->
            finding.lastSeenAtMillis == now && previousById[finding.id] != finding
        } ?: return
        if (!announcementState.shouldAnnounce(changed.id, changed.tier.ordinal)) return

        if (notifications) {
            notification(
                name,
                message("found", changed.confidence, changed.anchor.x, changed.anchor.y, changed.anchor.z),
                NotificationEvent.Severity.INFO,
            )
        }
        if (chatCoordinates) {
            val coordinates = "${changed.anchor.x} ${changed.anchor.y} ${changed.anchor.z}"
            val evidence = changed.evidence.sortedByDescending(EvidenceSummary::score)
                .take(2)
                .joinToString(" + ") { familyLabel(it.family) }
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
        } ?: return
        ledger.save(scope.serverKey, scope.dimensionKey, findings)
    }

    private fun BaseFinding.toRenderMarker() = BaseFinderMarker(
        id = id,
        anchor = anchor,
        confidence = confidence,
        topEvidenceKeys = evidence.sortedByDescending(EvidenceSummary::score)
            .take(2)
            .map { familyLabel(it.family) },
        updatedAtMillis = lastSeenAtMillis,
        evidenceDetails = evidence.sortedByDescending(EvidenceSummary::score)
            .map { summary ->
                BaseFinderLabelEvidence(
                    family = familyLabel(summary.family),
                    score = summary.score,
                    detections = summary.keys.map(::evidenceLabel),
                )
            },
        bounds = bounds,
    )

    private fun currentRenderSettings() = BaseFinderRenderSettings(
        minimumConfidence = minimumConfidence,
        maximumDistance = GlowBox.maximumDistance.toDouble(),
        renderLimit = GlowBox.renderLimit,
        boxRadius = GlowBox.boxRadius.toDouble(),
        boxHeight = GlowBox.boxHeight.toDouble(),
        boxMode = GlowBox.boxMode,
        dynamicPadding = GlowBox.dynamicPadding,
        lowConfidenceColor = GlowBox.lowConfidenceColor,
        highConfidenceColor = GlowBox.highConfidenceColor,
        showLabels = GlowBox.showLabels,
        maxLabels = GlowBox.maxLabels,
        labelScale = GlowBox.labelScale,
        showEvidenceDetails = GlowBox.showEvidenceDetails,
        maxEvidenceDetails = GlowBox.maxEvidenceDetails,
        pulse = GlowBox.Pulse.running,
        pulseSpeedHz = GlowBox.Pulse.speed.toDouble(),
        pulseAmount = GlowBox.Pulse.amount / 100.0,
        baseLabel = GlowBox.labelText.ifBlank { message("label.base").string },
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

    private fun enabledFamilies(): Set<BaseSignalFamily> = buildSet {
        if (storage) add(BaseSignalFamily.STORAGE)
        if (utilities) add(BaseSignalFamily.UTILITIES)
        if (automation) add(BaseSignalFamily.AUTOMATION)
        if (entities) add(BaseSignalFamily.ENTITIES)
        if (structural) add(BaseSignalFamily.STRUCTURAL)
        if (geometry) add(BaseSignalFamily.GEOMETRY)
        if (activity) add(BaseSignalFamily.ACTIVITY)
        if (chunkTrails) add(BaseSignalFamily.CHUNK_TRAILS)
    }

    private fun familyLabel(family: BaseSignalFamily): String =
        message("family.${family.name.lowercase()}").string

    private fun evidenceLabel(key: String): String = key.substringAfter('.', key)
        .replace('_', ' ')
        .replaceFirstChar { character -> character.titlecase() }

    internal fun <T> immutableCopy(source: Collection<T>): List<T> = java.util.List.copyOf(source)
}

internal fun baseFinderBlockCoordinate(value: Double): Int = floor(value).toInt()

internal fun baseFinderEvidenceFingerprint(
    snapshots: List<ChunkEvidenceSnapshot>,
    minimumConfidence: Int,
    highSensitivity: Boolean,
    enabledFamilies: Set<BaseSignalFamily>,
): Int = listOf(snapshots, minimumConfidence, highSensitivity, enabledFamilies).hashCode()

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

private const val LEGACY_BASE_FINDER_MINIMUM_CONFIDENCE = 65

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
