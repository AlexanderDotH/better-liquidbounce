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
package net.ccbluex.liquidbounce.bootstrap.command

import net.ccbluex.liquidbounce.api.thirdparty.translator.TranslationDefaultLanguage
import net.ccbluex.liquidbounce.features.command.commands.translate.CommandAutoTranslate

internal object AutoTranslateDefaultLanguageAdapter {
    fun install() = TranslationDefaultLanguage.install(CommandAutoTranslate::languageCode)
}
