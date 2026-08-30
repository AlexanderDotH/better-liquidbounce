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

package net.ccbluex.liquidbounce.features.command.commands.client

import com.google.gson.GsonBuilder
import net.ccbluex.liquidbounce.api.core.HttpClient
import net.ccbluex.liquidbounce.api.core.HttpMethod
import net.ccbluex.liquidbounce.api.core.asForm
import net.ccbluex.liquidbounce.api.core.parse
import net.ccbluex.liquidbounce.features.autoconfig.AutoConfig.serializeAutoConfig
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandRuntime.suspendHandler
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.commands.client.debug.createDebugReport
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.plus
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Style
import java.net.URI

/**
 * Debug Command to collect information about the client
 * in order to help developers to fix bugs or help users
 * with their issues.
 *
 * This command will create a JSON file with all the information
 * and send it to the CCBlueX Paste API.
 */
object CommandDebug : Command.Factory {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    override fun createCommand() = CommandBuilder.begin("debug")
        .suspendHandler {
            chat("§7Collecting debug information...")

            val buffer = okio.Buffer()

            buffer.outputStream().writer().use {
                serializeAutoConfig(it)
            }
            val autoConfig = buffer.readUtf8()
            val autoConfigPaste = uploadToPaste(autoConfig)
            buffer.clear()

            val debugJson = createDebugReport(autoConfigPaste)
            buffer.outputStream().writer().use {
                gson.toJson(debugJson, it)
            }
            val paste = uploadToPaste(buffer.readUtf8())
            buffer.clear()

            chat(
                "Debug information has been uploaded to: ".asPlainText(ChatFormatting.GREEN),
                paste.asPlainText(Style.EMPTY + ChatFormatting.YELLOW + ClickEvent.OpenUrl(URI(paste))),
            )
        }
        .build()

    /**
     * Uploads the given content to the CCBlueX Paste API
     * and returns the URL of the paste.
     */
    private suspend fun uploadToPaste(content: String): String {
        val form = "content=$content"
        return HttpClient.request("https://paste.ccbluex.net/api.php", HttpMethod.POST, body = form.asForm())
                .parse<String>()
    }

}
