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
package net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogEntry
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogLevel
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRevision
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaritonePublicationCursorTest {

    @Test
    fun `route is published only for a newer revision`() {
        val cursor = BaritonePublicationCursor()

        assertTrue(cursor.acceptRoute(BaritoneRoute(BaritoneRevision(2))))
        assertFalse(cursor.acceptRoute(BaritoneRoute(BaritoneRevision(2))))
        assertFalse(cursor.acceptRoute(BaritoneRoute(BaritoneRevision(1))))
    }

    @Test
    fun `logs are sorted deduplicated and revision bounded`() {
        val cursor = BaritonePublicationCursor()
        val newest = log(4)
        val first = cursor.newLogs(listOf(newest, log(2), log(2)))

        assertEquals(listOf(2L, 4L), first.map { it.revision.value })
        assertEquals(emptyList(), cursor.newLogs(listOf(log(3), newest)))
    }

    private fun log(revision: Long) = BaritoneLogEntry(
        BaritoneRevision(revision),
        BaritoneLogLevel.INFO,
        "log-$revision",
        revision,
    )
}
