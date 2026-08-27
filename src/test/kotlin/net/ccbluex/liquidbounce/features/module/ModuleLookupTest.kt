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
package net.ccbluex.liquidbounce.features.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ModuleLookupTest {

    @Test
    fun `exact module name wins before a compatibility alias`() {
        val exact = LookupEntry("SuperHit")
        val reach = LookupEntry("Reach", listOf("SuperHit"))

        assertEquals(
            exact,
            findByExactNameOrAlias(listOf(reach, exact), "superhit", LookupEntry::name, LookupEntry::aliases),
        )
    }

    @Test
    fun `compatibility alias resolves when no exact module remains`() {
        val reach = LookupEntry("Reach", listOf("SuperHit"))

        assertEquals(
            reach,
            findByExactNameOrAlias(listOf(reach), "SUPERHIT", LookupEntry::name, LookupEntry::aliases),
        )
        assertNull(findByExactNameOrAlias(listOf(reach), "Missing", LookupEntry::name, LookupEntry::aliases))
    }

    private data class LookupEntry(
        val name: String,
        val aliases: List<String> = emptyList(),
    )
}
