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
package net.ccbluex.liquidbounce.features.module.modules.world.trialchamber

import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberPosition
import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberSnapshot
import net.ccbluex.liquidbounce.features.trialchamber.TrialLootSnapshot
import net.ccbluex.liquidbounce.features.trialchamber.TrialLootType
import net.ccbluex.liquidbounce.features.trialchamber.TrialSpawnerPhase
import net.ccbluex.liquidbounce.features.trialchamber.TrialSpawnerSnapshot
import net.ccbluex.liquidbounce.features.trialchamber.TrialVaultSnapshot
import net.ccbluex.liquidbounce.features.trialchamber.TrialVaultStatus
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotRenderTargetMapperTest {

    @Test
    fun `maps snapshot groups in spawner vault loot order`() {
        val targets = SnapshotRenderTargetMapper.map(snapshot())

        assertEquals(
            listOf(
                "spawner:1,11,-1",
                "spawner:2,12,-2",
                "vault:3,13,-3",
                "vault:4,14,-4",
                "chest:5,15,-5",
                "barrel:6,16,-6",
                "pot:7,17,-7",
                "dispenser:8,18,-8",
            ),
            targets.map(TrialChamberRenderTarget::id),
        )
        assertEquals(
            listOf(
                TrialChamberRenderTargetKind.SPAWNER,
                TrialChamberRenderTargetKind.SPAWNER,
                TrialChamberRenderTargetKind.NORMAL_VAULT,
                TrialChamberRenderTargetKind.OMINOUS_VAULT,
                TrialChamberRenderTargetKind.CHEST,
                TrialChamberRenderTargetKind.BARREL,
                TrialChamberRenderTargetKind.POT,
                TrialChamberRenderTargetKind.DISPENSER,
            ),
            targets.map(TrialChamberRenderTarget::kind),
        )
        assertEquals(
            listOf(
                "Trial Spawner: Active",
                "Trial Spawner: Cooldown",
                "Vault: Claimed",
                "Ominous Vault: Available",
                "Chest",
                "Barrel",
                "Pot",
                "Dispenser",
            ),
            targets.map(TrialChamberRenderTarget::label),
        )
    }

    @Test
    fun `preserves colors visit completion and block geometry`() {
        val targets = SnapshotRenderTargetMapper.map(snapshot())

        assertEquals(
            listOf(
                Color4b(255, 132, 48),
                Color4b(255, 132, 48),
                Color4b(55, 210, 255),
                Color4b(165, 92, 255),
                Color4b(40, 130, 255),
                Color4b(246, 130, 31),
                Color4b(224, 166, 45),
                Color4b(190, 190, 190),
            ),
            targets.map(TrialChamberRenderTarget::color),
        )
        assertEquals(listOf(false, true, true, false, false, false, false, false), targets.map { it.completed })
        assertEquals(listOf(false, false, false, false, true, false, true, false), targets.map { it.visited })
        assertEquals(Vec3(1.5, 11.5, -0.5), targets.first().position)
        assertEquals(AABB(1.0, 11.0, -1.0, 2.0, 12.0, 0.0), targets.first().worldBox)
    }

    private fun snapshot() = TrialChamberSnapshot.create(
        worldEpoch = 3L,
        revision = 8L,
        playerInsideChamber = true,
        anchors = emptyList(),
        spawners = listOf(
            TrialSpawnerSnapshot(position(1), TrialSpawnerPhase.ACTIVE, ominous = false, expectedEntityType = null),
            TrialSpawnerSnapshot(position(2), TrialSpawnerPhase.COOLDOWN, ominous = true, expectedEntityType = null),
        ),
        mobs = emptyList(),
        vaults = listOf(
            TrialVaultSnapshot(position(3), ominous = false, status = TrialVaultStatus.CLAIMED),
            TrialVaultSnapshot(position(4), ominous = true, status = TrialVaultStatus.AVAILABLE),
        ),
        loot = TrialLootType.entries.mapIndexed { index, type ->
            TrialLootSnapshot(position(index + 5), type, visited = index % 2 == 0)
        },
    )

    private fun position(index: Int) = TrialChamberPosition(index, index + 10, -index)
}
