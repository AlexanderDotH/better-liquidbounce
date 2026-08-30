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

import net.minecraft.SystemReport
import net.minecraft.server.Services
import net.minecraft.server.WorldStem
import net.minecraft.server.notifications.EmptyNotificationService
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.players.NameAndId
import net.minecraft.server.players.PlayerList
import net.minecraft.world.Difficulty
import net.minecraft.world.level.storage.LevelStorageSource
import java.nio.file.Path

internal abstract class BaseFinderMinecraftServerBase(
    serverThread: Thread,
    storageAccess: LevelStorageSource.LevelStorageAccess,
    packRepository: PackRepository,
    worldStem: WorldStem,
    services: Services,
    private val serverDirectory: Path,
    private val viewChunks: Int,
) : BaseFinderMinecraftServerTransportBase(
    serverThread,
    storageAccess,
    packRepository,
    worldStem,
    services,
), BaseFinderSilentMinecraftServer {
    override fun useNativeTransport(): Boolean = false
    override fun isPublished(): Boolean = false
    override fun shouldInformAdmins(): Boolean = false
    override fun isSingleplayerOwner(nameAndId: NameAndId): Boolean = false
    override fun fillServerSystemReport(report: SystemReport): SystemReport = report
    override fun getMaxPlayers(): Int = 0
    override fun getServerDirectory(): Path = serverDirectory

    override fun initServer(): Boolean {
        setUsesAuthentication(false)
        services().nameToIdCache().resolveOfflineUsers(true)
        setPlayerList(
            object : PlayerList(
                this,
                registries(),
                playerDataStorage,
                EmptyNotificationService(),
            ) {},
        )
        loadLevel()
        playerList.setViewDistance(viewChunks)
        playerList.setSimulationDistance(BaseFinderBackgroundServer.MIN_SIMULATION_DISTANCE)
        applyEfficiencySettings()
        return true
    }

    private fun applyEfficiencySettings() {
        setDifficulty(Difficulty.PEACEFUL, true)
        setDifficultyLocked(true)
        tickRateManager().setFrozen(true)
        setAutoSave(false)
        for (level in allLevels) level.noSave = true
        updateMobSpawningFlags()
    }

    override fun stopServer() {
        for (level in allLevels) {
            try {
                level.chunkSource.deactivateTicketsOnClosing()
            } catch (_: Throwable) {
            }
            try {
                level.close()
            } catch (_: Throwable) {
            }
        }
    }
}
