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
package net.ccbluex.liquidbounce.features.module.modules.misc.bettertab

import java.util.Optional
import java.util.UUID

internal object EssentialPresenceBridge {

    private const val ESSENTIAL_CLASS = "gg.essential.Essential"

    fun onlineUsers(uuids: Set<UUID>): Set<UUID> = runCatching {
        val classLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        val essentialClass = Class.forName(ESSENTIAL_CLASS, false, classLoader)
        essentialOnlineUsers(uuids, essentialClass)
    }.getOrDefault(emptySet())
}

internal fun essentialOnlineUsers(uuids: Set<UUID>, essentialClass: Class<*>): Set<UUID> = runCatching {
    val essential = requireNotNull(essentialClass.getMethod("getInstance").invoke(null))
    val connectionManager = requireNotNull(essential.javaClass.getMethod("getConnectionManager").invoke(essential))
    val profileManager = requireNotNull(
        connectionManager.javaClass.getMethod("getProfileManager").invoke(connectionManager)
    )
    val getStatus = profileManager.javaClass.getMethod("getStatusIfLoaded", UUID::class.java)

    uuids.filterTo(linkedSetOf()) { uuid ->
        val status = (getStatus.invoke(profileManager, uuid) as? Optional<*>)?.orElse(null)
        (status as? Enum<*>)?.name == "ONLINE"
    }
}.getOrDefault(emptySet())
