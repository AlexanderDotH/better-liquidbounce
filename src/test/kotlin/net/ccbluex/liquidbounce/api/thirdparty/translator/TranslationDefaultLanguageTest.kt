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
package net.ccbluex.liquidbounce.api.thirdparty.translator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationDefaultLanguageTest {
    @Test
    fun `uninstalled provider retains english fallback`() {
        TranslationDefaultLanguage.withProviderForTest(null) {
            assertEquals("en", TranslationDefaultLanguage.code())
        }
    }

    @Test
    fun `installed provider is read dynamically`() {
        var languageCode = "de"
        TranslationDefaultLanguage.withProviderForTest({ languageCode }) {
            assertEquals("de", TranslationDefaultLanguage.code())
            languageCode = "fr"
            assertEquals("fr", TranslationDefaultLanguage.code())
        }
    }
}
