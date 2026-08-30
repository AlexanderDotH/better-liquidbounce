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
import net.ccbluex.liquidbounce.utils.block.AbstractBlockLocationTracker
import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.BarrelBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.DecoratedPotBlock
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.TrialSpawnerBlock
import net.minecraft.world.level.block.VaultBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState
import net.minecraft.world.level.block.entity.vault.VaultState
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Always-on, session-only Trial Chamber observation service.
 *
 * The cheap anchor scanner and packet correlation stay active regardless of module state so global Combat and
 * Visual Trial targeting remain correct. The broader loot scanner is subscribed only while the tracker module runs.
 */
object TrialChamberRuntime : EventListener, MinecraftShortcuts {

    private val publishedSnapshot = AtomicReference<TrialChamberSnapshot?>(null)
    private val correlator = TrialSpawnCorrelator()
    private val membership = TrialMobMembership(VANILLA_TRIAL_MOB_TRACKING_DISTANCE_BLOCKS)
    private val resourceState = TrialResourceState()
    private val refreshPolicy = TrialRuntimeRefreshPolicy()
    private val interactions = ResourceInteractionTracker(resourceState)
    private val worldSnapshots = WorldSnapshotReader(resourceState, refreshPolicy)
    private val mobSnapshots = MobSnapshotCollector(membership)
    private val snapshotRefresh = SnapshotRefreshCoordinator(
        membership,
        resourceState,
        refreshPolicy,
        worldSnapshots,
        mobSnapshots,
    )

    private var initialized = false
    private var resourceTracking = false
    private var currentTick = 0L

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
        worldSnapshots.setResourceTrackingEnabled(enabled)
        refreshPolicy.forceSnapshotAndLootRefresh()
        if (enabled) {
            ChunkScanner.subscribe(TrialChamberLootScanner)
        } else {
            ChunkScanner.unsubscribe(TrialChamberLootScanner)
            interactions.clearMenuVisit()
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
        snapshotRefresh.onWorldChanged { currentTick = 0L }
        refreshPolicy.reset()
        interactions.reset()
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
                mc.execute { interactions.confirmMenuVisit(menuType, currentTick) }
            }

            is ServerboundUseItemOnPacket -> if (event.origin == TransferOrigin.OUTGOING) {
                val position = packet.hitResult.blockPos.immutable()
                val hand = packet.hand
                mc.execute { interactions.observeBlockInteraction(position, hand, currentTick) }
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

    private fun tickRuntime() {
        val level = mc.level ?: return clearPublishedState()
        if (mc.player == null) return clearPublishedState()
        currentTick = level.gameTime.coerceAtLeast(currentTick + 1L)
        correlator.expire(currentTick)
        interactions.observeVaultUnlock(currentTick)
        if (refreshPolicy.shouldRefreshSnapshot(currentTick)) updateSnapshot()
    }

    private fun updateSnapshot() {
        publishedSnapshot.set(snapshotRefresh.refresh(currentTick, interactions::reset))
    }

    private fun clearPublishedState() {
        snapshotRefresh.clearPublishedState()
        publishedSnapshot.set(null)
    }
}

internal enum class ScannedAnchorKind {
    TRIAL_SPAWNER,
    VAULT,
}

internal data class ScannedAnchor(
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

internal object TrialChamberAnchorScanner : AbstractBlockLocationTracker.BlockPos2State<ScannedAnchor>() {
    override fun getStateFor(pos: BlockPos, state: BlockState): ScannedAnchor? = ScannedAnchor.from(state)
}

internal enum class ScannedLootKind(val resourceKind: TrialResourceKind) {
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

internal object TrialChamberLootScanner : AbstractBlockLocationTracker.BlockPos2State<ScannedLootKind>() {
    override fun getStateFor(pos: BlockPos, state: BlockState): ScannedLootKind? = ScannedLootKind.from(state)
}

internal data class LoadedTrialAnchor(
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

internal data class LoadedTrialResource(
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

internal fun TrialSpawnerState.toSnapshotPhase(): TrialSpawnerPhase = when (this) {
    TrialSpawnerState.INACTIVE -> TrialSpawnerPhase.INACTIVE
    TrialSpawnerState.WAITING_FOR_PLAYERS -> TrialSpawnerPhase.WAITING_FOR_PLAYERS
    TrialSpawnerState.ACTIVE -> TrialSpawnerPhase.ACTIVE
    TrialSpawnerState.WAITING_FOR_REWARD_EJECTION -> TrialSpawnerPhase.WAITING_FOR_REWARD_EJECTION
    TrialSpawnerState.EJECTING_REWARD -> TrialSpawnerPhase.EJECTING_REWARD
    TrialSpawnerState.COOLDOWN -> TrialSpawnerPhase.COOLDOWN
}

internal fun VaultState.toBlockPhase(): TrialVaultBlockPhase = when (this) {
    VaultState.INACTIVE -> TrialVaultBlockPhase.INACTIVE
    VaultState.ACTIVE -> TrialVaultBlockPhase.ACTIVE
    VaultState.UNLOCKING -> TrialVaultBlockPhase.UNLOCKING
    VaultState.EJECTING -> TrialVaultBlockPhase.EJECTING
}

private fun BlockPos.toTrialBlockPosition() = TrialBlockPosition(x, y, z)
internal fun BlockPos.toSnapshotPosition() = TrialChamberPosition(x, y, z)
internal fun BlockPos.toResourcePosition() = TrialResourcePosition(x, y, z)
internal fun TrialResourcePosition.toBlockPos() = BlockPos(x, y, z)
internal fun TrialResourcePosition.toSnapshotPosition() = TrialChamberPosition(x, y, z)
internal fun TrialChamberPosition.toBlockPos() = BlockPos(x, y, z)
