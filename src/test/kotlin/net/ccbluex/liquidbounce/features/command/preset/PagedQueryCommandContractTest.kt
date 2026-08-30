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
package net.ccbluex.liquidbounce.features.command.preset

import net.ccbluex.liquidbounce.features.command.Parameter
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.utils.text.asPlainText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PagedQueryCommandContractTest {

    @Test
    fun `paged query retains optional page parameter and range verification`() {
        val command = CommandBuilder.begin("items").pagedQuery(
            pageSize = 5,
            header = { "header".asPlainText() },
            items = { (1..13).toList() },
            eachRow = { _, item -> item.toString().asPlainText() },
        )

        val page = command.parameters.single()
        assertEquals("page", page.name)
        assertFalse(page.required)
        assertFalse(page.vararg)
        assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, page.verifier!!.verifyAndParse("3"))
        assertInstanceOf(Parameter.Verificator.Result.Error::class.java, page.verifier!!.verifyAndParse("4"))
    }

    @Test
    fun `pagination layout retains boundary and ellipsis pages`() {
        assertEquals(listOf(1, 2, 3, 6), paginationPages(currentPage = 1, maxPage = 6))
        assertEquals(listOf(1, 3, 4, 5, 7), paginationPages(currentPage = 4, maxPage = 7))
        assertEquals(listOf(1, 4, 5, 6), paginationPages(currentPage = 6, maxPage = 6))
        assertTrue(paginationPages(currentPage = 1, maxPage = 3).containsAll((1..3).toList()))
    }
}
