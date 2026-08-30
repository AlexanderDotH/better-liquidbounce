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
package net.ccbluex.liquidbounce.features.command.commands.translate

import net.ccbluex.liquidbounce.api.thirdparty.translator.TranslateLanguage
import net.ccbluex.liquidbounce.api.thirdparty.translator.TranslationResult
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.CommandRuntime.suspendHandler
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.global.GlobalSettingsAutoTranslate
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.copyable
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable

object CommandTranslate : Command.Factory {

    override fun createCommand() = CommandBuilder.begin("translate")
        .alias("tr")
        .parameter(languageParameter("sourceLanguage") { listOf("auto") + languageCodes.keys })
        .parameter(languageParameter("targetLanguage") { languageCodes.keys })
        .parameter(textParameter())
        .suspendHandler {
            executeTranslation(command, args)
        }
        .build()

    private fun languageParameter(name: String, suggestions: () -> Iterable<String>) =
        ParameterBuilder.begin<String>(name)
            .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
            .autocompletedFrom(placeholdersProvider = suggestions)
            .required()
            .build()

    private fun textParameter() = ParameterBuilder.begin<String>("text")
        .verifiedBy(ParameterBuilder.STRING_VALIDATOR)
        .required()
        .vararg()
        .build()

    private suspend fun executeTranslation(command: Command, arguments: Array<out Any>) {
        val sourceLanguage = arguments[0] as String
        val targetLanguage = arguments[1] as String
        val texts = arguments[2] as Array<*>
        if (sourceLanguage.equals(targetLanguage, ignoreCase = true)) {
            throw CommandException(command.result("sameLanguage"))
        }
        val result = GlobalSettingsAutoTranslate.translate(
            TranslateLanguage.of(sourceLanguage),
            TranslateLanguage.of(targetLanguage),
            texts.joinToString(" "),
        )
        if (result !is TranslationResult.Success) {
            chat(result.toResultText())
            return
        }
        if (result.translation == result.origin) {
            throw CommandException(command.result("sameText"))
        }
        printTranslation(result)
    }

    private fun printTranslation(result: TranslationResult.Success) {
        chat(
            regular("("), variable(result.fromLanguage.literal), regular(") "),
            regular(result.origin).copyable(copyContent = result.origin),
        )
        chat(
            regular("("), variable(result.toLanguage.literal), regular(") "),
            regular(result.translation).copyable(copyContent = result.translation),
        )
    }

}
