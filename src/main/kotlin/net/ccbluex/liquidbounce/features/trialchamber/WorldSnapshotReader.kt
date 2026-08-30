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
package net.ccbluex.liquidbounce.features.trialchamber

import net.ccbluex.liquidbounce.interfaces.VaultSharedDataAccess
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity

/** Reads live Minecraft state and reduces it into immutable runtime snapshot values. */
internal class WorldSnapshotReader(
    private val resourceState: TrialResourceState,
    private val refreshPolicy: TrialRuntimeRefreshPolicy,
) : MinecraftShortcuts {

    private var resourceTracking = false
    private var cachedLootResources: List<LoadedTrialResource> = emptyList()

    fun setResourceTrackingEnabled(enabled: Boolean) {
        resourceTracking = enabled
        cachedLootResources = emptyList()
    }

    fun clearLootCache() {
        cachedLootResources = emptyList()
    }

    fun loadedAnchors(): List<LoadedTrialAnchor> {
        val level = mc.level ?: return emptyList()
        return TrialChamberAnchorScanner.iterate().mapNotNull { (position, scanned) ->
            val immutablePosition = position.immutable()
            if (!level.hasChunk(immutablePosition.x shr 4, immutablePosition.z shr 4)) return@mapNotNull null
            val liveState = level.getBlockState(immutablePosition)
            val live = ScannedAnchor.from(liveState) ?: return@mapNotNull null
            LoadedTrialAnchor(immutablePosition, live)
        }.toList()
    }

    fun spawnerSnapshot(anchor: LoadedTrialAnchor): TrialSpawnerSnapshot? {
        if (anchor.kind != ScannedAnchorKind.TRIAL_SPAWNER) return null
        val level = mc.level ?: return null
        val blockEntity = level.getBlockEntity(anchor.position) as? TrialSpawnerBlockEntity
        val blockEntityState = blockEntity?.state
        val observation = resolveTrialSpawnerBlockObservation(
            liveBlockPhase = anchor.spawnerState?.toSnapshotPhase(),
            blockEntityPhase = blockEntityState?.toSnapshotPhase(),
        )
        val state = anchor.spawnerState ?: blockEntityState ?: TrialSpawnerState.INACTIVE
        val expectedType = blockEntity?.let { trialSpawner ->
            trialSpawner.trialSpawner.stateData
                .getOrCreateDisplayEntity(trialSpawner.trialSpawner, level, state)
                ?.type
                ?.let(BuiltInRegistries.ENTITY_TYPE::getKey)
                ?.toString()
        }
        return TrialSpawnerSnapshot(
            position = anchor.position.toSnapshotPosition(),
            phase = observation.phase,
            ominous = anchor.ominous,
            expectedEntityType = expectedType,
        )
    }

    fun syncResources(
        selection: TrialChamberSelection,
        selectedAnchors: List<LoadedTrialAnchor>,
        loadedAnchors: List<TrialChamberAnchor>,
        currentTick: Long,
    ): TrialResourceSessionSnapshot {
        val observedPositions = linkedSetOf<TrialResourcePosition>()
        selectedAnchors.forEach { anchor ->
            val kind = when (anchor.kind) {
                ScannedAnchorKind.TRIAL_SPAWNER -> TrialResourceKind.TRIAL_SPAWNER
                ScannedAnchorKind.VAULT -> if (anchor.ominous) {
                    TrialResourceKind.OMINOUS_VAULT
                } else {
                    TrialResourceKind.VAULT
                }
            }
            val position = anchor.position.toResourcePosition()
            observedPositions += position
            resourceState.observeResource(kind, position)
            if (kind.isVault) reconcileVaultBlockState(anchor, currentTick)
        }

        if (resourceTracking) {
            if (refreshPolicy.shouldRefreshLoot(currentTick)) {
                cachedLootResources = selectedLootResources(selection, loadedAnchors)
            }
            cachedLootResources.forEach { resource ->
                observedPositions += resource.positions
                resourceState.observeResource(resource.kind, resource.position, resource.connectedChestHalf)
            }
        } else {
            cachedLootResources = emptyList()
        }
        resourceState.retainObservedPositions(observedPositions)
        return resourceState.snapshot()
    }

    private fun selectedLootResources(
        selection: TrialChamberSelection,
        loadedAnchors: List<TrialChamberAnchor>,
    ): List<LoadedTrialResource> {
        val level = mc.level ?: return emptyList()
        return TrialChamberLootScanner.iterate().mapNotNull { (position, scannedKind) ->
            val immutablePosition = position.immutable()
            if (!level.hasChunk(immutablePosition.x shr 4, immutablePosition.z shr 4)) return@mapNotNull null
            val liveState = level.getBlockState(immutablePosition)
            val liveKind = ScannedLootKind.from(liveState) ?: return@mapNotNull null
            if (liveKind != scannedKind) return@mapNotNull null
            if (!selection.cluster.containsContainer(
                    position = TrialWorldPosition(
                        immutablePosition.x + 0.5,
                        immutablePosition.y + 0.5,
                        immutablePosition.z + 0.5,
                    ),
                    loadedAnchors = loadedAnchors,
                )
            ) {
                return@mapNotNull null
            }
            LoadedTrialResource.from(immutablePosition, liveKind, liveState)
        }.toList()
    }

    private fun reconcileVaultBlockState(anchor: LoadedTrialAnchor, currentTick: Long) {
        val level = mc.level ?: return
        val localPlayer = mc.player ?: return
        val vaultState = anchor.vaultState ?: return
        val blockEntity = level.getBlockEntity(anchor.position) as? VaultBlockEntity ?: return
        val connectedPlayers = (blockEntity.sharedData as VaultSharedDataAccess).connectedPlayers()
        val detectionRange = blockEntity.config.deactivationRange()
        val withinRange = !localPlayer.isSpectator &&
            localPlayer.blockPosition().closerThan(anchor.position, detectionRange)
        resourceState.reconcileVaultBlockObservation(
            position = anchor.position.toResourcePosition(),
            observation = TrialVaultBlockObservation(
                phase = vaultState.toBlockPhase(),
                localPlayerConnected = localPlayer.uuid in connectedPlayers,
                localPlayerWithinRange = withinRange,
            ),
            tick = currentTick,
        )
    }

    fun vaultSnapshot(
        anchor: LoadedTrialAnchor,
        resources: TrialResourceSessionSnapshot,
    ): TrialVaultSnapshot? {
        if (anchor.kind != ScannedAnchorKind.VAULT) return null
        val resource = resources.resourceAt(anchor.position.toResourcePosition()) ?: return null
        return TrialVaultSnapshot(
            position = anchor.position.toSnapshotPosition(),
            ominous = anchor.ominous,
            status = when (resource.vaultState ?: TrialVaultDisplayState.UNKNOWN) {
                TrialVaultDisplayState.AVAILABLE -> TrialVaultStatus.AVAILABLE
                TrialVaultDisplayState.CLAIMED -> TrialVaultStatus.CLAIMED
                TrialVaultDisplayState.UNKNOWN -> TrialVaultStatus.UNKNOWN
            },
        )
    }

    fun lootSnapshot(resource: TrialResourceSnapshot): TrialLootSnapshot? {
        val type = when (resource.kind) {
            TrialResourceKind.CHEST -> TrialLootType.CHEST
            TrialResourceKind.BARREL -> TrialLootType.BARREL
            TrialResourceKind.DECORATED_POT -> TrialLootType.POT
            TrialResourceKind.DISPENSER -> TrialLootType.DISPENSER
            else -> return null
        }
        return TrialLootSnapshot(resource.id.canonicalPosition.toSnapshotPosition(), type, resource.visited)
    }
}
