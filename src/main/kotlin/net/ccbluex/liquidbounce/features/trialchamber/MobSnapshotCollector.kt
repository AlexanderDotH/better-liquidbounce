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

import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/** Reconstructs and snapshots the session-local trial mob membership. */
internal class MobSnapshotCollector(
    private val membership: TrialMobMembership,
) : MinecraftShortcuts {

    fun reconstructRunningWave(spawners: List<TrialSpawnerSnapshot>, currentTick: Long) {
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

    fun prune(currentOrigins: Set<TrialSpawnerOrigin>, currentTick: Long) {
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

    fun snapshots(): List<TrialMobSnapshot> {
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

    private companion object {
        const val ENTITY_APPEARANCE_GRACE_TICKS = 4L
    }
}
