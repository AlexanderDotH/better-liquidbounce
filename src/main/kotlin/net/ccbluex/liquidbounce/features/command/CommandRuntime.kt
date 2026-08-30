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

package net.ccbluex.liquidbounce.features.command

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.config.ClientCommandHistory
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ChatSendEvent
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.removeMessage
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.kotlin.MinecraftDispatcher
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.bold
import net.ccbluex.liquidbounce.utils.text.highlight
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.text.onClick
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/** Links Minecraft with the command engine. */
internal object CommandRuntime : EventListener {

    @Volatile
    private var isShuttingDown: Boolean = false

    fun <B : CommandHandlerBuilder<B>> B.suspendHandler(
        allowParallel: Boolean = false,
        handler: Command.Handler.Suspend,
    ): B = installSuspendHandler(this, allowParallel, handler)

    internal fun <B : CommandHandlerBuilder<B>> installSuspendHandler(
        builder: B,
        allowParallel: Boolean,
        handler: Command.Handler.Suspend,
    ): B = if (allowParallel) installParallelHandler(builder, handler) else installSerializedHandler(builder, handler)

    private fun <B : CommandHandlerBuilder<B>> installParallelHandler(
        builder: B,
        handler: Command.Handler.Suspend,
    ) = builder.handler {
        commandCoroutineScope.launch(CoroutineName(command.name)) {
            with(handler) { this@handler() }
        }
    }

    private fun <B : CommandHandlerBuilder<B>> installSerializedHandler(
        builder: B,
        handler: Command.Handler.Suspend,
    ): B {
        val running = AtomicBoolean(false)
        return builder.handler {
            if (!running.compareAndSet(false, true)) {
                reportAlreadyRunning(command)
                return@handler
            }
            val metadata = MessageMetadata(id = "C${command.name}#progress", remove = true)
            val progressJob = launchProgressMessage(command, metadata)
            commandCoroutineScope.launch(CoroutineName(command.name)) {
                with(handler) { this@handler() }
            }.invokeOnCompletion { completeSerializedExecution(running, progressJob, metadata) }
        }
    }

    private fun reportAlreadyRunning(command: Command) {
        chat(
            markAsError(translation("liquidbounce.commandManager.commandExecuting", command.name)),
            command,
        )
    }

    private fun launchProgressMessage(command: Command, metadata: MessageMetadata): Job =
        commandCoroutineScope.launch(CoroutineName("${command.name} Progress")) {
            val startAt = System.currentTimeMillis()
            var index = 0
            val characters = charArrayOf('|', '/', '-', '\\')
            while (isActive) {
                delay(0.25.seconds)
                val duration = (System.currentTimeMillis() - startAt) / 1000
                val character = characters[index % characters.size]
                chat(
                    regular("<$character> Executing command "),
                    variable(command.name),
                    regular(" ("),
                    variable(duration.toString()),
                    regular("s)"),
                    metadata = metadata,
                )
                index++
            }
        }

    private fun completeSerializedExecution(
        running: AtomicBoolean,
        progressJob: Job,
        metadata: MessageMetadata,
    ) {
        running.set(false)
        progressJob.cancel()
        mc.gui.hud.chat.removeMessage(metadata.id)
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (isShuttingDown && throwable is CancellationException) {
            // Client shutdown, ignored
        } else {
            handleExceptions(throwable)
        }
    }

    private val commandCoroutineScope by lazy {
        CoroutineScope(MinecraftDispatcher + SupervisorJob() + coroutineExceptionHandler)
    }

    internal fun handleExceptions(e: Throwable) {
        when (e) {
            is CommandException -> {
                mc.gui.hud.chat.removeMessage("CommandManager#error")
                val data = MessageMetadata(id = "CommandManager#error", remove = false)
                chat(e.text.withStyle(ChatFormatting.RED), metadata = data)

                if (e.usageInfo.isNotEmpty()) {
                    chat(highlight("Usage: ").bold(true), metadata = data)

                    for (usage in e.usageInfo) {
                        val prefix = CommandManager.GlobalSettings.prefix
                        val text = regular("")
                            .append("\u2B25 ".asPlainText(ChatFormatting.BLUE))
                            .append(regular(prefix))
                            .append(usage)
                            .onClick(ClickEvent.SuggestCommand(prefix + usage.string))

                        chat(text, metadata = data)
                    }
                }
            }
            else -> {
                chat(
                    markAsError(
                        translation(
                            "liquidbounce.commandManager.exceptionOccurred",
                            e.javaClass.simpleName ?: "Class name missing", e.message ?: "No message"
                        )
                    ),
                    metadata = MessageMetadata(id = "CommandManager#error")
                )
                logger.error("An exception occurred while executing a command", e)
            }
        }
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        isShuttingDown = true
        commandCoroutineScope.cancel()
    }

    @Suppress("unused")
    private val chatEventHandler = handler<ChatSendEvent>(priority = EventPriorityConvention.FIRST_PRIORITY) {
        if (!it.message.startsWith(CommandManager.GlobalSettings.prefix)) {
            return@handler
        }

        val commandBody = it.message.substring(CommandManager.GlobalSettings.prefix.length)
        try {
            CommandManager.execute(commandBody)
        } catch (e: Throwable) {
            handleExceptions(e)
        } finally {
            it.cancelEvent()
        }

        ClientCommandHistory.append(commandBody)
    }
}
