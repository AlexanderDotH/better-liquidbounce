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
package net.ccbluex.liquidbounce.script

import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.text.copyable
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.underline
import net.ccbluex.liquidbounce.utils.text.variable
import net.minecraft.network.chat.HoverEvent
import org.graalvm.polyglot.Context
import java.io.File
import java.net.BindException
import java.net.ServerSocket

internal class ScriptDebugContextConfigurator(
    private val file: File,
    private val debugOptions: ScriptDebugOptions,
    private val option: (String, String) -> Unit,
    private val portAvailabilityCheck: (Int) -> Unit = ::checkDebugPortAvailability,
    private val inspectAnnouncement: (File, String) -> Unit = ::announceInspectDebugSupport,
    private val dapAnnouncement: (File, Int) -> Unit = ::announceDapDebugSupport,
) {

    fun configure() {
        if (!debugOptions.enabled) {
            return
        }

        val protocol = debugOptions.protocol.toString().lowercase()
        option("$protocol.Suspend", debugOptions.suspendOnStart.toString())
        option("$protocol.Internal", debugOptions.inspectInternals.toString())
        option(protocol, debugOptions.port.toString())

        when (debugOptions.protocol) {
            DebugProtocol.INSPECT -> configureInspect()
            DebugProtocol.DAP -> configureDap()
        }
    }

    private fun configureInspect() {
        option("inspect.Path", file.name)
        inspectAnnouncement(file, inspectDevtoolUrl(file, debugOptions.port))
    }

    private fun configureDap() {
        try {
            portAvailabilityCheck(debugOptions.port)
        } catch (exception: BindException) {
            throw IllegalStateException("Debug port ${debugOptions.port} already in use", exception)
        }

        dapAnnouncement(file, debugOptions.port)
    }
}

internal fun Context.Builder.applyScriptDebugOptions(
    file: File,
    debugOptions: ScriptDebugOptions,
): Context.Builder {
    val builder = this
    ScriptDebugContextConfigurator(
        file = file,
        debugOptions = debugOptions,
        option = { name, value -> builder.option(name, value) },
    ).configure()
    return builder
}

private fun inspectDevtoolUrl(file: File, port: Int) =
    "devtools://devtools/bundled/js_app.html?ws=127.0.0.1:$port/${file.name}"

private fun checkDebugPortAvailability(port: Int) {
    ServerSocket(port).close()
}

private fun announceInspectDebugSupport(file: File, devtoolUrl: String) {
    chat(
        regular(translation("liquidbounce.scripts.debug.support", variable(file.toString())))
            .append(
                variable(devtoolUrl)
                    .copyable(
                        copyContent = devtoolUrl,
                        hover = HoverEvent.ShowText(
                            regular(translation("liquidbounce.scripts.debug.inspect.url"))
                        ),
                    )
                    .underline(true)
            )
    )
}

private fun announceDapDebugSupport(file: File, port: Int) {
    chat(
        regular(
            translation("liquidbounce.scripts.debug.support", variable(file.toString())).append(
                translation("liquidbounce.scripts.debug.dap", variable(port.toString()))
            )
        )
    )
}
