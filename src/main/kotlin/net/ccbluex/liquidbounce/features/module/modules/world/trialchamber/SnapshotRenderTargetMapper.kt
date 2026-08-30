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
import net.ccbluex.liquidbounce.features.trialchamber.TrialLootType
import net.ccbluex.liquidbounce.features.trialchamber.TrialSpawnerPhase
import net.ccbluex.liquidbounce.features.trialchamber.TrialVaultStatus
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Maps an immutable runtime snapshot into renderer-owned targets while preserving source order. */
internal object SnapshotRenderTargetMapper {

    fun map(snapshot: TrialChamberSnapshot): List<TrialChamberRenderTarget> = buildList {
        snapshot.spawners.forEach { spawner ->
            add(TrialChamberRenderTarget(
                id = "spawner:${spawner.position.id}",
                kind = TrialChamberRenderTargetKind.SPAWNER,
                position = spawner.position.center,
                worldBox = spawner.position.blockBox,
                label = "Trial Spawner: ${spawner.phase.displayName}",
                color = SPAWNER_COLOR,
                completed = spawner.completed,
            ))
        }
        snapshot.vaults.forEach { vault ->
            add(TrialChamberRenderTarget(
                id = "vault:${vault.position.id}",
                kind = if (vault.ominous) {
                    TrialChamberRenderTargetKind.OMINOUS_VAULT
                } else {
                    TrialChamberRenderTargetKind.NORMAL_VAULT
                },
                position = vault.position.center,
                worldBox = vault.position.blockBox,
                label = "${if (vault.ominous) "Ominous " else ""}Vault: ${vault.status.displayName}",
                color = if (vault.ominous) OMINOUS_VAULT_COLOR else VAULT_COLOR,
                completed = vault.completed,
            ))
        }
        snapshot.loot.forEach { resource ->
            val presentation = resource.type.presentation
            add(TrialChamberRenderTarget(
                id = "${resource.type.name.lowercase()}:${resource.position.id}",
                kind = presentation.kind,
                position = resource.position.center,
                worldBox = resource.position.blockBox,
                label = presentation.label,
                color = presentation.color,
                visited = resource.visited,
            ))
        }
    }

    private val TrialLootType.presentation: LootPresentation
        get() = when (this) {
            TrialLootType.CHEST -> LootPresentation(TrialChamberRenderTargetKind.CHEST, "Chest", CHEST_COLOR)
            TrialLootType.BARREL -> LootPresentation(TrialChamberRenderTargetKind.BARREL, "Barrel", BARREL_COLOR)
            TrialLootType.POT -> LootPresentation(TrialChamberRenderTargetKind.POT, "Pot", POT_COLOR)
            TrialLootType.DISPENSER -> LootPresentation(
                TrialChamberRenderTargetKind.DISPENSER,
                "Dispenser",
                DISPENSER_COLOR,
            )
        }

    private data class LootPresentation(
        val kind: TrialChamberRenderTargetKind,
        val label: String,
        val color: Color4b,
    )

    private val TrialChamberPosition.id: String
        get() = "$x,$y,$z"

    private val TrialChamberPosition.center: Vec3
        get() = Vec3(x + 0.5, y + 0.5, z + 0.5)

    private val TrialChamberPosition.blockBox: AABB
        get() = AABB(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0)

    private val TrialSpawnerPhase.displayName: String
        get() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

    private val TrialVaultStatus.displayName: String
        get() = name.lowercase().replaceFirstChar(Char::uppercase)

    private val SPAWNER_COLOR = Color4b(255, 132, 48)
    private val VAULT_COLOR = Color4b(55, 210, 255)
    private val OMINOUS_VAULT_COLOR = Color4b(165, 92, 255)
    private val CHEST_COLOR = Color4b(40, 130, 255)
    private val BARREL_COLOR = Color4b(246, 130, 31)
    private val POT_COLOR = Color4b(224, 166, 45)
    private val DISPENSER_COLOR = Color4b(190, 190, 190)
}
