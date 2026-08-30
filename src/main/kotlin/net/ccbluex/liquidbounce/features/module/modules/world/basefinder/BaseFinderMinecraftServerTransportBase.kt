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

import net.minecraft.server.MinecraftServer
import net.minecraft.server.Services
import net.minecraft.server.WorldStem
import net.minecraft.server.level.progress.LoggingLevelLoadListener
import net.minecraft.server.notifications.NotificationManager
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.debugchart.LocalSampleLogger
import net.minecraft.util.debugchart.SampleLogger
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.storage.LevelStorageSource
import java.net.Proxy
import java.util.Optional

internal abstract class BaseFinderMinecraftServerTransportBase(
    serverThread: Thread,
    storageAccess: LevelStorageSource.LevelStorageAccess,
    packRepository: PackRepository,
    worldStem: WorldStem,
    services: Services,
) : MinecraftServer(
    serverThread,
    storageAccess,
    packRepository,
    worldStem,
    Optional.of(GameRules(FeatureFlags.DEFAULT_FLAGS)),
    Proxy.NO_PROXY,
    DataFixers.getDataFixer(),
    services,
    LoggingLevelLoadListener.forDedicatedServer(),
    false,
    NotificationManager(),
) {
    private val tickTimeLogger = LocalSampleLogger(4)

    override fun isTickTimeLoggingEnabled(): Boolean = false
    override fun getTickTimeLogger(): SampleLogger = tickTimeLogger
    override fun shouldRconBroadcast(): Boolean = false
    override fun operatorUserPermissions(): LevelBasedPermissionSet = LevelBasedPermissionSet.OWNER
    override fun getFunctionCompilationPermissions(): PermissionSet = LevelBasedPermissionSet.OWNER
    override fun isDedicatedServer(): Boolean = true
    override fun getRateLimitPacketsPerSecond(): Int = 0
    override fun getCommandSpamThresholdSeconds(): Int = Int.MAX_VALUE
    override fun getChatSpamThresholdSeconds(): Int = Int.MAX_VALUE
}
