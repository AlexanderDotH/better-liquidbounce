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
package net.ccbluex.liquidbounce.features.trialchamber

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldEntityRemoveEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.injection.mixins.minecraft.blockentity.MixinVaultSharedDataAccessor
import net.ccbluex.liquidbounce.utils.block.AbstractBlockLocationTracker
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.level.block.BarrelBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.DecoratedPotBlock
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.TrialSpawnerBlock
import net.minecraft.world.level.block.VaultBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity
import net.minecraft.world.level.block.entity.vault.VaultState
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Always-on, session-only Trial Chamber observation service.
 *
 * The cheap anchor scanner and packet correlation stay active regardless of module state so global Combat and
 * Visual Trial targeting remain correct. The broader loot scanner is subscribed only while the tracker module runs.
 */
// Packet, scanner, entity, and world callbacks intentionally converge at this one session-state boundary.
@Suppress("TooManyFunctions")
object TrialChamberRuntime : EventListener, MinecraftShortcuts {

    private val publishedSnapshot = AtomicReference<TrialChamberSnapshot?>(null)
    private val correlator = TrialSpawnCorrelator()
    private val membership = TrialMobMembership(VANILLA_TRIAL_MOB_TRACKING_DISTANCE_BLOCKS)
    private val resourceState = TrialResourceState()
    private val sessionContinuity = TrialChamberSessionContinuity()
    private val refreshPolicy = TrialRuntimeRefreshPolicy()

    private var initialized = false
    private var resourceTracking = false
    private var worldEpoch = 0L
    private var revision = 0L
    private var currentTick = 0L
    private var currentSelection: TrialChamberSelection? = null
    private var cachedLootResources: List<LoadedTrialResource> = emptyList()
    private var pendingMenuVisit: TrialMenuVisitAttempt? = null
    private var pendingVaultUnlock: PendingVaultUnlock? = null

    @Synchronized
    fun initialize() {
        if (initialized) return
        initialized = true
        ChunkScanner.subscribe(TrialChamberAnchorScanner)
    }

    @Synchronized
    fun setResourceTrackingEnabled(enabled: Boolean) {
        if (resourceTracking == enabled) return
        resourceTracking = enabled
        cachedLootResources = emptyList()
        refreshPolicy.forceSnapshotAndLootRefresh()
        if (enabled) {
            ChunkScanner.subscribe(TrialChamberLootScanner)
        } else {
            ChunkScanner.unsubscribe(TrialChamberLootScanner)
            pendingMenuVisit = null
        }
    }

    fun snapshot(): TrialChamberSnapshot? = publishedSnapshot.get()

    @JvmStatic
    fun isCurrentTrialMob(uuid: UUID): Boolean = membership.isCurrentTrialMob(uuid)

    @JvmStatic
    fun isCurrentTrialMob(entity: Entity): Boolean = isCurrentTrialMob(entity.uuid)

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        tickRuntime()
    }

    @Suppress("unused")
    private val entityRemoveHandler = handler<WorldEntityRemoveEvent> { event ->
        membership.onEntityRemoved(event.entity.uuid)
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent>(FIRST_PRIORITY) {
        worldEpoch++
        revision = 0L
        currentTick = 0L
        currentSelection = null
        cachedLootResources = emptyList()
        sessionContinuity.clear()
        refreshPolicy.reset()
        pendingMenuVisit = null
        pendingVaultUnlock = null
        correlator.clear()
        membership.onSessionChanged()
        resourceState.resetSession()
        publishedSnapshot.set(null)
    }

    /** PacketEvent can originate on Netty, so only immutable packet primitives cross to the game thread. */
    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(READ_FINAL_STATE) { event ->
        if (event.isCancelled || !event.original) return@handler

        when (val packet = event.packet) {
            is ClientboundLevelEventPacket -> if (event.origin == TransferOrigin.INCOMING) {
                val type = packet.type
                if (type == LevelEvent.PARTICLES_TRIAL_SPAWNER_SPAWN ||
                    type == LevelEvent.PARTICLES_TRIAL_SPAWNER_SPAWN_MOB_AT
                ) {
                    val packedPosition = packet.pos.asLong()
                    val data = packet.data
                    mc.execute { observeTrialLevelEvent(type, packedPosition, data) }
                }
            }

            is ClientboundAddEntityPacket -> if (event.origin == TransferOrigin.INCOMING) {
                val uuid = packet.uuid
                val type = BuiltInRegistries.ENTITY_TYPE.getKey(packet.type).toString()
                val spawnCell = BlockPos.containing(packet.x, packet.y, packet.z).asLong()
                mc.execute { observeAddedEntity(uuid, type, spawnCell) }
            }

            is ClientboundOpenScreenPacket -> if (event.origin == TransferOrigin.INCOMING) {
                val menuType = packet.type
                mc.execute { confirmPendingMenuVisit(menuType) }
            }

            is ServerboundUseItemOnPacket -> if (event.origin == TransferOrigin.OUTGOING) {
                val position = packet.hitResult.blockPos.immutable()
                val hand = packet.hand
                mc.execute { observeLocalBlockInteraction(position, hand) }
            }
        }
    }

    private fun observeTrialLevelEvent(type: Int, packedPosition: Long, data: Int) {
        val associations = when (type) {
            LevelEvent.PARTICLES_TRIAL_SPAWNER_SPAWN -> correlator.observe(
                TrialSpawnerPulse(TrialSpawnerOrigin(packedPosition), data, currentTick)
            )

            LevelEvent.PARTICLES_TRIAL_SPAWNER_SPAWN_MOB_AT -> correlator.observe(
                TrialSpawnerMobSpawnEvent(TrialSpawnCell(packedPosition), data, currentTick)
            )

            else -> emptyList()
        }
        associations.forEach(membership::add)
    }

    private fun observeAddedEntity(uuid: UUID, type: String, packedPosition: Long) {
        correlator.observe(
            TrialAddedEntity(uuid, TrialEntityTypeKey(type), TrialSpawnCell(packedPosition), currentTick)
        ).forEach(membership::add)
    }

    private fun observeLocalBlockInteraction(position: BlockPos, hand: net.minecraft.world.InteractionHand) {
        val level = mc.level ?: return
        val player = mc.player ?: return
        val resourcePosition = position.toResourcePosition()

        resourceState.beginMenuVisit(resourcePosition, currentTick)?.let { attempt ->
            pendingMenuVisit = attempt
        }

        val state = level.getBlockState(position)
        if (state.block !is VaultBlock || state.getValue(VaultBlock.STATE) != VaultState.ACTIVE) return
        val expectedKey = if (state.getValue(VaultBlock.OMINOUS)) Items.OMINOUS_TRIAL_KEY else Items.TRIAL_KEY
        if (!player.getItemInHand(hand).`is`(expectedKey)) return
        if (resourceState.beginLocalVaultUnlock(resourcePosition)) {
            pendingVaultUnlock = PendingVaultUnlock(resourcePosition, currentTick)
        }
    }

    private fun confirmPendingMenuVisit(menuType: MenuType<*>) {
        val attempt = pendingMenuVisit ?: return
        val compatible = when (attempt.resourceId.kind) {
            TrialResourceKind.CHEST -> menuType === MenuType.GENERIC_9x3 || menuType === MenuType.GENERIC_9x6
            TrialResourceKind.BARREL -> menuType === MenuType.GENERIC_9x3
            TrialResourceKind.DISPENSER -> menuType === MenuType.GENERIC_3x3
            else -> false
        }
        if (compatible) resourceState.confirmMenuVisit(attempt, currentTick)
        pendingMenuVisit = null
    }

    private fun tickRuntime() {
        val level = mc.level ?: return clearPublishedState()
        if (mc.player == null) return clearPublishedState()
        currentTick = level.gameTime.coerceAtLeast(currentTick + 1L)
        correlator.expire(currentTick)
        observePendingVaultUnlock()
        if (refreshPolicy.shouldRefreshSnapshot(currentTick)) updateSnapshot()
    }

    private fun updateSnapshot() {
        val player = mc.player ?: return clearPublishedState()

        val scannedAnchors = loadedAnchors()
        val observer = TrialWorldPosition(player.x, player.y, player.z)
        val selection = TrialChamberSelector.select(
            worldEpoch = worldEpoch,
            observer = observer,
            loadedAnchors = scannedAnchors.map(LoadedTrialAnchor::selectionAnchor),
            previous = currentSelection,
        )
        currentSelection = selection
        if (selection == null) {
            cachedLootResources = emptyList()
            refreshPolicy.forceLootRefresh()
            membership.retainCurrentOrigins(emptySet())
            resourceState.suspendObservations()
            pendingMenuVisit = null
            pendingVaultUnlock = null
            publishedSnapshot.set(null)
            return
        }

        val selectedPositions = selection.cluster.anchors.map { it.position }
        if (sessionContinuity.observe(selection.cluster) == TrialChamberContinuity.CHANGED) {
            cachedLootResources = emptyList()
            refreshPolicy.forceLootRefresh()
            resourceState.resetSession()
            pendingMenuVisit = null
            pendingVaultUnlock = null
        }

        val selectedPositionSet = selectedPositions.toHashSet()
        val selectedAnchors = scannedAnchors
            .filter { it.selectionAnchor.position in selectedPositionSet }
            .sortedBy { it.position.asLong() }
        val currentOrigins = selectedAnchors.asSequence()
            .filter { it.kind == ScannedAnchorKind.TRIAL_SPAWNER }
            .mapTo(linkedSetOf()) { TrialSpawnerOrigin(it.position.asLong()) }
        membership.retainCurrentOrigins(currentOrigins)

        val spawners = selectedAnchors.mapNotNull(::spawnerSnapshot)
        if (refreshPolicy.shouldReconstructWave(currentTick)) reconstructRunningWave(spawners)
        pruneMembership(currentOrigins)

        syncResources(selection, selectedAnchors, scannedAnchors.map(LoadedTrialAnchor::selectionAnchor))
        val refreshedResources = resourceState.snapshot()
        val vaults = selectedAnchors.mapNotNull { anchor -> vaultSnapshot(anchor, refreshedResources) }
        val loot = refreshedResources.resources.mapNotNull(::lootSnapshot)
        val mobs = mobSnapshots()
        val anchorSnapshots = selectedAnchors.map(LoadedTrialAnchor::snapshot)

        publishedSnapshot.set(TrialChamberSnapshot.create(
            worldEpoch = worldEpoch,
            revision = ++revision,
            playerInsideChamber = selection.cluster.nearestAnchorDistanceTo(observer) <=
                TrialChamberSelectionPolicy.CONTAINER_DISTANCE,
            anchors = anchorSnapshots,
            spawners = spawners,
            mobs = mobs,
            vaults = vaults,
            loot = loot,
        ))
    }

    private fun clearPublishedState() {
        currentSelection = null
        cachedLootResources = emptyList()
        refreshPolicy.forceSnapshotAndLootRefresh()
        publishedSnapshot.set(null)
    }

    private fun loadedAnchors(): List<LoadedTrialAnchor> {
        val level = mc.level ?: return emptyList()
        return TrialChamberAnchorScanner.iterate().mapNotNull { (position, scanned) ->
            val immutablePosition = position.immutable()
            if (!level.hasChunk(immutablePosition.x shr 4, immutablePosition.z shr 4)) return@mapNotNull null
            val liveState = level.getBlockState(immutablePosition)
            val live = ScannedAnchor.from(liveState) ?: return@mapNotNull null
            LoadedTrialAnchor(immutablePosition, live)
        }.toList()
    }

    private fun spawnerSnapshot(anchor: LoadedTrialAnchor): TrialSpawnerSnapshot? {
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

    private fun reconstructRunningWave(spawners: List<TrialSpawnerSnapshot>) {
        val level = mc.level ?: return
        val candidates = spawners.filter {
            it.phase == TrialSpawnerPhase.ACTIVE && it.expectedEntityType != null
        }
        if (candidates.isEmpty()) return

        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity || membership.isCurrentTrialMob(entity.uuid)) continue
            val entityType = TrialEntityTypeKey(BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString())
            val origin = candidates.asSequence()
                .filter { it.expectedEntityType == entityType.value }
                .map { spawner ->
                    spawner to entity.position().distanceTo(Vec3.atCenterOf(spawner.position.toBlockPos()))
                }
                .filter { (_, distance) -> isConservativeTrialMobFallback(TrialFallbackCandidate(
                    activeSpawner = true,
                    displayMobType = entityType,
                    candidateMobType = entityType,
                    distanceFromSpawnerBlocks = distance,
                )) }
                .minWithOrNull(compareBy<Pair<TrialSpawnerSnapshot, Double>> { it.second }
                    .thenBy { it.first.position })
                ?.first
                ?: continue
            membership.add(TrialMobAssociation(
                entityUuid = entity.uuid,
                entityType = entityType,
                originSpawner = TrialSpawnerOrigin(origin.position.toBlockPos().asLong()),
                spawnCell = TrialSpawnCell(entity.blockPosition().asLong()),
                correlatedAtTick = currentTick,
            ))
        }
    }

    private fun pruneMembership(currentOrigins: Set<TrialSpawnerOrigin>) {
        val level = mc.level ?: return
        membership.retainCurrentOrigins(currentOrigins)
        membership.snapshot().forEach { association ->
            val entity = level.getEntity(association.entityUuid)
            if (entity == null) {
                if (currentTick - association.correlatedAtTick > ENTITY_APPEARANCE_GRACE_TICKS) {
                    membership.onEntityRemoved(association.entityUuid)
                }
                return@forEach
            }
            val origin = BlockPos.of(association.originSpawner.packedBlockPosition)
            membership.onDistanceObserved(
                association.entityUuid,
                entity.position().distanceTo(Vec3.atCenterOf(origin)),
            )
        }
    }

    private fun syncResources(
        selection: TrialChamberSelection,
        selectedAnchors: List<LoadedTrialAnchor>,
        loadedAnchors: List<TrialChamberAnchor>,
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
            if (kind.isVault) reconcileVaultBlockState(anchor)
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

    private fun reconcileVaultBlockState(anchor: LoadedTrialAnchor) {
        val level = mc.level ?: return
        val localPlayer = mc.player ?: return
        val vaultState = anchor.vaultState ?: return
        val blockEntity = level.getBlockEntity(anchor.position) as? VaultBlockEntity ?: return
        val connectedPlayers = (blockEntity.sharedData as MixinVaultSharedDataAccessor)
            .`liquid_bounce$getConnectedPlayers`()
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

    private fun observePendingVaultUnlock() {
        val pending = pendingVaultUnlock ?: return
        if (currentTick - pending.startedAtTick > VAULT_UNLOCK_OBSERVATION_TICKS) {
            pendingVaultUnlock = null
            return
        }
        val state = mc.level?.getBlockState(pending.position.toBlockPos()) ?: return
        if (state.block !is VaultBlock) {
            pendingVaultUnlock = null
            return
        }
        if (state.getValue(VaultBlock.STATE) == VaultState.UNLOCKING ||
            state.getValue(VaultBlock.STATE) == VaultState.EJECTING
        ) {
            resourceState.completeLocalVaultUnlock(pending.position)
            pendingVaultUnlock = null
        }
    }

    private fun vaultSnapshot(
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

    private fun lootSnapshot(resource: TrialResourceSnapshot): TrialLootSnapshot? {
        val type = when (resource.kind) {
            TrialResourceKind.CHEST -> TrialLootType.CHEST
            TrialResourceKind.BARREL -> TrialLootType.BARREL
            TrialResourceKind.DECORATED_POT -> TrialLootType.POT
            TrialResourceKind.DISPENSER -> TrialLootType.DISPENSER
            else -> return null
        }
        return TrialLootSnapshot(resource.id.canonicalPosition.toSnapshotPosition(), type, resource.visited)
    }

    private fun mobSnapshots(): List<TrialMobSnapshot> {
        val level = mc.level ?: return emptyList()
        return membership.snapshot().mapNotNull { association ->
            val entity = level.getEntity(association.entityUuid) ?: return@mapNotNull null
            TrialMobSnapshot(
                uuid = entity.uuid,
                entityType = association.entityType.value,
                position = entity.blockPosition().toSnapshotPosition(),
                originSpawner = BlockPos.of(association.originSpawner.packedBlockPosition).toSnapshotPosition(),
                alive = (entity as? LivingEntity)?.isAlive == true,
            )
        }.sortedBy { it.uuid.toString() }
    }

    private data class PendingVaultUnlock(
        val position: TrialResourcePosition,
        val startedAtTick: Long,
    )

    private const val ENTITY_APPEARANCE_GRACE_TICKS = 4L
    private const val VAULT_UNLOCK_OBSERVATION_TICKS = 40L
}

private enum class ScannedAnchorKind {
    TRIAL_SPAWNER,
    VAULT,
}

private data class ScannedAnchor(
    val kind: ScannedAnchorKind,
    val spawnerState: TrialSpawnerState? = null,
    val vaultState: VaultState? = null,
    val ominous: Boolean = false,
) {
    companion object {
        fun from(state: BlockState): ScannedAnchor? = when (state.block) {
            is TrialSpawnerBlock -> ScannedAnchor(
                kind = ScannedAnchorKind.TRIAL_SPAWNER,
                spawnerState = state.getValue(TrialSpawnerBlock.STATE),
                ominous = state.getValue(TrialSpawnerBlock.OMINOUS),
            )

            is VaultBlock -> ScannedAnchor(
                kind = ScannedAnchorKind.VAULT,
                vaultState = state.getValue(VaultBlock.STATE),
                ominous = state.getValue(VaultBlock.OMINOUS),
            )

            else -> null
        }
    }
}

private object TrialChamberAnchorScanner : AbstractBlockLocationTracker.BlockPos2State<ScannedAnchor>() {
    override fun getStateFor(pos: BlockPos, state: BlockState): ScannedAnchor? = ScannedAnchor.from(state)
}

private enum class ScannedLootKind(val resourceKind: TrialResourceKind) {
    CHEST(TrialResourceKind.CHEST),
    BARREL(TrialResourceKind.BARREL),
    DECORATED_POT(TrialResourceKind.DECORATED_POT),
    DISPENSER(TrialResourceKind.DISPENSER),
    ;

    companion object {
        fun from(state: BlockState): ScannedLootKind? = when {
            state.block is ChestBlock -> CHEST
            state.block is BarrelBlock -> BARREL
            state.block is DecoratedPotBlock -> DECORATED_POT
            state.block === Blocks.DISPENSER -> DISPENSER
            else -> null
        }
    }
}

private object TrialChamberLootScanner : AbstractBlockLocationTracker.BlockPos2State<ScannedLootKind>() {
    override fun getStateFor(pos: BlockPos, state: BlockState): ScannedLootKind? = ScannedLootKind.from(state)
}

private data class LoadedTrialAnchor(
    val position: BlockPos,
    val scanned: ScannedAnchor,
) {
    val kind: ScannedAnchorKind
        get() = scanned.kind
    val ominous: Boolean
        get() = scanned.ominous
    val spawnerState: TrialSpawnerState?
        get() = scanned.spawnerState
    val vaultState: VaultState?
        get() = scanned.vaultState

    val selectionAnchor = TrialChamberAnchor(
        position = position.toTrialBlockPosition(),
        kind = when (kind) {
            ScannedAnchorKind.TRIAL_SPAWNER -> TrialChamberAnchorKind.TRIAL_SPAWNER
            ScannedAnchorKind.VAULT -> TrialChamberAnchorKind.VAULT
        },
        activeSpawner = spawnerState == TrialSpawnerState.ACTIVE,
    )

    fun snapshot() = TrialChamberAnchorSnapshot(
        position.toSnapshotPosition(),
        when (kind) {
            ScannedAnchorKind.TRIAL_SPAWNER -> TrialChamberAnchorType.TRIAL_SPAWNER
            ScannedAnchorKind.VAULT -> if (ominous) {
                TrialChamberAnchorType.OMINOUS_VAULT
            } else {
                TrialChamberAnchorType.VAULT
            }
        },
    )
}

private data class LoadedTrialResource(
    val position: TrialResourcePosition,
    val kind: TrialResourceKind,
    val connectedChestHalf: TrialResourcePosition? = null,
) {
    val positions: Set<TrialResourcePosition>
        get() = setOfNotNull(position, connectedChestHalf)

    companion object {
        fun from(position: BlockPos, kind: ScannedLootKind, state: BlockState): LoadedTrialResource {
            val connectedHalf = if (kind == ScannedLootKind.CHEST &&
                state.getValue(ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE
            ) {
                ChestBlock.getConnectedBlockPos(position, state).toResourcePosition()
            } else {
                null
            }
            return LoadedTrialResource(position.toResourcePosition(), kind.resourceKind, connectedHalf)
        }
    }
}

private fun TrialSpawnerState.toSnapshotPhase(): TrialSpawnerPhase = when (this) {
    TrialSpawnerState.INACTIVE -> TrialSpawnerPhase.INACTIVE
    TrialSpawnerState.WAITING_FOR_PLAYERS -> TrialSpawnerPhase.WAITING_FOR_PLAYERS
    TrialSpawnerState.ACTIVE -> TrialSpawnerPhase.ACTIVE
    TrialSpawnerState.WAITING_FOR_REWARD_EJECTION -> TrialSpawnerPhase.WAITING_FOR_REWARD_EJECTION
    TrialSpawnerState.EJECTING_REWARD -> TrialSpawnerPhase.EJECTING_REWARD
    TrialSpawnerState.COOLDOWN -> TrialSpawnerPhase.COOLDOWN
}

private fun VaultState.toBlockPhase(): TrialVaultBlockPhase = when (this) {
    VaultState.INACTIVE -> TrialVaultBlockPhase.INACTIVE
    VaultState.ACTIVE -> TrialVaultBlockPhase.ACTIVE
    VaultState.UNLOCKING -> TrialVaultBlockPhase.UNLOCKING
    VaultState.EJECTING -> TrialVaultBlockPhase.EJECTING
}

private fun BlockPos.toTrialBlockPosition() = TrialBlockPosition(x, y, z)
private fun BlockPos.toSnapshotPosition() = TrialChamberPosition(x, y, z)
private fun BlockPos.toResourcePosition() = TrialResourcePosition(x, y, z)
private fun TrialResourcePosition.toBlockPos() = BlockPos(x, y, z)
private fun TrialResourcePosition.toSnapshotPosition() = TrialChamberPosition(x, y, z)
private fun TrialChamberPosition.toBlockPos() = BlockPos(x, y, z)
