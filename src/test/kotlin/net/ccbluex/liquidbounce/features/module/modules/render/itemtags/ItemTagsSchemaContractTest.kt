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
package net.ccbluex.liquidbounce.features.module.modules.render.itemtags

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ItemTagsSchemaContractTest {

    @Test
    fun `merge mode default tags and declaration order remain stable`() {
        val source = Files.readString(
            Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/ModuleItemTags.kt")
        )

        assertTrue(source.contains("enumChoice(\"MergeMode\", MergeMode.BY_COMPONENTS)"))
        assertInOrder(source, "NONE(\"None\"", "BY_ITEM(\"ByItem\"", "BY_COMPONENTS(\"ByComponents\"")
    }

    private fun assertInOrder(source: String, vararg tokens: String) {
        var previous = -1
        tokens.forEach { token ->
            val current = source.indexOf(token)
            assertTrue(current > previous, "$token must retain its ItemTags declaration order")
            previous = current
        }
    }
}
