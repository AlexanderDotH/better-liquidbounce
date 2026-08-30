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

import net.ccbluex.liquidbounce.event.events.BlockChangeEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundSoundPacket

internal fun ModuleBaseFinder.enableRuntime() {
    val epoch = BaseFinderTracker.onWorldChanged()
    seedRuntime.onWorldChanged(epoch)
    ChunkScanner.subscribe(BaseFinderTracker)
    val level = mc.level
    if (level != null) activateScope(level, epoch) else syncSeedRuntimeSettings()
}

internal fun ModuleBaseFinder.disableRuntime() {
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

internal fun ModuleBaseFinder.handleWorldChange(event: WorldChangeEvent) {
    persistCurrentScope()
    serverSettingsBinding.unbind(persist = false)
    findings = emptyList()
    announcementState.clear()
    lastEvidenceFingerprint = Int.MIN_VALUE
    clearVolatileRenderState()

    val epoch = BaseFinderTracker.onWorldChanged()
    seedRuntime.onWorldChanged(epoch)
    val level = event.world
    if (level != null) activateScope(level, epoch) else syncSeedRuntimeSettings()
}

internal fun ModuleBaseFinder.handleGameTick() {
    val level = mc.level ?: return
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

internal fun ModuleBaseFinder.handlePacket(event: PacketEvent) {
    if (!Evidence.activity || event.origin != TransferOrigin.INCOMING) return
    val packet = event.packet as? ClientboundSoundPacket ?: return
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
        ),
    )
}

internal fun ModuleBaseFinder.handleBlockChange(event: BlockChangeEvent) {
    if (!running) return
    BaseFinderTracker.recordBlock(event.blockPos, event.newState, cleared = false)
}

internal fun ModuleBaseFinder.renderWorld(event: WorldRenderEvent) {
    val cameraPosition = event.camera.position()
    val snapshot = publishedSnapshot.get()
    if (this.Render.running && snapshot != null) {
        val batch = BaseFinderRenderPlanner.plan(
            BaseFinderRenderRequest.fromSnapshot(
                snapshot = snapshot,
                cameraPosition = cameraPosition,
                settings = currentRenderSettings(),
                nowMillis = System.currentTimeMillis(),
            ),
        )
        renderBatch.set(batch)
        val glowStyle = (this.Render.mode.activeMode as? BaseFinderRenderSettings.Glow)?.style
        BaseFinderRenderer.renderWorld(event, batch, glowStyle)
    } else {
        renderBatch.set(BaseFinderRenderBatch.EMPTY)
    }
    renderMismatchWorld(event, cameraPosition)
}

private fun ModuleBaseFinder.renderMismatchWorld(
    event: WorldRenderEvent,
    cameraPosition: net.minecraft.world.phys.Vec3,
) {
    if (!shouldRenderSeedMismatches()) return
    val cells = mismatchCellsSnapshot.get()
    if (cells.isEmpty()) return
    val mismatchBatch = BaseFinderSeedMismatchRenderPlanner.plan(
        cells = cells,
        cameraPosition = cameraPosition,
        settings = currentMismatchRenderSettings(),
    )
    BaseFinderRenderer.renderMismatchWorld(event, mismatchBatch)
}

internal fun ModuleBaseFinder.renderOverlay(event: OverlayRenderEvent) {
    if (this.Render.running && BaseFinderRenderSettings.Labels.showLabels) {
        BaseFinderRenderer.renderLabels(event, renderBatch.get())
    }
}
