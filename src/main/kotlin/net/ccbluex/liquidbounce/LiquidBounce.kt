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
package net.ccbluex.liquidbounce

import net.ccbluex.liquidbounce.bootstrap.liquidbounce.ClientBootstrapState
import net.ccbluex.liquidbounce.bootstrap.liquidbounce.ClientLifecycle
import net.ccbluex.liquidbounce.bootstrap.liquidbounce.LiquidBounceClientConfig
import net.ccbluex.liquidbounce.common.ClientBuildMetadata
import net.ccbluex.liquidbounce.common.ClientLifecycleState
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.events.ClientStartEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.integration.task.TaskManager
import net.ccbluex.liquidbounce.utils.client.clientIdentifier
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.minecraft.resources.Identifier
import java.io.InputStream

/**
 * Stable public bootstrap facade for LiquidBounce.
 *
 * Lifecycle implementation lives in [ClientLifecycle] so this object only owns
 * public compatibility entry points and event wiring.
 */
object LiquidBounce : EventListener {

    const val CLIENT_NAME = ClientBuildMetadata.NAME
    const val CLIENT_AUTHOR = ClientBuildMetadata.AUTHOR
    const val IN_DEVELOPMENT = ClientBuildMetadata.IN_DEVELOPMENT

    val clientVersion get() = LiquidBounceClientConfig.clientVersion
    val clientCommit get() = LiquidBounceClientConfig.clientCommit
    val clientBranch get() = LiquidBounceClientConfig.clientBranch
    val logger get() = net.ccbluex.liquidbounce.utils.client.logger

    var taskManager: TaskManager?
        get() = ClientBootstrapState.taskManager
        set(value) {
            ClientBootstrapState.taskManager = value
        }

    val isInitialized get() = ClientLifecycleState.isInitialized

    @JvmStatic
    fun identifier(path: String): Identifier = clientIdentifier(path)

    @JvmStatic
    fun resource(path: String): InputStream =
        LiquidBounce::class.java.getResourceAsStream("/resources/liquidbounce/$path")
            ?: throw IllegalArgumentException("Resource $path not found")

    @JvmStatic
    fun resourceToString(path: String): String =
        resource(path).use { it.bufferedReader().readText() }

    @Suppress("unused")
    private val startHandler = handler<ClientStartEvent> {
        ClientLifecycle.start()
    }

    @Suppress("unused")
    private val screenHandler = handler<ScreenEvent>(priority = FIRST_PRIORITY) { event ->
        ClientLifecycle.enforceTaskScreen(event)
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        ClientLifecycle.shutdown()
    }
}
