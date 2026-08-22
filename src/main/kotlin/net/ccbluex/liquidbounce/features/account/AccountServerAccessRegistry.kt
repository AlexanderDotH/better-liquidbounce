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
package net.ccbluex.liquidbounce.features.account

import java.util.WeakHashMap

internal object AccountServerAccessRegistry {

    private val workingServersByAccount = WeakHashMap<MinecraftAccount, LinkedHashSet<String>>()

    @Synchronized
    fun list(account: MinecraftAccount): List<String> = workingServersByAccount[account]?.toList().orEmpty()

    @Synchronized
    fun markWorking(account: MinecraftAccount, serverName: String): Boolean {
        val normalizedServerName = normalizeServerName(serverName)
        if (normalizedServerName.isEmpty()) {
            return false
        }

        return workingServersByAccount.getOrPut(account, ::linkedSetOf).add(normalizedServerName)
    }

    @Synchronized
    fun markUnavailable(account: MinecraftAccount, serverName: String): Boolean {
        val workingServers = workingServersByAccount[account] ?: return false
        val removed = workingServers.removeIf { serverNamesMatch(it, serverName) }
        if (workingServers.isEmpty()) {
            workingServersByAccount.remove(account)
        }
        return removed
    }

    @Synchronized
    fun restore(account: MinecraftAccount, serverNames: Iterable<String>) {
        val normalizedServerNames = serverNames.mapNotNullTo(linkedSetOf()) {
            normalizeServerName(it).takeIf(String::isNotEmpty)
        }
        if (normalizedServerNames.isEmpty()) {
            workingServersByAccount.remove(account)
            return
        }

        workingServersByAccount[account] = normalizedServerNames
    }

}

internal fun normalizeServerName(serverName: String): String =
    serverName.trim().lowercase().removeSuffix(".")

internal fun serverNamesMatch(first: String, second: String): Boolean {
    val normalizedFirst = normalizeServerName(first)
    val normalizedSecond = normalizeServerName(second)
    return normalizedFirst == normalizedSecond ||
        normalizedFirst.endsWith(".$normalizedSecond") ||
        normalizedSecond.endsWith(".$normalizedFirst")
}
