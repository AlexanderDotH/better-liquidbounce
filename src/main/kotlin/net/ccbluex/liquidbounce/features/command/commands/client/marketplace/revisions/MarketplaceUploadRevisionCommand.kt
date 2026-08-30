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
package net.ccbluex.liquidbounce.features.command.commands.client.marketplace.revisions

import net.ccbluex.liquidbounce.api.services.marketplace.MarketplaceApi
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.CommandRuntime.suspendHandler
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.command.preset.accountOrException
import net.ccbluex.liquidbounce.features.cosmetic.ClientAccountManager
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import java.io.File

/**
 * Upload marketplace item revision
 */
object MarketplaceUploadRevisionCommand : Command.Factory {

    override fun createCommand() = CommandBuilder
        .begin("upload")
        .parameter(
            ParameterBuilder
                .begin<Int>("id")
                .verifiedBy(ParameterBuilder.INTEGER_VALIDATOR)
                .required()
                .build()
        )
        .parameter(
            ParameterBuilder
                .begin<String>("file")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .required()
                .build()
        )
        .parameter(
            ParameterBuilder
                .begin<String>("version")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .required()
                .build()
        )
        .parameter(
            ParameterBuilder
                .begin<String>("changelog")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .vararg()
                .optional()
                .build()
        )
        .parameter(
            ParameterBuilder
                .begin<String>("dependencies")
                .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
                .optional()
                .build()
        )
        .suspendHandler { uploadRevision() }
        .build()
}

private data class RevisionUpload(
    val id: Int,
    val file: File,
    val version: String,
    val changelog: String?,
    val dependencies: String?,
)

private fun Command.Handler.Context.revisionUpload(): RevisionUpload {
    val filePath = args[1] as String
    val file = File(filePath)
    if (!file.exists()) {
        throw CommandException(translation("liquidbounce.command.marketplace.error.fileNotFound", filePath))
    }
    return RevisionUpload(
        id = args[0] as Int,
        file = file,
        version = args[2] as String,
        changelog = (args.getOrNull(3) as? Array<*>)?.joinToString(" "),
        dependencies = args.getOrNull(4) as? String,
    )
}

private suspend fun Command.Handler.Context.uploadRevision() {
    val clientAccount = ClientAccountManager.accountOrException()
    val upload = revisionUpload()
    try {
        MarketplaceApi.createMarketplaceItemRevision(
            clientAccount.takeSession(),
            upload.id,
            upload.file,
            upload.version,
            upload.changelog,
            upload.dependencies,
        )
        chat(regular(command.result("success", variable(upload.version), variable(upload.id.toString()))))
    } catch (@Suppress("SwallowedException") exception: Exception) {
        logger.error("Failed to upload marketplace item revision", exception)
        throw CommandException(
            translation(
                "liquidbounce.command.marketplace.error.updateFailed",
                upload.id.toString(),
                exception.message ?: "Unknown error",
            ),
        )
    }
}
