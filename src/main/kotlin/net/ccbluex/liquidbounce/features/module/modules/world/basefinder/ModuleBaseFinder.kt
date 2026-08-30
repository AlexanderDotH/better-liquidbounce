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
import net.ccbluex.liquidbounce.event.events.BlockChangeEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Passively identifies probable player bases in chunks that the server has already sent to the client.
 *
 * Optional [WorldSeed] comparison regenerates vanilla overworld/nether/end columns for the configured seed and treats
 * mismatches as seed-backed evidence. Custom datapack generators are out of scope for this path.
 */
object ModuleBaseFinder : ClientModule("BaseFinder", ModuleCategories.WORLD) {

    private val minimumConfidenceSetting = int("MinimumConfidence", 0, 0..100, "%").apply(::tagBy)
    internal val minimumConfidence by minimumConfidenceSetting
    internal val highSensitivity by boolean("HighSensitivity", true)

    /** Detector family toggles that feed scoring. */
    internal val Evidence: BaseFinderEvidenceSettings
        get() = BaseFinderEvidenceSettings
    internal val SeedMismatch: BaseFinderSeedMismatchSettings
        get() = BaseFinderSeedMismatchSettings
    internal val Scoring: BaseFinderScoringSettings
        get() = BaseFinderScoringSettings
    internal val Alerts: BaseFinderAlertSettings
        get() = BaseFinderAlertSettings
    internal val Render: BaseFinderRenderSettings
        get() = BaseFinderRenderSettings

    /**
     * Seed-backed column regen, scoring evidence, and mismatch outlines.
     * Alias [SeedCompare] keeps older module configs loadable.
     *
     * Worker/cache/rescan knobs are fixed internals — only seed, scan range, and outlines are exposed.
     */

    /** Complete scoring matrix. Sections stay collapsible in both ClickGUI variants. */


    internal val publishedSnapshot = AtomicReference<BaseFinderRenderSnapshot?>(null)
    internal val renderRevision = AtomicLong()
    internal val renderBatch = AtomicReference(BaseFinderRenderBatch.EMPTY)
    internal val mismatchCellsSnapshot = AtomicReference<List<SeedMismatchCell>>(emptyList())
    internal val ledger by lazy { BaseFinderLedger() }
    internal val announcementState = BaseFinderAnnouncementState()
    internal var findings: List<BaseFinding> = emptyList()
    internal var lastEvidenceFingerprint = Int.MIN_VALUE
    internal var overlayTickCounter = 0
    internal var overlayRefreshCursor = 0
    internal var sparseAuditCursor = 0
    internal val seedRuntime = BaseFinderSeedRuntime()
    internal val seedDebugListener: (String) -> Unit = { message ->
        if (ModuleDebug.running) {
            logger.info("[SeedMismatch] $message")
        }
    }
    internal val serverSettingsBindingDelegate = lazy {
        BaseFinderServerSettingsBinding(
            store = BaseFinderServerSettingsStore(),
            snapshot = ::currentServerSettings,
            apply = ::applyServerSettings,
            onFailure = { throwable -> logger.error("Failed to persist BaseFinder server settings", throwable) },
        )
    }
    internal val serverSettingsBinding: BaseFinderServerSettingsBinding
        get() = serverSettingsBindingDelegate.value

    /**
     * Finding markers: plain boxes or shared Gaussian glow, plus confidence colors and labels.
     * Alias [GlowBox] keeps older module configs loadable.
     */

    init {
        Evidence.tree(SeedMismatch)
        treeAll(Evidence, Scoring, Alerts, Render)
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateBaseFinderSettings(jsonObject)
    }

    override fun onEnabled() {
        enableRuntime()
    }

    override fun onDisabled() {
        disableRuntime()
    }

    internal fun findingsForCurrentScope(): List<BaseFinding> = loadFindingsForCurrentScope()

    internal fun exportCurrentFindings(format: BaseFinderExportFormat): Path = exportCurrentScopeFindings(format)

    internal fun exportFindings(format: BaseFinderExportFormat): Path = exportCurrentScopeFindings(format)

    internal fun clearCurrentFindings(): Int = clearFindingsForCurrentScope()

    internal fun clearSeedComparisonCache() = clearSeedRuntimeCache()

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { event ->
        handleWorldChange(event)
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        handleGameTick()
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        handlePacket(event)
    }

    /**
     * ChunkScanner receives server packets, but local predicted place/break changes may not be echoed back to the
     * actor. Capture accepted client changes too so their seed comparison is immediately made stale and refreshed.
     */
    @Suppress("unused")
    private val blockChangeHandler = handler<BlockChangeEvent> { event ->
        handleBlockChange(event)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        renderWorld(event)
    }

    @Suppress("unused")
    private val overlayHandler = handler<OverlayRenderEvent> { event ->
        renderOverlay(event)
    }

    internal fun <T> immutableCopy(source: Collection<T>): List<T> = java.util.List.copyOf(source)
}
